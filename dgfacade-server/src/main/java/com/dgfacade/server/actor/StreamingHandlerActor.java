/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, modification, or use is strictly prohibited without
 * explicit written permission from the copyright holder.
 * Patent Pending.
 */
package com.dgfacade.server.actor;

import com.dgfacade.common.handler.StreamingHandler;
import com.dgfacade.common.model.*;
import com.dgfacade.messaging.StreamingPublisher;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;

/**
 * Long-lived Pekko actor that manages a streaming handler's lifecycle.
 *
 * Unlike the one-shot child actors in HandlerActor, this actor stays alive
 * for the duration of the streaming session (up to TTL minutes).
 *
 * It runs the handler's execute() method in a separate thread and routes
 * published data to ALL configured channels via StreamingPublisher (multi-channel fan-out).
 *
 * Protocol:
 *   - StreamingProtocol.StartStreaming   → start the handler
 *   - StreamingProtocol.StopStreaming    → gracefully shut down
 *   - StreamingProtocol.TtlExpired      → TTL timer fired, shut down
 *   - StreamingProtocol.DataPublished   → handler published data (internal)
 */
public class StreamingHandlerActor extends AbstractBehavior<StreamingHandlerActor.StreamingCommand> {

    private static final Logger log = LoggerFactory.getLogger(StreamingHandlerActor.class);

    // --- Protocol messages ---
    public sealed interface StreamingCommand {}
    public record StartStreaming(DGRequest request, StreamingSession session,
                                 StreamingHandler handler, StreamingPublisher publisher,
                                 org.apache.pekko.actor.typed.ActorRef<HandlerProtocol.HandlerResult> replyTo)
            implements StreamingCommand {}
    public record StopStreaming(String sessionId) implements StreamingCommand {}
    public record TtlExpired() implements StreamingCommand {}
    public record DataPublished(DGResponse response) implements StreamingCommand {}

    private final com.dgfacade.server.service.StreamingSessionManager sessionManager;
    private StreamingSession session;
    private StreamingHandler handler;
    private StreamingPublisher publisher;
    private Thread executionThread;

    private StreamingHandlerActor(ActorContext<StreamingCommand> context,
                                   com.dgfacade.server.service.StreamingSessionManager sessionManager) {
        super(context);
        this.sessionManager = sessionManager;
    }

    public static Behavior<StreamingCommand> create(
            com.dgfacade.server.service.StreamingSessionManager sessionManager) {
        return Behaviors.setup(ctx -> new StreamingHandlerActor(ctx, sessionManager));
    }

