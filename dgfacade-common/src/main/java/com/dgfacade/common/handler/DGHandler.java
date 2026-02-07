/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, modification, or use is strictly prohibited without
 * explicit written permission from the copyright holder.
 * Patent Pending.
 */
package com.dgfacade.common.handler;

import com.dgfacade.common.model.DGRequest;
import com.dgfacade.common.model.DGResponse;
import com.dgfacade.common.model.HandlerStatus;

/**
 * Core handler interface for DGFacade. All request handlers must implement
 * this interface. The framework invokes lifecycle methods in order:
 * start() -> execute() -> stop()
 */
public interface DGHandler {

    /** Returns the unique request type this handler processes. */
    String getRequestType();

    /** Returns a human-readable description of this handler. */
    String getDescription();

    /**
     * Initialize the handler with the incoming request.
     * Perform validation and resource acquisition here.
     * @param request the incoming request
     * @throws Exception if initialization fails
     */
    void start(DGRequest request) throws Exception;

    /**
     * Execute the handler's business logic.
     * @return the response containing results
     * @throws Exception if execution fails
     */
    DGResponse execute() throws Exception;

    /**
     * Clean up resources and perform post-processing.
     * This is called regardless of whether execute() succeeded or failed.
     * @param response the response from execute(), or null if execute() failed
     */
    void stop(DGResponse response);

    /** Returns the current status of this handler instance. */
    HandlerStatus getStatus();

    /**
     * Returns true if this handler is a long-living streaming handler.
     * One-shot handlers return false (the default).
     * Streaming handlers override this to return true.
     */
    default boolean isStreaming() { return false; }
}
