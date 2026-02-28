/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential.
 */
package com.dgfacade.server.handler;

import com.dgfacade.common.model.DGRequest;
import com.dgfacade.common.model.DGResponse;
import java.util.function.Consumer;

/**
 * Extended handler interface for streaming (long-living) handlers.
 * These handlers send multiple updates over time until TTL expires or work completes.
 * Updates are pushed via the updateSink callback.
 */
public interface StreamingDGHandler extends DGHandler {

    /**
     * Execute with a streaming update sink. Each call to updateSink.accept() sends
     * a partial update to the client (via WebSocket, Kafka, etc.).
     *
     * @param request    the incoming DGRequest
     * @param updateSink callback to push streaming updates
     * @return the final response when streaming completes
     */
    DGResponse executeStreaming(DGRequest request, Consumer<DGResponse> updateSink);

    @Override
    default DGResponse execute(DGRequest request) {
        // Non-streaming fallback: collect all updates and return the last one
        return executeStreaming(request, update -> {});
    }

    @Override
    default boolean isStreaming() {
        return true;
    }
}
