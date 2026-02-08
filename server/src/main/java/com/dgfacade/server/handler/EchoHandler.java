/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential. Patent Pending.
 */
package com.dgfacade.server.handler;

import com.dgfacade.common.model.DGRequest;
import com.dgfacade.common.model.DGResponse;
import java.util.HashMap;
import java.util.Map;

/**
 * Built-in handler that echoes back the request payload.
 * Useful for testing connectivity and round-trip latency.
 */
public class EchoHandler implements DGHandler {

    private Map<String, Object> config;
    private volatile boolean stopped = false;

    @Override
    public void construct(Map<String, Object> config) {
        this.config = config != null ? config : Map.of();
    }

    @Override
    public DGResponse execute(DGRequest request) {
        if (stopped) return DGResponse.error(request.getRequestId(), "Handler was stopped");
        Map<String, Object> data = new HashMap<>();
        data.put("echo_payload", request.getPayload());
        data.put("echo_request_type", request.getRequestType());
        data.put("echo_request_id", request.getRequestId());
        data.put("echo_timestamp", java.time.Instant.now().toString());
        data.put("handler_config", config);
        return DGResponse.success(request.getRequestId(), data);
    }

    @Override
    public void stop() { stopped = true; }

    @Override
    public void cleanup() { config = null; }
}
