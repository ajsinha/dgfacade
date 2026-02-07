/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, modification, or use is strictly prohibited without
 * explicit written permission from the copyright holder.
 * Patent Pending.
 */
package com.dgfacade.server.handler;

import com.dgfacade.common.handler.DGHandler;
import com.dgfacade.common.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Simple echo handler that returns the received payload.
 * Useful for testing connectivity and request/response flow.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class EchoHandler implements DGHandler {

    private static final Logger log = LoggerFactory.getLogger(EchoHandler.class);
    private DGRequest request;
    private HandlerStatus status = HandlerStatus.CREATED;

    @Override
    public String getRequestType() { return "ECHO"; }

    @Override
    public String getDescription() { return "Echoes back the received payload for testing"; }

    @Override
    public void start(DGRequest request) {
        this.status = HandlerStatus.STARTING;
        this.request = request;
        this.status = HandlerStatus.READY;
        log.info("EchoHandler started for request: {}", request.getRequestId());
    }

    @Override
    public DGResponse execute() {
        this.status = HandlerStatus.EXECUTING;
        Map<String, Object> result = new HashMap<>();
        result.put("echo", request.getPayload());
        result.put("requestType", request.getRequestType());
        result.put("requestId", request.getRequestId());
        result.put("source", request.getSource());
        return DGResponse.success(request.getRequestId(), getRequestType(), result);
    }

    @Override
    public void stop(DGResponse response) {
        this.status = HandlerStatus.STOPPED;
        log.info("EchoHandler stopped for request: {}", request.getRequestId());
    }

    @Override
    public HandlerStatus getStatus() { return status; }
}
