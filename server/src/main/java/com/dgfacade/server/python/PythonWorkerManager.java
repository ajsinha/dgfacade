/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential.
 */
package com.dgfacade.server.python;

import com.dgfacade.common.config.DGProperties;
import com.dgfacade.common.util.JsonUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Manages a pool of Python worker processes for executing Python-based handlers.
 *
 * <h3>Architecture</h3>
 * <pre>
 * ┌─────────────────────────────────────────────────┐
 * │              PythonWorkerManager                 │
 * │                                                  │
 * │  ┌──────────┐ ┌──────────┐ ┌──────────┐        │
 * │  │ Worker 0 │ │ Worker 1 │ │ Worker N │ ...    │
 * │  │ :25333   │ │ :25334   │ │ :25335   │        │
 * │  └──────────┘ └──────────┘ └──────────┘        │
 * │        ▲            ▲            ▲              │
 * │        └────────────┼────────────┘              │
 * │              Round-Robin Dispatch               │
 * │                     ▲                           │
 * │              DGHandlerPython                    │
 * └─────────────────────────────────────────────────┘
 * </pre>
 *
 * <p>Each worker is a Python subprocess running {@code dgfacade_worker.py}.
 * The manager handles startup, health monitoring, round-robin dispatch,
 * automatic restart of failed workers, and admin operations for the UI.</p>
 *
 * @since 1.6.2
 */
public class PythonWorkerManager {

    private static final Logger log = LoggerFactory.getLogger(PythonWorkerManager.class);

    private final String configDir;
    private volatile PythonConfig config;
    private volatile Py4JConfig py4jConfig;
    private final List<PythonWorkerProcess> workers = new CopyOnWriteArrayList<>();
    private final AtomicInteger roundRobin = new AtomicInteger(0);
    private final AtomicLong totalRequestsRouted = new AtomicLong(0);
    private final AtomicLong totalErrors = new AtomicLong(0);
    private ScheduledExecutorService healthChecker;
    private volatile boolean running = false;
    private Instant startedAt;

    public PythonWorkerManager(PythonConfig config, String configDir) {
        this.config = config;
        this.configDir = configDir;
        this.py4jConfig = loadPy4JConfig(configDir);
    }

    /**
     * Start the worker pool. Spawns N worker processes and begins health monitoring.
     *
     * @return number of workers successfully started
     */
    public int start() {
        if (!config.isEnabled()) {
            log.info("Python worker pool is disabled (py4j.json enabled=false)");
            return 0;
        }

        log.info("═══════════════════════════════════════════════════════════");
        log.info("  Py4J Python Worker Pool — Starting");
        log.info("  Workers: {}, Python: {}", config.getWorkerCount(), config.getPythonBinary());
        if (py4jConfig != null) {
            log.info("  Handlers registered: {}", py4jConfig.getHandlers().size());
        }
        log.info("═══════════════════════════════════════════════════════════");

        String propertiesJson = buildPropertiesJson();

        int started = 0;
        for (int i = 0; i < config.getWorkerCount(); i++) {
            int port = config.getGatewayPortRangeStart() + i;
            PythonWorkerProcess worker = new PythonWorkerProcess(i, port, config);
            workers.add(worker);
            if (worker.start(propertiesJson)) {
                started++;
            } else {
                log.error("Python worker {} failed to start on port {}", i, port);
            }
        }

        if (started > 0) {
            running = true;
            startedAt = Instant.now();
            startHealthChecker();
            log.info("Python worker pool started: {}/{} workers ready", started, config.getWorkerCount());
        } else {
            log.error("No Python workers could be started — Python handlers will be unavailable");
        }

        return started;
    }

    /**
     * Execute a Python handler request using a round-robin selected worker.
     */
    public String executeHandler(String handlerModule, String handlerClass,
                                  String requestJson, String configJson) throws IOException {
        if (!running || workers.isEmpty()) {
            throw new IOException("Python worker pool is not running");
        }

        int maxAttempts = workers.size();
        Exception lastException = null;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            PythonWorkerProcess worker = selectWorker();
            if (worker == null) continue;

            try {
                Map<String, Object> execRequest = new LinkedHashMap<>();
                execRequest.put("command", "execute");
                execRequest.put("handler_module", handlerModule);
                execRequest.put("handler_class", handlerClass);
                execRequest.put("request_json", requestJson);
                execRequest.put("config_json", configJson);

                totalRequestsRouted.incrementAndGet();
                return worker.executeRequest(JsonUtil.toJson(execRequest));
            } catch (IOException e) {
                lastException = e;
                totalErrors.incrementAndGet();
                log.warn("Python worker {} execution failed, trying next: {}",
                        worker.getWorkerId(), e.getMessage());
            }
        }

