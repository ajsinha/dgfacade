/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential. Patent Pending.
 */
package com.dgfacade.server.actor;

import com.dgfacade.common.model.*;
import com.dgfacade.server.channel.ChannelAccessor;
import com.dgfacade.server.handler.DGHandler;
import com.dgfacade.server.handler.DGHandlerProxy;
import com.dgfacade.server.handler.StreamingDGHandler;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * Pekko typed actor that manages the lifecycle of a single DGHandler instance.
 * Each request gets its own actor which:
 *   1. Instantiates and constructs the handler
 *   2. Executes it with TTL enforcement via a scheduled timeout
 *   3. Stops and cleans up after completion or timeout
 *
 * This design allows millions of concurrent handlers with minimal overhead.
 */
public class HandlerActor extends AbstractBehavior<HandlerActor.Command> {

    private static final Logger log = LoggerFactory.getLogger(HandlerActor.class);

    public sealed interface Command {}
    public record Execute(HandlerMessages.ExecuteRequest req) implements Command {}
    public record Timeout() implements Command {}
    public record Stop() implements Command {}

    private final String handlerId;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private DGHandler handler;
    private HandlerState state;
    private CompletableFuture<DGResponse> responseFuture;

    private HandlerActor(ActorContext<Command> context, String handlerId) {
        super(context);
        this.handlerId = handlerId;
    }

    public static Behavior<Command> create(String handlerId) {
        return Behaviors.setup(ctx -> new HandlerActor(ctx, handlerId));
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(Execute.class, this::onExecute)
                .onMessage(Timeout.class, this::onTimeout)
                .onMessage(Stop.class, this::onStop)
                .build();
    }

    private Behavior<Command> onExecute(Execute cmd) {
        HandlerMessages.ExecuteRequest req = cmd.req();
        this.responseFuture = req.responseFuture();
        // Use the state object from ExecutionEngine (already in the recentStates buffer)
        this.state = req.state();

        // Schedule TTL timeout
        int ttl = req.handlerConfig().getTtlMinutes();
        if (req.request().getTtlMinutes() > 0) ttl = req.request().getTtlMinutes();
        getContext().scheduleOnce(Duration.ofMinutes(ttl), getContext().getSelf(), new Timeout());

        try {
            // Instantiate handler class
            Class<?> clazz = Class.forName(req.handlerConfig().getHandlerClass());

            // If the class implements DGHandler, use it directly.
            // Otherwise, wrap it in a dynamic proxy (DGHandlerProxy).
            if (DGHandler.class.isAssignableFrom(clazz)) {
                handler = (DGHandler) clazz.getDeclaredConstructor().newInstance();
                log.debug("Handler {} implements DGHandler — using directly", clazz.getSimpleName());
            } else {
                handler = DGHandlerProxy.createFrom(clazz);
                log.info("Handler {} does NOT implement DGHandler — wrapped in DGHandlerProxy",
                        clazz.getSimpleName());
            }

            // Construct
            state.setPhase(HandlerState.Phase.CONSTRUCTING);
            // Inject ChannelAccessor for handlers that need pub/sub access
            if (req.channelAccessor() != null) {
                handler.setChannelAccessor(req.channelAccessor());
            }
            handler.construct(req.handlerConfig().getConfig());

            // Execute
            state.markStarted();
            req.request().setExecutionStartedAt(Instant.now());

            DGResponse response;
            if (handler instanceof StreamingDGHandler streamingHandler) {
                response = streamingHandler.executeStreaming(req.request(), update -> {
                    // Streaming updates could be sent via WebSocket or messaging
                    log.debug("Streaming update from {}: seq={}", handlerId, update.getSequenceNumber());
                });
            } else {
                response = handler.execute(req.request());
            }

            // Success
            response.setHandlerId(handlerId);
            long duration = state.getStartedAt() != null ?
                    Duration.between(state.getStartedAt(), Instant.now()).toMillis() : 0;
            response.setExecutionTimeMs(duration);
            state.markCompleted(true, null);
            // Set response JSON immediately so handler-detail page has it
            try { state.setResponseJson(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(response)); } catch (Exception ignored) {}
            responseFuture.complete(response);

            log.info("Handler {} completed in {}ms", handlerId, duration);

        } catch (Exception e) {
            log.error("Handler {} failed", handlerId, e);
            state.markCompleted(false, e.getMessage());
            state.setExceptionStackTrace(stackTraceToString(e));
            DGResponse errorResp = DGResponse.error(
                    req.request().getRequestId(), "Handler execution failed: " + e.getMessage());
            errorResp.setHandlerId(handlerId);
            try { state.setResponseJson(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(errorResp)); } catch (Exception ignored) {}
            responseFuture.complete(errorResp);
        } finally {
            if (handler != null) {
                try { handler.cleanup(); } catch (Exception e) { log.warn("Cleanup error", e); }
            }
        }

        return Behaviors.stopped();
    }

    private Behavior<Command> onTimeout(Timeout cmd) {
        log.warn("Handler {} TTL expired, forcing stop", handlerId);
        if (handler != null) {
            try { handler.stop(); } catch (Exception e) { log.warn("Stop error on timeout", e); }
            try { handler.cleanup(); } catch (Exception e) { log.warn("Cleanup error on timeout", e); }
        }
        if (state != null) state.markTimedOut();
        if (responseFuture != null && !responseFuture.isDone()) {
            DGResponse timeoutResp = DGResponse.timeout(state != null ? state.getRequestId() : "unknown");
            timeoutResp.setHandlerId(handlerId);
            if (state != null) {
                try { state.setResponseJson(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(timeoutResp)); } catch (Exception ignored) {}
            }
            responseFuture.complete(timeoutResp);
        }
        return Behaviors.stopped();
    }

    private Behavior<Command> onStop(Stop cmd) {
        if (handler != null) {
            try { handler.stop(); } catch (Exception e) { log.warn("Stop error", e); }
            try { handler.cleanup(); } catch (Exception e) { log.warn("Cleanup error", e); }
        }
        return Behaviors.stopped();
    }

    public HandlerState getState() { return state; }

    private static String stackTraceToString(Throwable t) {
        java.io.StringWriter sw = new java.io.StringWriter();
        t.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }
}
