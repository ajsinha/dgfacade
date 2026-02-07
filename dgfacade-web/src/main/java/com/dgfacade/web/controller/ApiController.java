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

import com.dgfacade.common.handler.HandlerRegistry;
import com.dgfacade.common.model.DGRequest;
import com.dgfacade.common.model.DGResponse;
import com.dgfacade.common.model.RequestSource;
import com.dgfacade.common.model.StreamingSession;
import com.dgfacade.messaging.RequestDispatcher;
import com.dgfacade.server.service.ActorRequestDispatcher;
import com.dgfacade.server.service.StreamingSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1")
public class ApiController {

    private static final Logger log = LoggerFactory.getLogger(ApiController.class);

    private final RequestDispatcher dispatcher;
    private final HandlerRegistry handlerRegistry;
    private final StreamingSessionManager sessionManager;

    public ApiController(RequestDispatcher dispatcher, HandlerRegistry handlerRegistry,
                         StreamingSessionManager sessionManager) {
        this.dispatcher = dispatcher;
        this.handlerRegistry = handlerRegistry;
        this.sessionManager = sessionManager;
    }

    // ── One-shot & streaming handler dispatch ─────────────────────────

    @PostMapping("/request")
    public CompletableFuture<ResponseEntity<DGResponse>> processRequest(@RequestBody DGRequest request) {
        log.info("REST API request received: type={}, id={}, streaming={}",
                request.getRequestType(), request.getRequestId(), request.isStreaming());
        request.setSource(RequestSource.REST_API);
        return dispatcher.dispatch(request).thenApply(ResponseEntity::ok);
    }

    // ── Streaming session management ──────────────────────────────────

    @GetMapping("/streaming/sessions")
    public ResponseEntity<Collection<StreamingSession>> listStreamingSessions() {
        return ResponseEntity.ok(sessionManager.getActiveSessions());
    }

    @GetMapping("/streaming/sessions/{sessionId}")
    public ResponseEntity<?> getStreamingSession(@PathVariable String sessionId) {
        return sessionManager.getSession(sessionId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/streaming/sessions/{sessionId}")
    public CompletableFuture<ResponseEntity<DGResponse>> stopStreamingSession(
            @PathVariable String sessionId) {
        if (dispatcher instanceof ActorRequestDispatcher actorDispatcher) {
            return actorDispatcher.stopStreamingSession(sessionId)
                    .thenApply(ResponseEntity::ok);
        }
        return CompletableFuture.completedFuture(
                ResponseEntity.badRequest().body(
                        DGResponse.error(sessionId, "Streaming not supported by this dispatcher")));
    }

    // ── Handler introspection ─────────────────────────────────────────

    @GetMapping("/handlers")
    public ResponseEntity<Collection<String>> getHandlers() {
        return ResponseEntity.ok(handlerRegistry.getRegisteredTypes());
    }

    // ── Health check ──────────────────────────────────────────────────

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "DGFacade",
                "handlers", handlerRegistry.getRegisteredTypes().size(),
                "streamingSessions", sessionManager.getActiveCount(),
                "timestamp", System.currentTimeMillis()
        ));
    }
}
