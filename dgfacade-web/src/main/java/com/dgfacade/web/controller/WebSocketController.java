/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, modification, or use is strictly prohibited without
 * explicit written permission from the copyright holder.
 * Patent Pending.
 */
package com.dgfacade.web.controller;

import com.dgfacade.common.model.DGRequest;
import com.dgfacade.common.model.DGResponse;
import com.dgfacade.common.model.RequestSource;
import com.dgfacade.messaging.RequestDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

/**
 * WebSocket STOMP controller for real-time request submission and responses.
 *
 * Clients connect to /ws (SockJS) and can:
 *   - Send requests to /app/request   → response sent to /topic/responses
 *   - Subscribe to /topic/stream/{sessionId}  → live streaming data
 */
@Controller
public class WebSocketController {

    private static final Logger log = LoggerFactory.getLogger(WebSocketController.class);

    private final RequestDispatcher dispatcher;
    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketController(RequestDispatcher dispatcher,
                               SimpMessagingTemplate messagingTemplate) {
        this.dispatcher = dispatcher;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Handles requests sent to /app/request via STOMP.
     * The response is broadcast to /topic/responses.
     * For streaming handlers, the initial acknowledgement goes here;
     * subsequent data goes to /topic/stream/{sessionId}.
     */
    @MessageMapping("/request")
    @SendTo("/topic/responses")
    public DGResponse handleRequest(DGRequest request) {
        log.info("WebSocket request received: type={}, id={}",
                request.getRequestType(), request.getRequestId());
        request.setSource(RequestSource.WEBSOCKET);

        try {
            return dispatcher.dispatch(request).get();
        } catch (Exception e) {
            log.error("WebSocket dispatch failed", e);
            return DGResponse.error(request.getRequestId(),
                    "WebSocket dispatch failed: " + e.getMessage());
        }
    }
}
