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

import com.dgfacade.common.model.DGResponse;
import com.dgfacade.common.model.ResponseChannel;

import java.util.Set;
import java.util.function.Consumer;

/**
 * Extension of DGHandler for long-living streaming handlers.
 *
 * Unlike one-shot handlers where execute() runs once and returns,
 * a StreamingHandler's execute() runs a loop (or event-driven process)
 * that can produce data over time. When data is available, the handler
 * calls the DataPublisher callback to push it to the configured channels.
 *
 * Multi-channel support (v1.1.0):
 *   A streaming handler can publish to any combination of channels:
 *   WEBSOCKET, KAFKA, and/or ACTIVEMQ simultaneously. The handler does
 *   NOT need to know which channels are active — it simply calls
 *   dataPublisher.accept(response), and the framework fans out to all
 *   configured channels.
 *
 * Lifecycle:
 *   1. start(request)       — validate, initialize
 *   2. setDataPublisher()   — framework injects callback before execute()
 *   3. execute()            — starts the streaming loop (blocking until TTL or shutdown)
 *   4. stop(lastResponse)   — cleanup, called on TTL expiry or manual shutdown
 */
public interface StreamingHandler extends DGHandler {

    /** Returns true — this is a streaming handler. */
    @Override
    default boolean isStreaming() { return true; }

    /** Default time-to-live in minutes for this handler's streaming session. */
    long getDefaultTtlMinutes();

    /**
     * Default response channels for this handler.
     * Returns a set of one or more channels. The requester can override
     * this via the request's responseChannels field.
     *
     * Example: Set.of(WEBSOCKET)           — WebSocket only
     * Example: Set.of(WEBSOCKET, KAFKA)    — fan-out to both
     * Example: Set.of(KAFKA, ACTIVEMQ)     — messaging only
     */
    Set<ResponseChannel> getDefaultResponseChannels();

    /**
     * Injected by the framework before execute() is called.
     * The handler calls this consumer whenever it has data to publish.
     * Each DGResponse published through this callback is routed to ALL
     * channels configured for this session.
     */
    void setDataPublisher(Consumer<DGResponse> publisher);

    /**
     * Called by the framework to signal the handler to stop its streaming loop.
     * Implementations should set a flag that causes execute() to exit gracefully.
     */
    void requestShutdown();

    /**
     * Returns true if the handler's streaming loop is still running.
     */
    boolean isRunning();
}
