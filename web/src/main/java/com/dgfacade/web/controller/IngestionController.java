/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential. Patent Pending.
 */
package com.dgfacade.web.controller;

import com.dgfacade.server.ingestion.IngestionService;
import com.dgfacade.server.ingestion.RequestIngester;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller for the Request Ingestion monitoring page and API endpoints.
 * Provides real-time stats, configuration view, and manual request submission.
 */
@Controller
public class IngestionController {

    private static final Logger log = LoggerFactory.getLogger(IngestionController.class);
    private final IngestionService ingestionService;

    public IngestionController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    // ─── UI Pages ───────────────────────────────────────────────────────

    @GetMapping("/monitoring/ingestion")
    public String ingestionDashboard(Model model) {
        List<RequestIngester.IngesterStats> stats = ingestionService.getAllStats();
        List<Map<String, Object>> configs = ingestionService.getAllConfigs();
        model.addAttribute("stats", stats);
        model.addAttribute("configs", configs);
        model.addAttribute("activeCount", ingestionService.getActiveCount());
        // Pre-compute ID lists for JavaScript (avoids SpEL projection in th:inline)
        model.addAttribute("allIngesterIds", configs.stream()
                .map(c -> (String) c.get("_id")).filter(id -> id != null).toList());
        model.addAttribute("runningIngesterIds", stats.stream()
                .map(RequestIngester.IngesterStats::id).toList());
        return "pages/monitoring/ingestion";
    }

    // ─── REST API ───────────────────────────────────────────────────────

    @GetMapping("/api/v1/ingesters")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> listIngesters() {
        List<RequestIngester.IngesterStats> stats = ingestionService.getAllStats();
        return ResponseEntity.ok(Map.of(
                "total", ingestionService.listIngesterIds().size(),
                "active", ingestionService.getActiveCount(),
                "ingesters", stats
        ));
    }

    @GetMapping("/api/v1/ingesters/{id}/stats")
    @ResponseBody
    public ResponseEntity<?> ingesterStats(@PathVariable String id) {
        RequestIngester.IngesterStats stats = ingestionService.getStats(id);
        if (stats == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/api/v1/ingesters/{id}/config")
    @ResponseBody
    public ResponseEntity<?> ingesterConfig(@PathVariable String id) {
        Map<String, Object> cfg = ingestionService.getConfig(id);
        if (cfg == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(cfg);
    }

    @PostMapping("/api/v1/ingesters/{id}/submit")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> submitManual(
            @PathVariable String id,
            @RequestBody String jsonPayload) {
        boolean result = ingestionService.submitManualRequest(id, jsonPayload);
        if (!result) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "Ingester '" + id + "' not found or not running"
            ));
        }
        return ResponseEntity.ok(Map.of(
                "success", true,
                "ingester", id,
                "message", "Request submitted to ingester '" + id + "'"
        ));
    }

    @PostMapping("/api/v1/ingesters/{id}/start")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> startIngester(@PathVariable String id) {
        log.info("Start ingester request: {}", id);
        String error = ingestionService.startIngester(id);
        if (error != null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", error
            ));
        }
        return ResponseEntity.ok(Map.of(
                "success", true,
                "ingester", id,
                "message", "Ingester '" + id + "' started successfully"
        ));
    }

    @PostMapping("/api/v1/ingesters/{id}/stop")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> stopIngester(@PathVariable String id) {
        log.info("Stop ingester request: {}", id);
        String error = ingestionService.stopIngester(id);
        if (error != null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", error
            ));
        }
        return ResponseEntity.ok(Map.of(
                "success", true,
                "ingester", id,
                "message", "Ingester '" + id + "' stopped successfully"
        ));
    }

    @PostMapping("/api/v1/ingesters/{id}/enable")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> enableIngester(@PathVariable String id) {
        log.info("Enable ingester request: {}", id);
        String error = ingestionService.setIngesterEnabled(id, true);
        if (error != null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", error));
        }
        return ResponseEntity.ok(Map.of(
                "success", true,
                "ingester", id,
                "message", "Ingester '" + id + "' enabled (will auto-start on next application restart)"
        ));
    }

    @PostMapping("/api/v1/ingesters/{id}/disable")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> disableIngester(@PathVariable String id) {
        log.info("Disable ingester request: {}", id);
        String error = ingestionService.setIngesterEnabled(id, false);
        if (error != null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", error));
        }
        return ResponseEntity.ok(Map.of(
                "success", true,
                "ingester", id,
                "message", "Ingester '" + id + "' disabled (will not auto-start on next application restart)"
        ));
    }
}
