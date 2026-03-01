/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential.
 */
package com.dgfacade.web.controller;

import com.dgfacade.server.cluster.ClusterService;
import com.dgfacade.server.engine.ExecutionEngine;
import com.dgfacade.server.ingestion.IngestionService;
import com.dgfacade.server.service.BrokerService;
import com.dgfacade.server.service.InputChannelService;
import com.dgfacade.server.service.OutputChannelService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.dgfacade.server.metrics.MetricsService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Timer;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Ping and Health Check controller. All endpoints are public (no auth required).
 */
@Controller
public class PingController {

    private final ExecutionEngine engine;
    private final IngestionService ingestionService;
    private final BrokerService brokerService;
    private final InputChannelService inputChannelService;
    private final OutputChannelService outputChannelService;
    private final ClusterService clusterService;
    private final MetricsService metricsService;
    private final Instant startedAt = Instant.now();

    @Value("${dgfacade.app-name:DGFacade}")
    private String appName;

    @Value("${dgfacade.version:1.6.2}")
    private String version;

    public PingController(ExecutionEngine engine, IngestionService ingestionService,
                          BrokerService brokerService, InputChannelService inputChannelService,
                          OutputChannelService outputChannelService, ClusterService clusterService,
                          MetricsService metricsService) {
        this.engine = engine;
        this.ingestionService = ingestionService;
        this.brokerService = brokerService;
        this.inputChannelService = inputChannelService;
        this.outputChannelService = outputChannelService;
        this.clusterService = clusterService;
        this.metricsService = metricsService;
    }

    /** GET /ping - Simple text ping for load balancers and uptime monitors. */
    @GetMapping("/ping")
    @ResponseBody
    public String ping() {
        return "pong";
    }

