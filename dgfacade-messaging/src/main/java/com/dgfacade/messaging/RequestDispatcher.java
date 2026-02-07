/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, modification, or use is strictly prohibited without
 * explicit written permission from the copyright holder.
 * Patent Pending.
 */
package com.dgfacade.messaging;

import com.dgfacade.common.model.DGRequest;
import com.dgfacade.common.model.DGResponse;
import com.dgfacade.common.model.StreamingSession;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Dispatches incoming requests to the handler execution framework.
 */
public interface RequestDispatcher {

    /** Dispatch a one-shot request for processing. Returns a future with the response. */
    CompletableFuture<DGResponse> dispatch(DGRequest request);

    /** Start a streaming handler session. Returns a future with the initial response (session info). */
    default CompletableFuture<DGResponse> startStreaming(DGRequest request) {
        return dispatch(request);
    }

    /** Stop an active streaming session. */
    default CompletableFuture<DGResponse> stopStreaming(String sessionId) {
        return CompletableFuture.completedFuture(DGResponse.error(sessionId, "Streaming not supported"));
    }

    /** Get all active streaming sessions. */
    default Collection<StreamingSession> getStreamingSessions() {
        return java.util.Collections.emptyList();
    }

    /** Get a specific streaming session. */
    default Optional<StreamingSession> getStreamingSession(String sessionId) {
        return Optional.empty();
    }
}
