/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential. Patent Pending.
 */
package com.dgfacade.server.actor;

import com.dgfacade.common.model.DGRequest;
import com.dgfacade.common.model.DGResponse;
import com.dgfacade.common.model.HandlerConfig;
import java.io.Serializable;

/**
 * Message protocol for the Pekko actor system.
 * All messages exchanged between actors are defined here.
 */
public final class HandlerMessages {

    private HandlerMessages() {}

    /** Request to execute a handler. */
    public record ExecuteRequest(
        DGRequest request,
        HandlerConfig handlerConfig,
        String handlerId,
        java.util.concurrent.CompletableFuture<DGResponse> responseFuture
    ) implements Serializable {}

    /** Signal that a handler has completed. */
    public record HandlerCompleted(
        String handlerId,
        DGResponse response,
        long durationMs
    ) implements Serializable {}

    /** Signal that a handler has failed. */
    public record HandlerFailed(
        String handlerId,
        String errorMessage,
        Throwable exception
    ) implements Serializable {}

    /** Signal that a handler has exceeded its TTL. */
    public record HandlerTimeout(String handlerId) implements Serializable {}

    /** Request to stop a running handler. */
    public record StopHandler(String handlerId) implements Serializable {}

    /** Streaming update from a handler. */
    public record StreamingUpdate(
        String handlerId,
        String requestId,
        DGResponse update
    ) implements Serializable {}

    /** Query the state of a handler. */
    public record QueryHandlerState(
        String handlerId,
        org.apache.pekko.actor.typed.ActorRef<HandlerStateResponse> replyTo
    ) implements Serializable {}

    /** Response to a handler state query. */
    public record HandlerStateResponse(
        com.dgfacade.common.model.HandlerState state
    ) implements Serializable {}

    /** Wrapper for all messages the supervisor understands. */
    public sealed interface SupervisorCommand permits
            WrappedExecute, WrappedStop, WrappedTimeout, WrappedQuery {}

    public record WrappedExecute(ExecuteRequest req) implements SupervisorCommand {}
    public record WrappedStop(StopHandler stop) implements SupervisorCommand {}
    public record WrappedTimeout(HandlerTimeout timeout) implements SupervisorCommand {}
    public record WrappedQuery(QueryHandlerState query) implements SupervisorCommand {}
}
