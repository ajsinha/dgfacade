/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential. Patent Pending.
 */
package com.dgfacade.web.controller;

import com.dgfacade.common.model.DGRequest;
import com.dgfacade.common.model.DGResponse;
import com.dgfacade.common.model.HandlerState;
import com.dgfacade.server.engine.ExecutionEngine;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * REST API controller for DGFacade.
 * Provides the primary HTTP/JSON endpoint for submitting requests.
 */
@RestController
@RequestMapping("/api/v1")
public class ApiController {

    private final ExecutionEngine engine;

    public ApiController(ExecutionEngine engine) {
        this.engine = engine;
    }

    /**
     * POST /api/v1/request - Submit a DGRequest for execution.
     * The request JSON must include: api_key, request_type, payload.
     */
    @PostMapping("/request")
    public CompletableFuture<ResponseEntity<DGResponse>> submitRequest(@RequestBody DGRequest request) {
        request.setSourceChannel("REST");
        return engine.submit(request).thenApply(ResponseEntity::ok);
    }

    /** GET /api/v1/handlers - List all registered handler types. */
    @GetMapping("/handlers")
    public ResponseEntity<Map<String, Object>> getHandlers() {
        return ResponseEntity.ok(Map.of(
                "request_types", engine.getRegisteredRequestTypes(),
                "count", engine.getRegisteredRequestTypes().size()));
    }

    /** GET /api/v1/status - Get recent handler execution states. */
    @GetMapping("/status")
    public ResponseEntity<List<HandlerState>> getStatus() {
        return ResponseEntity.ok(engine.getRecentStates());
    }

    /** POST /api/v1/reload - Reload handler configurations. */
    @PostMapping("/reload")
    public ResponseEntity<Map<String, String>> reload() {
        engine.reloadConfigs();
        return ResponseEntity.ok(Map.of("status", "reloaded"));
    }

    /** GET /api/v1/health - Health check endpoint. */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "handlers", engine.getRegisteredRequestTypes().size(),
                "recent_executions", engine.getRecentStates().size()));
    }
}
