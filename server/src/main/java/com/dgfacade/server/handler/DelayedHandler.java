/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential.
 */
package com.dgfacade.server.handler;

import com.dgfacade.common.model.DGRequest;
import com.dgfacade.common.model.DGResponse;
import java.util.Map;

/**
 * Handler that simulates a long-running operation with configurable delay.
 * Useful for testing TTL enforcement and timeout behavior.
 */
public class DelayedHandler implements DGHandler {

    private volatile boolean stopped = false;
    private long delayMs = 5000;

    @Override
    public void construct(Map<String, Object> config) {
        if (config != null && config.containsKey("delay_ms")) {
            delayMs = ((Number) config.get("delay_ms")).longValue();
        }
    }

    @Override
    public DGResponse execute(DGRequest request) {
        long start = System.currentTimeMillis();
        // Check every 100ms if we should stop
        while (!stopped && (System.currentTimeMillis() - start) < delayMs) {
            try { Thread.sleep(100); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return DGResponse.error(request.getRequestId(), "Interrupted");
            }
        }
        if (stopped) return DGResponse.error(request.getRequestId(), "Stopped by TTL or cancellation");
        return DGResponse.success(request.getRequestId(),
                Map.of("delayed_ms", System.currentTimeMillis() - start, "message", "Completed after delay"));
    }

    @Override
    public void stop() { stopped = true; }

    @Override
    public void cleanup() {}
}