    /** GET /api/ping - JSON ping with basic metadata. */
    @GetMapping("/api/ping")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> apiPing() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "UP");
        response.put("app", appName);
        response.put("version", version);
        response.put("timestamp", Instant.now().toString());
        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
        response.put("uptime", formatDuration(Duration.ofMillis(uptimeMs)));
        return ResponseEntity.ok(response);
    }

    /** GET /health - Comprehensive health check page (UI). */
    @GetMapping("/health")
    public String healthPage(Model model) {
        model.addAttribute("appName", appName);
        model.addAttribute("version", version);

        // JVM metrics
        Runtime rt = Runtime.getRuntime();
        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
        model.addAttribute("uptime", formatDuration(Duration.ofMillis(uptimeMs)));
        model.addAttribute("heapUsed", formatBytes(rt.totalMemory() - rt.freeMemory()));
        model.addAttribute("heapMax", formatBytes(rt.maxMemory()));
        model.addAttribute("heapPct", (int) (((rt.totalMemory() - rt.freeMemory()) * 100.0) / rt.maxMemory()));
        model.addAttribute("cpuCount", rt.availableProcessors());
        model.addAttribute("javaVersion", System.getProperty("java.version"));
        model.addAttribute("osName", System.getProperty("os.name") + " " + System.getProperty("os.arch"));

        // Component counts
        model.addAttribute("handlerCount", engine.getRegisteredRequestTypes().size());
        model.addAttribute("brokerCount", brokerService.listBrokerIds().size());
        model.addAttribute("inputChannelCount", inputChannelService.listChannelIds().size());
        model.addAttribute("outputChannelCount", outputChannelService.listChannelIds().size());
        model.addAttribute("ingesterCount", ingestionService.listIngesterIds().size());
        model.addAttribute("activeIngesters", ingestionService.getActiveCount());
        model.addAttribute("clusterSize", clusterService.getClusterSize());

        // Overall status
        boolean healthy = engine.getRegisteredRequestTypes().size() > 0;
        model.addAttribute("overallStatus", healthy ? "UP" : "DEGRADED");

        return "pages/monitoring/health";
    }

    /** GET /api/health - JSON health check for automated monitoring. */
    @GetMapping("/api/health")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> apiHealth() {
        Map<String, Object> health = new LinkedHashMap<>();
        Runtime rt = Runtime.getRuntime();
        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();

        health.put("status", "UP");
        health.put("version", version);
        health.put("uptime", formatDuration(Duration.ofMillis(uptimeMs)));

        Map<String, Object> components = new LinkedHashMap<>();
        components.put("handlers", Map.of("status", "UP", "count", engine.getRegisteredRequestTypes().size()));
        components.put("brokers", Map.of("status", "UP", "count", brokerService.listBrokerIds().size()));
        components.put("inputChannels", Map.of("status", "UP", "count", inputChannelService.listChannelIds().size()));
        components.put("outputChannels", Map.of("status", "UP", "count", outputChannelService.listChannelIds().size()));
        components.put("ingesters", Map.of("active", ingestionService.getActiveCount(), "total", ingestionService.listIngesterIds().size()));
        components.put("cluster", Map.of("size", clusterService.getClusterSize()));
        health.put("components", components);

        Map<String, Object> jvm = new LinkedHashMap<>();
        jvm.put("heapUsed", formatBytes(rt.totalMemory() - rt.freeMemory()));
        jvm.put("heapMax", formatBytes(rt.maxMemory()));
        jvm.put("cpuCount", rt.availableProcessors());
        jvm.put("javaVersion", System.getProperty("java.version"));
        health.put("jvm", jvm);

        return ResponseEntity.ok(health);
    }

    /** GET /api/v1/metrics/snapshot — Current metrics snapshot for sparkline charts. */
    @GetMapping("/api/v1/metrics/snapshot")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> metricsSnapshot() {
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("timestamp", Instant.now().toString());

        Runtime rt = Runtime.getRuntime();
        long heapUsed = rt.totalMemory() - rt.freeMemory();
        long heapMax = rt.maxMemory();
        snap.put("heapUsedMB", Math.round(heapUsed / (1024.0 * 1024.0)));
        snap.put("heapMaxMB", Math.round(heapMax / (1024.0 * 1024.0)));
        snap.put("heapPct", (int) ((heapUsed * 100.0) / heapMax));
        snap.put("activeRequests", metricsService.getActiveRequestCount());
        snap.put("activeIngesters", ingestionService.getActiveCount());
        snap.put("clusterSize", clusterService.getClusterSize());

        // Aggregate counter values from Micrometer
        double totalRequests = 0;
        double totalErrors = 0;
        double totalTimeouts = 0;
        double meanLatencyMs = 0;
        int timerCount = 0;

        for (Meter meter : metricsService.getRegistry().getMeters()) {
            String name = meter.getId().getName();
            if ("dgfacade.requests.total".equals(name) && meter instanceof Counter c) {
                String status = meter.getId().getTag("status");
                if ("success".equals(status) || "submitted".equals(status)) totalRequests += c.count();
                if ("error".equals(status)) totalErrors += c.count();
            }
            if ("dgfacade.handler.timeouts.total".equals(name) && meter instanceof Counter c) {
                totalTimeouts += c.count();
            }
            if ("dgfacade.handler.execution.duration".equals(name) && meter instanceof Timer t) {
                if (t.count() > 0) {
                    meanLatencyMs += t.mean(TimeUnit.MILLISECONDS);
                    timerCount++;
                }
            }
        }
        snap.put("totalRequests", (long) totalRequests);
        snap.put("totalErrors", (long) totalErrors);
        snap.put("totalTimeouts", (long) totalTimeouts);
        snap.put("avgLatencyMs", timerCount > 0 ? Math.round(meanLatencyMs / timerCount) : 0);

        return ResponseEntity.ok(snap);
    }

    private String formatDuration(Duration d) {
        long days = d.toDays();
        long hours = d.toHoursPart();
        long minutes = d.toMinutesPart();
        long seconds = d.toSecondsPart();
        if (days > 0) return days + "d " + hours + "h " + minutes + "m";
        if (hours > 0) return hours + "h " + minutes + "m " + seconds + "s";
        if (minutes > 0) return minutes + "m " + seconds + "s";
        return seconds + "s";
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String unit = "KMGTPE".charAt(exp - 1) + "B";
        return String.format("%.1f %s", bytes / Math.pow(1024, exp), unit);
    }
}