        throw new IOException("All Python workers failed or unavailable. Last error: "
                + (lastException != null ? lastException.getMessage() : "no healthy workers"));
    }

    /**
     * Stop all workers and the health checker.
     */
    public void stop() {
        running = false;
        if (healthChecker != null) {
            healthChecker.shutdown();
        }
        for (PythonWorkerProcess worker : workers) {
            try {
                worker.stop();
            } catch (Exception e) {
                log.warn("Error stopping Python worker {}: {}", worker.getWorkerId(), e.getMessage());
                worker.kill();
            }
        }
        workers.clear();
        log.info("Python worker pool stopped");
    }

    // ── Admin Operations ─────────────────────────────────────────────

    /**
     * Restart a specific worker by ID.
     */
    public void restartWorkerById(int workerId) {
        for (int i = 0; i < workers.size(); i++) {
            PythonWorkerProcess worker = workers.get(i);
            if (worker.getWorkerId() == workerId) {
                final int idx = i;
                log.info("Admin: restarting Python worker {}", workerId);
                worker.stop();
                int port = config.getGatewayPortRangeStart() + workerId;
                PythonWorkerProcess newWorker = new PythonWorkerProcess(workerId, port, config);
                workers.set(idx, newWorker);
                newWorker.start(buildPropertiesJson());
                log.info("Admin: Python worker {} restarted", workerId);
                return;
            }
        }
        throw new IllegalArgumentException("Worker not found: " + workerId);
    }

    /**
     * Restart all workers.
     */
    public void restartAllWorkers() {
        log.info("Admin: restarting all Python workers...");
        String propsJson = buildPropertiesJson();
        for (int i = 0; i < workers.size(); i++) {
            PythonWorkerProcess worker = workers.get(i);
            int wid = worker.getWorkerId();
            worker.stop();
            int port = config.getGatewayPortRangeStart() + wid;
            PythonWorkerProcess newWorker = new PythonWorkerProcess(wid, port, config);
            workers.set(i, newWorker);
            newWorker.start(propsJson);
        }
        log.info("Admin: all Python workers restarted");
    }

    /**
     * Reload py4j.json configuration from disk.
     */
    public void reloadConfig() {
        this.config = PythonConfig.load(configDir);
        this.py4jConfig = loadPy4JConfig(configDir);
        log.info("Admin: Python configuration reloaded");
    }

    // ── Status Reporting ─────────────────────────────────────────────

    /**
     * Full status snapshot for Admin UI.
     */
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("enabled", config.isEnabled());
        status.put("running", running);
        status.put("managerStartedAt", startedAt != null ? startedAt.toString() : null);
        status.put("totalRequestsRouted", totalRequestsRouted.get());
        status.put("totalErrors", totalErrors.get());
        status.put("workerCount", workers.size());
        status.put("healthyWorkerCount", getHealthyWorkerCount());
        status.put("pythonBinary", config.getPythonBinary());
        status.put("pythonPath", config.getPythonPath());

        List<Map<String, Object>> workerStatuses = new ArrayList<>();
        for (PythonWorkerProcess w : workers) {
            Map<String, Object> ws = w.toStatusMap();
            if (w.getStartedAt() != null) {
                ws.put("uptimeSeconds", Duration.between(w.getStartedAt(), Instant.now()).getSeconds());
            }
            workerStatuses.add(ws);
        }
        status.put("workers", workerStatuses);

        if (py4jConfig != null) {
            List<Map<String, Object>> handlerDefs = py4jConfig.getHandlers().stream().map(h -> {
                Map<String, Object> hd = new LinkedHashMap<>();
                hd.put("requestType", h.getRequestType());
                hd.put("pythonModule", h.getPythonModule());
                hd.put("pythonClass", h.getPythonClass());
                hd.put("description", h.getDescription());
                return hd;
            }).collect(Collectors.toList());
            status.put("handlers", handlerDefs);
        }

        return status;
    }

    public List<PythonWorkerProcess> getWorkers() {
        return Collections.unmodifiableList(workers);
    }

    /** Returns worker statuses as a serializable list of maps (for REST API). */
    public List<Map<String, Object>> getWorkerStatusList() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (PythonWorkerProcess w : workers) {
            Map<String, Object> ws = w.toStatusMap();
            if (w.getStartedAt() != null) {
                ws.put("uptimeSeconds", Duration.between(w.getStartedAt(), Instant.now()).getSeconds());
            }
            result.add(ws);
        }
        return result;
    }

    public int getHealthyWorkerCount() {
        return (int) workers.stream()
                .filter(w -> w.getState() == PythonWorkerProcess.State.READY)
                .count();
    }

    public Optional<Py4JConfig.PythonHandlerDef> findHandlerDef(String requestType) {
        if (py4jConfig == null) return Optional.empty();
        return py4jConfig.getHandlers().stream()
                .filter(h -> h.getRequestType().equals(requestType))
                .findFirst();
    }

    public boolean isPythonHandler(String requestType) {
        return findHandlerDef(requestType).isPresent();
    }

    public boolean isEnabled() { return config.isEnabled(); }
    public boolean isRunning() { return running; }
    public PythonConfig getConfig() { return config; }
    public Py4JConfig getPy4JConfig() { return py4jConfig; }
    public long getTotalRequestsRouted() { return totalRequestsRouted.get(); }
    public long getTotalErrors() { return totalErrors.get(); }
    public Instant getStartedAt() { return startedAt; }

    // --- Private ---

    private PythonWorkerProcess selectWorker() {
        int size = workers.size();
        if (size == 0) return null;

        for (int attempt = 0; attempt < size; attempt++) {
            int idx = Math.abs(roundRobin.getAndIncrement() % size);
            PythonWorkerProcess worker = workers.get(idx);
            if (worker.getState() == PythonWorkerProcess.State.READY) {
                return worker;
            }
        }
        return null;
    }

    private void startHealthChecker() {
        healthChecker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "python-health-checker");
            t.setDaemon(true);
            return t;
        });

        healthChecker.scheduleAtFixedRate(() -> {
            for (PythonWorkerProcess worker : workers) {
                try {
                    if (!worker.isAlive() || worker.getState() == PythonWorkerProcess.State.DEAD) {
                        if (worker.getRestartCount() < config.getMaxRestartAttempts()) {
                            log.warn("Python worker {} is dead — restarting (attempt {}/{})",
                                    worker.getWorkerId(), worker.getRestartCount() + 1, config.getMaxRestartAttempts());
                            worker.incrementRestartCount();
                            worker.start(buildPropertiesJson());
                        } else {
                            log.error("Python worker {} exceeded max restart attempts ({})",
                                    worker.getWorkerId(), config.getMaxRestartAttempts());
                        }
                    } else if (worker.getState() == PythonWorkerProcess.State.READY) {
                        worker.healthCheck();
                    }
                } catch (Exception e) {
                    log.error("Health check error for Python worker {}: {}", worker.getWorkerId(), e.getMessage());
                }
            }
        }, config.getWorkerHealthCheckIntervalSeconds(),
           config.getWorkerHealthCheckIntervalSeconds(), TimeUnit.SECONDS);
    }

    private String buildPropertiesJson() {
        try {
            if (DGProperties.isInitialized()) {
                return JsonUtil.toJson(DGProperties.get().getAll());
            }
        } catch (Exception e) {
            log.warn("Could not serialize application properties for Python workers: {}", e.getMessage());
        }
        return "{}";
    }

    private static Py4JConfig loadPy4JConfig(String configDir) {
        File file = new File(configDir, "py4j.json");
        if (!file.exists()) {
            return new Py4JConfig();
        }
        try {
            Py4JConfig cfg = JsonUtil.fromFile(file, new TypeReference<Py4JConfig>() {});
            log.info("Loaded Py4J handler config: {} handler(s) defined", cfg.getHandlers().size());
            return cfg;
        } catch (IOException e) {
            log.error("Failed to parse Py4J config from {}: {}", file.getAbsolutePath(), e.getMessage());
            return new Py4JConfig();
        }
    }
}
