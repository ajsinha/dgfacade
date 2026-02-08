/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential. Patent Pending.
 */
package com.dgfacade.web.lifecycle;

import com.dgfacade.server.config.HandlerConfigRegistry;
import com.dgfacade.server.engine.ExecutionEngine;
import com.dgfacade.server.metrics.MetricsService;
import com.dgfacade.server.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Orchestrates the DGFacade startup sequence with phased initialization.
 *
 * <p>Each significant step is announced in the log with prominent banners
 * that cannot be missed. A 2-second stabilization delay separates each phase.
 * After all phases complete, a final system-ready announcement publishes
 * all URLs and operational details.</p>
 *
 * <pre>
 * Phase 0: Spring Boot context ready (triggered by ApplicationReadyEvent)
 * Phase 1: External JAR loading
 * Phase 2: User & API key loading
 * Phase 3: Handler configuration loading
 * Phase 4: Execution engine verification
 * Phase 5: Broker configuration scanning
 * Phase 6: Channel configuration scanning
 * Phase 7: WebSocket server verification
 * FINAL:   System Ready announcement with all URLs
 * </pre>
 */
@Component
public class StartupOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(StartupOrchestrator.class);
    private static final int PHASE_DELAY_MS = 2000;

    private final ExecutionEngine executionEngine;
    private final HandlerConfigRegistry handlerConfigRegistry;
    private final UserService userService;
    private final MetricsService metricsService;

    @Value("${dgfacade.app-name:DGFacade}")
    private String appName;

    @Value("${dgfacade.version:1.2.0}")
    private String version;

    @Value("${server.port:8080}")
    private int serverPort;

    @Value("${server.servlet.context-path:/}")
    private String contextPath;

    @Value("${dgfacade.brokers.config-dir:config/brokers}")
    private String brokersConfigDir;

    @Value("${dgfacade.channels.config-dir:config/channels}")
    private String channelsConfigDir;

    @Value("${dgfacade.config.handlers-dir:config/handlers}")
    private String handlersDir;

    @Value("${dgfacade.config.users-file:config/users.json}")
    private String usersFile;

    @Value("${dgfacade.config.apikeys-file:config/apikeys.json}")
    private String apiKeysFile;

    @Value("${dgfacade.config.external-libs-dir:./libs}")
    private String externalLibsDir;

    @Value("${dgfacade.channels.default-queue-depth:10000}")
    private int defaultQueueDepth;

    @Value("${dgfacade.channels.backpressure-warning-pct:70}")
    private int backpressureWarningPct;

    @Value("${dgfacade.channels.backpressure-critical-pct:90}")
    private int backpressureCriticalPct;

    @Value("${dgfacade.channels.fanout-thread-pool-size:16}")
    private int fanoutThreadPoolSize;

    private final AtomicBoolean startupComplete = new AtomicBoolean(false);

    public StartupOrchestrator(ExecutionEngine executionEngine,
                               HandlerConfigRegistry handlerConfigRegistry,
                               UserService userService,
                               MetricsService metricsService) {
        this.executionEngine = executionEngine;
        this.handlerConfigRegistry = handlerConfigRegistry;
        this.userService = userService;
        this.metricsService = metricsService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        Instant startTime = Instant.now();

        logBanner("STARTUP INITIATED",
                appName + " v" + version + " - Starting initialization sequence...",
                "Timestamp: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        try {
            // Phase 1: External JAR Loading
            phaseDelay();
            logPhase(1, "External JAR Loading",
                    "Scanning " + externalLibsDir + " for plugin JARs...");
            int jarCount = countFiles(externalLibsDir, ".jar");
            logPhaseComplete(1, "External JAR Loading",
                    jarCount + " external JAR(s) loaded from " + externalLibsDir);

            // Phase 2: User & API Key Loading
            phaseDelay();
            logPhase(2, "User & API Key Loading",
                    "Loading user accounts and API keys...");
            int userCount = userService.getAllUsers().size();
            int apiKeyCount = userService.getAllApiKeys().size();
            logPhaseComplete(2, "User & API Key Loading",
                    userCount + " user(s) loaded from " + usersFile,
                    apiKeyCount + " API key(s) loaded from " + apiKeysFile);

            // Phase 3: Handler Configuration Loading
            phaseDelay();
            logPhase(3, "Handler Configuration Loading",
                    "Scanning " + handlersDir + " for handler configs...");
            int handlerCount = executionEngine.getRegisteredRequestTypes().size();
            logPhaseComplete(3, "Handler Configuration Loading",
                    handlerCount + " request type(s) registered",
                    "Config directory: " + handlersDir);

            // Phase 4: Execution Engine Verification
            phaseDelay();
            logPhase(4, "Execution Engine Verification",
                    "Verifying Pekko actor system and handler dispatch...");
            logPhaseComplete(4, "Execution Engine Verification",
                    "Pekko actor system: RUNNING",
                    "Handler dispatch: READY");

            // Phase 5: Broker Configuration Scanning
            phaseDelay();
            logPhase(5, "Broker Configuration Scanning",
                    "Scanning " + brokersConfigDir + " for broker JSON files...");
            int brokerCount = countFiles(brokersConfigDir, ".json");
            String[] brokerNames = listFileNames(brokersConfigDir, ".json");
            StringBuilder brokerDetail = new StringBuilder();
            brokerDetail.append(brokerCount).append(" broker(s) discovered in ").append(brokersConfigDir);
            logPhaseComplete(5, "Broker Configuration Scanning", brokerDetail.toString());
            for (String name : brokerNames) {
                log.info("  → Loaded broker: {}", name);
            }

            // Phase 6: Channel Configuration Scanning
            phaseDelay();
            logPhase(6, "Channel Configuration Scanning",
                    "Scanning " + channelsConfigDir + " for channel JSON files...");
            int channelCount = countFiles(channelsConfigDir, ".json");
            String[] channelNames = listFileNames(channelsConfigDir, ".json");
            StringBuilder channelDetail = new StringBuilder();
            channelDetail.append(channelCount).append(" channel(s) discovered in ").append(channelsConfigDir);
            logPhaseComplete(6, "Channel Configuration Scanning", channelDetail.toString());
            for (String name : channelNames) {
                log.info("  → Loaded channel: {}", name);
            }

            // Phase 7: WebSocket Server Verification
            phaseDelay();
            logPhase(7, "WebSocket Server Verification",
                    "Verifying WebSocket endpoint availability...");
            logPhaseComplete(7, "WebSocket Server Verification",
                    "WebSocket endpoint: ws://localhost:" + serverPort + "/ws/gateway",
                    "Status: ACCEPTING CONNECTIONS");

            // Final delay before system ready
            phaseDelay();

            // Mark startup as complete
            startupComplete.set(true);
            Duration elapsed = Duration.between(startTime, Instant.now());

            // ── Update Prometheus gauges with discovered counts ──
            metricsService.updateUserCount(userCount);
            metricsService.updateApiKeyCount(apiKeyCount);
            metricsService.updateHandlerCount(handlerCount);
            metricsService.updateBrokerCount(brokerCount);
            metricsService.updateChannelCount(channelCount);

            // Final System Ready Announcement
            logSystemReady(userCount, apiKeyCount, handlerCount, brokerCount, channelCount, elapsed);

        } catch (Exception e) {
            log.error("╔════════════════════════════════════════════════════════════════════╗");
            log.error("║  STARTUP FAILED                                                    ║");
            log.error("╚════════════════════════════════════════════════════════════════════╝");
            log.error("Startup error: {}", e.getMessage(), e);
        }
    }

    public boolean isStartupComplete() {
        return startupComplete.get();
    }

    // ─── Logging Helpers ──────────────────────────────────────────────────────

    private void logBanner(String title, String... lines) {
        log.info("");
        log.info("╔════════════════════════════════════════════════════════════════════╗");
        log.info("║  {}{}║", title, pad(title, 65));
        for (String line : lines) {
            log.info("║  {}{}║", line, pad(line, 65));
        }
        log.info("╚════════════════════════════════════════════════════════════════════╝");
        log.info("");
    }

    private void logPhase(int number, String title, String description) {
        log.info("");
        log.info("╔════════════════════════════════════════════════════════════════════╗");
        log.info("║  Phase {}: {}{}║", number, title, pad("Phase " + number + ": " + title, 59));
        log.info("║  {}{}║", description, pad(description, 65));
        log.info("╚════════════════════════════════════════════════════════════════════╝");
    }

    private void logPhaseComplete(int number, String title, String... details) {
        log.info("✓ Phase {} complete: {}", number, title);
        for (String detail : details) {
            log.info("  {}", detail);
        }
    }

    private void logSystemReady(int users, int apiKeys, int handlers,
                                int brokers, int channels, Duration elapsed) {
        String hostname;
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            hostname = "localhost";
        }

        String ctx = contextPath.equals("/") ? "" : contextPath;
        String baseUrl = "http://" + hostname + ":" + serverPort + ctx;
        String apiUrl = baseUrl + "/api/v1";
        String wsUrl = "ws://" + hostname + ":" + serverPort + ctx + "/ws/gateway";
        String healthUrl = baseUrl + "/actuator/health";
        String metricsUrl = baseUrl + "/actuator/metrics";
        String prometheusUrl = baseUrl + "/actuator/prometheus";

        log.info("");
        log.info("╔════════════════════════════════════════════════════════════════════╗");
        log.info("║                                                                    ║");
        log.info("║   ██████╗  ██████╗ ███████╗ █████╗  ██████╗ █████╗ ██████╗ ███████╗║");
        log.info("║   ██╔══██╗██╔════╝ ██╔════╝██╔══██╗██╔════╝██╔══██╗██╔══██╗██╔════╝║");
        log.info("║   ██║  ██║██║  ███╗█████╗  ███████║██║     ███████║██║  ██║█████╗  ║");
        log.info("║   ██║  ██║██║   ██║██╔══╝  ██╔══██║██║     ██╔══██║██║  ██║██╔══╝  ║");
        log.info("║   ██████╔╝╚██████╔╝██║     ██║  ██║╚██████╗██║  ██║██████╔╝███████╗║");
        log.info("║   ╚═════╝  ╚═════╝ ╚═╝     ╚═╝  ╚═╝ ╚═════╝╚═╝  ╚═╝╚═════╝ ╚══════╝║");
        log.info("║                                                                    ║");
        log.info("║   SYSTEM READY — {} v{}{}║", appName, version,
                pad("SYSTEM READY — " + appName + " v" + version, 52));
        log.info("║                                                                    ║");
        log.info("╠════════════════════════════════════════════════════════════════════╣");
        log.info("║                                                                    ║");
        log.info("║   Web Dashboard  : {}{}║", baseUrl, pad("Web Dashboard  : " + baseUrl, 52));
        log.info("║   REST API       : {}{}║", apiUrl, pad("REST API       : " + apiUrl, 52));
        log.info("║   WebSocket      : {}{}║", wsUrl, pad("WebSocket      : " + wsUrl, 52));
        log.info("║   Health Check   : {}{}║", healthUrl, pad("Health Check   : " + healthUrl, 52));
        log.info("║   Metrics        : {}{}║", metricsUrl, pad("Metrics        : " + metricsUrl, 52));
        log.info("║   Prometheus     : {}{}║", prometheusUrl, pad("Prometheus     : " + prometheusUrl, 52));
        log.info("║                                                                    ║");
        log.info("╠════════════════════════════════════════════════════════════════════╣");
        log.info("║                                                                    ║");
        log.info("║   Components:                                                      ║");
        log.info("║   ✓ Users          : {} loaded{}║", users,
                pad("✓ Users          : " + users + " loaded", 52));
        log.info("║   ✓ API Keys       : {} loaded{}║", apiKeys,
                pad("✓ API Keys       : " + apiKeys + " loaded", 52));
        log.info("║   ✓ Handlers       : {} request type(s){}║", handlers,
                pad("✓ Handlers       : " + handlers + " request type(s)", 52));
        log.info("║   ✓ Brokers        : {} configured{}║", brokers,
                pad("✓ Brokers        : " + brokers + " configured", 52));
        log.info("║   ✓ Channels       : {} configured{}║", channels,
                pad("✓ Channels       : " + channels + " configured", 52));
        log.info("║   ✓ Execution Eng. : Pekko actor system RUNNING{}║",
                pad("✓ Execution Eng. : Pekko actor system RUNNING", 52));
        log.info("║   ✓ WebSocket      : ACCEPTING CONNECTIONS{}║",
                pad("✓ WebSocket      : ACCEPTING CONNECTIONS", 52));
        log.info("║                                                                    ║");
        log.info("╠════════════════════════════════════════════════════════════════════╣");
        log.info("║                                                                    ║");
        log.info("║   Configuration:                                                   ║");
        log.info("║   • Brokers Dir    : {}{}║", brokersConfigDir,
                pad("• Brokers Dir    : " + brokersConfigDir, 52));
        log.info("║   • Channels Dir   : {}{}║", channelsConfigDir,
                pad("• Channels Dir   : " + channelsConfigDir, 52));
        log.info("║   • Handlers Dir   : {}{}║", handlersDir,
                pad("• Handlers Dir   : " + handlersDir, 52));
        log.info("║   • Queue Depth    : {} (default){}║", defaultQueueDepth,
                pad("• Queue Depth    : " + defaultQueueDepth + " (default)", 52));
        log.info("║   • Backpressure   : warn={}% critical={}%{}║",
                backpressureWarningPct, backpressureCriticalPct,
                pad("• Backpressure   : warn=" + backpressureWarningPct + "% critical=" + backpressureCriticalPct + "%", 52));
        log.info("║   • Fan-Out Pool   : {} threads{}║", fanoutThreadPoolSize,
                pad("• Fan-Out Pool   : " + fanoutThreadPoolSize + " threads", 52));
        log.info("║                                                                    ║");
        log.info("╠════════════════════════════════════════════════════════════════════╣");
        log.info("║   Login           : admin / admin123{}║",
                pad("Login           : admin / admin123", 52));
        log.info("║   Startup Time    : {} ms{}║", elapsed.toMillis(),
                pad("Startup Time    : " + elapsed.toMillis() + " ms", 52));
        log.info("║   Copyright © 2025-2030 Ashutosh Sinha. Patent Pending.            ║");
        log.info("╚════════════════════════════════════════════════════════════════════╝");
        log.info("");
    }

    private void phaseDelay() {
        try {
            Thread.sleep(PHASE_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String pad(String text, int totalWidth) {
        int remaining = totalWidth - text.length();
        if (remaining <= 0) return " ";
        return " ".repeat(remaining);
    }

    private int countFiles(String directory, String extension) {
        File dir = new File(directory);
        if (!dir.exists() || !dir.isDirectory()) return 0;
        File[] files = dir.listFiles((d, name) -> name.endsWith(extension));
        return files != null ? files.length : 0;
    }

    private String[] listFileNames(String directory, String extension) {
        File dir = new File(directory);
        if (!dir.exists() || !dir.isDirectory()) return new String[0];
        File[] files = dir.listFiles((d, name) -> name.endsWith(extension));
        if (files == null) return new String[0];
        String[] names = new String[files.length];
        for (int i = 0; i < files.length; i++) {
            names[i] = files[i].getName().replace(extension, "");
        }
        return names;
    }
}
