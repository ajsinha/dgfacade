/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential.
 */
package com.dgfacade.server.actor;

import com.dgfacade.common.model.HandlerState;
import com.dgfacade.common.util.CircularBuffer;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Top-level Pekko supervisor actor that manages all handler actors.
 * Spawns child HandlerActors for each incoming request.
 * Maintains a registry of active handlers and a circular buffer of
 * recent handler states (max 1000, max 1 hour) for the monitoring UI.
 */
public class HandlerSupervisor extends AbstractBehavior<HandlerMessages.SupervisorCommand> {

    private static final Logger log = LoggerFactory.getLogger(HandlerSupervisor.class);

    private final Map<String, ActorRef<HandlerActor.Command>> activeHandlers = new ConcurrentHashMap<>();
    private final CircularBuffer<HandlerState> stateHistory = new CircularBuffer<>(1000, Duration.ofHours(1));

    private HandlerSupervisor(ActorContext<HandlerMessages.SupervisorCommand> context) {
        super(context);
        log.info("HandlerSupervisor started");
    }

    public static Behavior<HandlerMessages.SupervisorCommand> create() {
        return Behaviors.setup(HandlerSupervisor::new);
    }

    @Override
    public Receive<HandlerMessages.SupervisorCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(HandlerMessages.WrappedExecute.class, this::onExecute)
                .onMessage(HandlerMessages.WrappedStop.class, this::onStop)
                .onMessage(HandlerMessages.WrappedTimeout.class, this::onTimeout)
                .build();
    }

    private Behavior<HandlerMessages.SupervisorCommand> onExecute(HandlerMessages.WrappedExecute cmd) {
        HandlerMessages.ExecuteRequest req = cmd.req();
        String handlerId = req.handlerId() != null ? req.handlerId() :
                "hdl-" + UUID.randomUUID().toString().substring(0, 12);

        // Create handler state record
        HandlerState hs = new HandlerState(handlerId, req.request().getRequestId(), req.request().getRequestType());
        hs.setUserId(req.request().getResolvedUserId());
        hs.setHandlerClass(req.handlerConfig().getHandlerClass());
        stateHistory.add(hs);

        // Spawn handler actor
        ActorRef<HandlerActor.Command> actorRef = getContext().spawn(
                HandlerActor.create(handlerId), handlerId);
        activeHandlers.put(handlerId, actorRef);

        // Send execute command
        actorRef.tell(new HandlerActor.Execute(req));

        // Watch for termination to clean up
        getContext().watchWith(actorRef, new HandlerMessages.WrappedStop(
                new HandlerMessages.StopHandler(handlerId)));

        log.debug("Spawned handler actor: {}", handlerId);
        return this;
    }

    private Behavior<HandlerMessages.SupervisorCommand> onStop(HandlerMessages.WrappedStop cmd) {
        String handlerId = cmd.stop().handlerId();
        ActorRef<HandlerActor.Command> ref = activeHandlers.remove(handlerId);
        if (ref != null) {
            ref.tell(new HandlerActor.Stop());
        }
        return this;
    }

    private Behavior<HandlerMessages.SupervisorCommand> onTimeout(HandlerMessages.WrappedTimeout cmd) {
        String handlerId = cmd.timeout().handlerId();
        ActorRef<HandlerActor.Command> ref = activeHandlers.get(handlerId);
        if (ref != null) {
            ref.tell(new HandlerActor.Timeout());
        }
        return this;
    }

    public int getActiveCount() { return activeHandlers.size(); }
    public CircularBuffer<HandlerState> getStateHistory() { return stateHistory; }
}
