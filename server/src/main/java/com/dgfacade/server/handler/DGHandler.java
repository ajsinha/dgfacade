/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential. Patent Pending.
 */
package com.dgfacade.server.handler;

import com.dgfacade.common.model.DGRequest;
import com.dgfacade.common.model.DGResponse;
import java.util.Map;

/**
 * Core handler interface for DGFacade.
 * Implement this to create custom request handlers that process DGRequest objects.
 *
 * Lifecycle:
 *   1. construct() - Initialize with configuration from handler JSON
 *   2. execute()   - Process the request and return a response
 *   3. stop()      - Called when TTL expires or execution completes. Signal to stop work.
 *   4. cleanup()   - Final cleanup of resources
 *
 * Handlers are instantiated per-request and managed by Pekko actors.
 * Millions of handlers may run concurrently.
 */
public interface DGHandler {

    /**
     * Initialize the handler with configuration from the handler JSON file.
     * @param config configuration dictionary from handler config
     */
    void construct(Map<String, Object> config);

    /**
     * Execute the request. This is where the business logic lives.
     * @param request the incoming DGRequest
     * @return the response to send back to the requester
     */
    DGResponse execute(DGRequest request);

    /**
     * Signal to stop processing. Called when TTL expires or cancellation is requested.
     * Implementations should check for this signal and stop gracefully.
     */
    void stop();

    /**
     * Final cleanup of any resources held by the handler.
     * Always called after execute() completes or stop() is invoked.
     */
    void cleanup();

    /**
     * @return a unique identifier for this handler instance
     */
    default String getHandlerId() {
        return getClass().getSimpleName() + "-" + Thread.currentThread().getId();
    }

    /**
     * Indicates if this handler supports streaming (multiple response updates).
     */
    default boolean isStreaming() {
        return false;
    }
}
