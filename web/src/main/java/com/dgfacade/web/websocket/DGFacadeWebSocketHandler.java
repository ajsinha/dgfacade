/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential.
 */
package com.dgfacade.web.websocket;

import com.dgfacade.common.model.DGRequest;
import com.dgfacade.common.model.DGResponse;
import com.dgfacade.common.util.JsonUtil;
import com.dgfacade.server.engine.ExecutionEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket handler for DGFacade.
 * Accepts DGRequest JSON over WebSocket, submits to the execution engine,
 * and sends DGResponse back. Supports streaming handlers sending multiple updates.
 */
@Component
public class DGFacadeWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(DGFacadeWebSocketHandler.class);
    private final ExecutionEngine engine;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public DGFacadeWebSocketHandler(ExecutionEngine engine) {
        this.engine = engine;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), session);
        log.info("WebSocket connected: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            DGRequest request = JsonUtil.fromJson(message.getPayload(), DGRequest.class);
            request.setSourceChannel("WebSocket");

            engine.submit(request).thenAccept(response -> {
                try {
                    session.sendMessage(new TextMessage(JsonUtil.toJson(response)));
                } catch (IOException e) {
                    log.error("Failed to send WebSocket response", e);
                }
            });
        } catch (Exception e) {
            log.error("WebSocket message handling error", e);
            try {
                DGResponse error = DGResponse.error("unknown", "Invalid request: " + e.getMessage());
                session.sendMessage(new TextMessage(JsonUtil.toJson(error)));
            } catch (IOException ex) {
                log.error("Failed to send error response", ex);
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        log.info("WebSocket disconnected: {}", session.getId());
    }

    public void sendToSession(String sessionId, DGResponse response) {
        WebSocketSession session = sessions.get(sessionId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(JsonUtil.toJson(response)));
            } catch (IOException e) {
                log.error("Failed to send to session {}", sessionId, e);
            }
        }
    }
}
