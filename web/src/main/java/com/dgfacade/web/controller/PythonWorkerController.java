/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential.
 */
package com.dgfacade.web.controller;

import com.dgfacade.server.python.Py4JConfig;
import com.dgfacade.server.python.PythonWorkerManager;
import com.dgfacade.server.python.PythonWorkerProcess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller for managing and monitoring Python worker processes.
 * Provides REST endpoints consumed by the Admin UI (python-workers.html)
 * and a Thymeleaf page route.
 *
 * @since 1.6.2
 */
@Controller
public class PythonWorkerController {

    private static final Logger log = LoggerFactory.getLogger(PythonWorkerController.class);

    private final PythonWorkerManager workerManager;

    public PythonWorkerController(PythonWorkerManager workerManager) {
        this.workerManager = workerManager;
    }

    // ── Page Route ──────────────────────────────────────────────────

    /**
     * Thymeleaf page: /admin/python-workers
     */
    @GetMapping("/admin/python-workers")
    public String pythonWorkersPage() {
        return "pages/admin/python-workers";
    }

    // ── REST API Endpoints ──────────────────────────────────────────

    /**
     * GET /api/v1/python/status — Full status snapshot.
     */
    @GetMapping("/api/v1/python/status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(workerManager.getStatus());
    }

    /**
     * GET /api/v1/python/workers — List all worker statuses.
     */
    @GetMapping("/api/v1/python/workers")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getWorkers() {
        return ResponseEntity.ok(workerManager.getWorkerStatusList());
    }

    /**
     * POST /api/v1/python/workers/{id}/restart — Restart a specific worker.
     */
    @PostMapping("/api/v1/python/workers/{id}/restart")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> restartWorker(@PathVariable("id") int workerId) {
        log.info("Admin request: restart Python worker {}", workerId);
        try {
            workerManager.restartWorkerById(workerId);
            return ResponseEntity.ok(Map.of(
                    "status", "OK",
                    "message", "Worker " + workerId + " restart initiated"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * POST /api/v1/python/restart-all — Restart all workers.
     */
    @PostMapping("/api/v1/python/restart-all")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> restartAll() {
        log.info("Admin request: restart all Python workers");
        workerManager.restartAllWorkers();
        return ResponseEntity.ok(Map.of(
                "status", "OK",
                "message", "All workers restart initiated"
        ));
    }

    /**
     * POST /api/v1/python/reload-config — Reload py4j.json from disk.
     */
    @PostMapping("/api/v1/python/reload-config")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> reloadConfig() {
        log.info("Admin request: reload Py4J configuration");
        workerManager.reloadConfig();
        return ResponseEntity.ok(Map.of(
                "status", "OK",
                "message", "Configuration reloaded. Restart workers to apply changes."
        ));
    }

    /**
     * GET /api/v1/python/handlers — List configured Python handlers.
     */
    @GetMapping("/api/v1/python/handlers")
    @ResponseBody
    public ResponseEntity<Object> getHandlers() {
        Py4JConfig config = workerManager.getPy4JConfig();
        if (config == null) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(config.getHandlers());
    }
}
