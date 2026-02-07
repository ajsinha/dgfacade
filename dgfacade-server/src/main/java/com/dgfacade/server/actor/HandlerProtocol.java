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

import com.dgfacade.common.model.DGRequest;
import com.dgfacade.common.model.DGResponse;

/**
 * Protocol messages for the Handler Actor system.
 */
public final class HandlerProtocol {

    private HandlerProtocol() {}

    /** Sealed interface for all handler messages */
    public sealed interface HandlerCommand permits ExecuteRequest, GetStatus, Shutdown {}

    /** Command to execute a handler request */
    public record ExecuteRequest(
            DGRequest request,
            org.apache.pekko.actor.typed.ActorRef<HandlerResult> replyTo
    ) implements HandlerCommand {}

    /** Command to query handler status */
    public record GetStatus(
            org.apache.pekko.actor.typed.ActorRef<StatusResponse> replyTo
    ) implements HandlerCommand {}

    /** Command to shut down the handler actor */
    public record Shutdown() implements HandlerCommand {}

    /** Result of handler execution */
    public sealed interface HandlerResult permits HandlerSuccess, HandlerFailure {}

    public record HandlerSuccess(DGResponse response) implements HandlerResult {}

    public record HandlerFailure(String requestId, String errorMessage) implements HandlerResult {}

    /** Status response */
    public record StatusResponse(String actorName, String status, long processedCount) {}
}
