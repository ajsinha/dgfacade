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

import com.dgfacade.common.handler.DGHandler;
import com.dgfacade.common.handler.HandlerRegistry;
import com.dgfacade.common.model.DGResponse;
import com.dgfacade.common.model.ResponseStatus;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Pekko Typed Actor that manages handler lifecycle execution.
 * Each request spawns a child actor for concurrent processing.
 */
public class HandlerActor extends AbstractBehavior<HandlerProtocol.HandlerCommand> {

    private static final Logger log = LoggerFactory.getLogger(HandlerActor.class);
    private final HandlerRegistry handlerRegistry;
    private long processedCount = 0;

    private HandlerActor(ActorContext<HandlerProtocol.HandlerCommand> context,
                         HandlerRegistry handlerRegistry) {
        super(context);
        this.handlerRegistry = handlerRegistry;
        log.info("HandlerActor started: {}", context.getSelf().path());
    }

    public static Behavior<HandlerProtocol.HandlerCommand> create(HandlerRegistry handlerRegistry) {
        return Behaviors.setup(ctx -> new HandlerActor(ctx, handlerRegistry));
    }

    @Override
    public Receive<HandlerProtocol.HandlerCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(HandlerProtocol.ExecuteRequest.class, this::onExecuteRequest)
                .onMessage(HandlerProtocol.GetStatus.class, this::onGetStatus)
                .onMessage(HandlerProtocol.Shutdown.class, msg -> Behaviors.stopped())
                .build();
    }

    private Behavior<HandlerProtocol.HandlerCommand> onExecuteRequest(
            HandlerProtocol.ExecuteRequest cmd) {

        String requestType = cmd.request().getRequestType();
        String requestId = cmd.request().getRequestId();

        log.info("Processing request {} of type {}", requestId, requestType);

        Optional<DGHandler> handlerOpt = handlerRegistry.createHandler(requestType);

        if (handlerOpt.isEmpty()) {
            cmd.replyTo().tell(new HandlerProtocol.HandlerFailure(
                    requestId, "No handler registered for type: " + requestType));
            return this;
        }

        // Execute handler lifecycle in a separate thread via child actor
        getContext().spawnAnonymous(Behaviors.setup(childCtx -> {
            DGHandler handler = handlerOpt.get();
            DGResponse response = null;
            long startTime = System.currentTimeMillis();
            try {
                handler.start(cmd.request());
                response = handler.execute();
                response.setExecutionTimeMs(System.currentTimeMillis() - startTime);
                cmd.replyTo().tell(new HandlerProtocol.HandlerSuccess(response));
            } catch (Exception e) {
                log.error("Handler execution failed for request {}", requestId, e);
                cmd.replyTo().tell(new HandlerProtocol.HandlerFailure(requestId, e.getMessage()));
            } finally {
                try {
                    handler.stop(response);
                } catch (Exception e) {
                    log.error("Handler stop failed for request {}", requestId, e);
                }
            }
            return Behaviors.stopped();
        }));

        processedCount++;
        return this;
    }

    private Behavior<HandlerProtocol.HandlerCommand> onGetStatus(HandlerProtocol.GetStatus cmd) {
        cmd.replyTo().tell(new HandlerProtocol.StatusResponse(
                getContext().getSelf().path().name(), "RUNNING", processedCount));
        return this;
    }
}