    @Override
    public Receive<StreamingCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(StartStreaming.class, this::onStartStreaming)
                .onMessage(StopStreaming.class, this::onStopStreaming)
                .onMessage(TtlExpired.class, this::onTtlExpired)
                .onMessage(DataPublished.class, this::onDataPublished)
                .build();
    }

    private Behavior<StreamingCommand> onStartStreaming(StartStreaming cmd) {
        this.session = cmd.session();
        this.handler = cmd.handler();
        this.publisher = cmd.publisher();

        log.info("Starting streaming session {} for handler {}", session.getSessionId(), session.getHandlerType());

        try {
            // Initialize the handler
            handler.start(cmd.request());
            session.setStatus(HandlerStatus.EXECUTING);
            sessionManager.register(session);

            // Set up the data publisher callback — routes data through the actor
            var selfRef = getContext().getSelf();
            handler.setDataPublisher(response -> {
                selfRef.tell(new DataPublished(response));
            });

            // Schedule TTL expiry
            getContext().scheduleOnce(
                    Duration.ofMinutes(session.getTtlMinutes()),
                    getContext().getSelf(),
                    new TtlExpired());

            // Run execute() in a separate thread (it's a blocking streaming loop)
            executionThread = new Thread(() -> {
                try {
                    DGResponse finalResponse = handler.execute();
                    // execute() returned — handler finished on its own
                    log.info("Streaming handler {} completed naturally", session.getSessionId());
                } catch (Exception e) {
                    log.error("Streaming handler {} execute() failed", session.getSessionId(), e);
                } finally {
                    // Trigger cleanup from the actor thread
                    selfRef.tell(new StopStreaming(session.getSessionId()));
                }
            }, "streaming-" + session.getSessionId());
            executionThread.setDaemon(true);
            executionThread.start();

            // Reply immediately with STREAMING_STARTED
            DGResponse startedResponse = new DGResponse();
            startedResponse.setRequestId(cmd.request().getRequestId());
            startedResponse.setStatus(ResponseStatus.STREAMING_STARTED);
            startedResponse.setHandlerType(session.getHandlerType());
            startedResponse.setMessage("Streaming session started: " + session.getSessionId());
            startedResponse.setResult(java.util.Map.of(
                    "sessionId", session.getSessionId(),
                    "ttlMinutes", session.getTtlMinutes(),
                    "responseChannels", session.getResponseChannels().stream()
                            .map(Enum::name).toList(),
                    "responseTopic", session.getResponseTopic(),
                    "expiresAt", session.getExpiresAt().toString()
            ));
            cmd.replyTo().tell(new HandlerProtocol.HandlerSuccess(startedResponse));

        } catch (Exception e) {
            log.error("Failed to start streaming handler {}", session.getHandlerType(), e);
            session.setStatus(HandlerStatus.FAILED);
            cmd.replyTo().tell(new HandlerProtocol.HandlerFailure(
                    cmd.request().getRequestId(), "Streaming start failed: " + e.getMessage()));
            return Behaviors.stopped();
        }

        return this;
    }

    private Behavior<StreamingCommand> onDataPublished(DataPublished cmd) {
        if (session == null || publisher == null) return this;

        try {
            // Stamp the response with streaming metadata
            DGResponse response = cmd.response();
            response.setStatus(ResponseStatus.STREAMING_DATA);
            response.setHandlerType(session.getHandlerType());
            if (response.getResult() != null) {
                response.getResult().put("_sessionId", session.getSessionId());
                response.getResult().put("_sequenceNumber", session.getMessagesPublished() + 1);
            }

            publisher.publish(session, response);
            session.incrementMessages();

            if (session.getMessagesPublished() % 100 == 0) {
                log.info("Streaming session {} published {} messages",
                         session.getSessionId(), session.getMessagesPublished());
            }
        } catch (Exception e) {
            log.error("Failed to publish streaming data for session {}", session.getSessionId(), e);
        }

        return this;
    }

    private Behavior<StreamingCommand> onStopStreaming(StopStreaming cmd) {
        return shutdownGracefully("Manual stop requested");
    }

    private Behavior<StreamingCommand> onTtlExpired(TtlExpired cmd) {
        log.info("TTL expired for streaming session {}", session != null ? session.getSessionId() : "unknown");
        return shutdownGracefully("TTL expired");
    }

    private Behavior<StreamingCommand> shutdownGracefully(String reason) {
        if (handler != null && handler.isRunning()) {
            log.info("Shutting down streaming handler: {} — reason: {}", session.getSessionId(), reason);
            handler.requestShutdown();

            // Give the execution thread a moment to finish
            if (executionThread != null && executionThread.isAlive()) {
                try {
                    executionThread.join(5000);
                } catch (InterruptedException ignored) {}
                if (executionThread.isAlive()) {
                    executionThread.interrupt();
                }
            }
        }

        if (handler != null) {
            try {
                handler.stop(null);
            } catch (Exception e) {
                log.error("Error stopping streaming handler", e);
            }
        }

        // Publish end-of-stream message
        if (session != null && publisher != null) {
            try {
                DGResponse endMsg = new DGResponse();
                endMsg.setRequestId(session.getRequestId());
                endMsg.setStatus(ResponseStatus.STREAMING_ENDED);
                endMsg.setHandlerType(session.getHandlerType());
                endMsg.setMessage("Streaming session ended: " + reason);
                endMsg.setResult(java.util.Map.of(
                        "sessionId", session.getSessionId(),
                        "totalMessages", session.getMessagesPublished(),
                        "reason", reason,
                        "duration", java.time.Duration.between(session.getStartedAt(), Instant.now()).toString()
                ));
                publisher.publish(session, endMsg);
            } catch (Exception e) {
                log.warn("Failed to publish end-of-stream for {}", session.getSessionId());
            }

            session.setStatus(HandlerStatus.STOPPED);
            sessionManager.unregister(session.getSessionId());
        }

        return Behaviors.stopped();
    }
}
