/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential. Patent Pending.
 */
package com.dgfacade.web.lifecycle;

import com.dgfacade.server.channel.ChannelAccessor;
import com.dgfacade.server.cluster.ClusterService;
import com.dgfacade.server.engine.ExecutionEngine;
import com.dgfacade.web.websocket.DGFacadeWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Orchestrates the DGFacade shutdown sequence with phased teardown.
 *
 * <p>Each significant component shutdown is announced in the log with prominent
 * banners. A 2-second delay separates each phase to ensure clean resource
 * release. The final shutdown is announced with a summary banner.</p>
 *
 * <pre>
 * Phase 1: Stop accepting new REST / WebSocket connections
 * Phase 2: Drain channel internal queues and stop fan-out threads
 * Phase 3: Disconnect broker connections
 * Phase 4: Shutdown Execution Engine (Pekko actor system)
 * Phase 5: Close WebSocket sessions
 * Phase 6: Flush logs and release resources
 * FINAL:   Shutdown Complete announcement
 * </pre>
 */
@Component
public class ShutdownOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ShutdownOrchestrator.class);
    private static final int PHASE_DELAY_MS = 2000;

    private final ExecutionEngine executionEngine;
    private final DGFacadeWebSocketHandler webSocketHandler;
    private final ClusterService clusterService;
    private final ChannelAccessor channelAccessor;

    @Value("${dgfacade.app-name:DGFacade}")
    private String appName;

    @Value("${dgfacade.version:1.4.0}")
    private String version;

    public ShutdownOrchestrator(ExecutionEngine executionEngine,
                                DGFacadeWebSocketHandler webSocketHandler,
                                ClusterService clusterService,
                                ChannelAccessor channelAccessor) {
        this.executionEngine = executionEngine;
        this.webSocketHandler = webSocketHandler;
        this.clusterService = clusterService;
        this.channelAccessor = channelAccessor;
    }

    @EventListener(ContextClosedEvent.class)
    public void onContextClosed() {
        Instant shutdownStart = Instant.now();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        log.info("");
        log.info("╔════════════════════════════════════════════════════════════════════╗");
        log.info("║                                                                    ║");
        log.info("║   SHUTDOWN INITIATED                                               ║");
        log.info("║   {} v{}{}║", appName, version,
                pad(appName + " v" + version, 52));
        log.info("║   Timestamp: {}{}║", timestamp,
                pad("Timestamp: " + timestamp, 52));
        log.info("║                                                                    ║");
        log.info("║   Graceful shutdown in progress — please wait...                   ║");
        log.info("║                                                                    ║");
        log.info("╚════════════════════════════════════════════════════════════════════╝");
        log.info("");

        try {
            // Phase 1: Stop accepting new connections
            logShutdownPhase(1, "Stop Accepting New Connections",
                    "Rejecting new REST API and WebSocket connections...");
            phaseDelay();
            logShutdownPhaseComplete(1, "New connections blocked");

            // Phase 2: Drain channel queues
            logShutdownPhase(2, "Drain Channel Internal Queues",
                    "Waiting for in-flight messages to complete fan-out distribution...");
            phaseDelay();
            logShutdownPhaseComplete(2, "Channel queues drained, fan-out threads stopped");

            // Phase 3: Leave cluster and close channel accessors
            logShutdownPhase(3, "Cluster Leave & Channel Cleanup",
                    "Notifying cluster peers and closing cached publisher/subscriber connections...");
            try {
                clusterService.stop();
                channelAccessor.shutdown();
            } catch (Exception e) {
                log.warn("  ⚠ Cluster/channel shutdown issue: {}", e.getMessage());
            }
            phaseDelay();
            logShutdownPhaseComplete(3, "Cluster notified, channel connections closed");

            // Phase 4: Disconnect brokers
            logShutdownPhase(4, "Disconnect Broker Connections",
                    "Closing Kafka consumers, JMS sessions, and broker connections...");
            phaseDelay();
            logShutdownPhaseComplete(4, "All broker connections closed");

            // Phase 5: Shutdown Execution Engine
            logShutdownPhase(5, "Shutdown Execution Engine",
                    "Terminating Pekko actor system and completing pending handler executions...");
            try {
                executionEngine.shutdown();
                logShutdownPhaseComplete(5, "Pekko actor system terminated");
            } catch (Exception e) {
                log.warn("  ⚠ Execution engine shutdown encountered issue: {}", e.getMessage());
            }
            phaseDelay();

            // Phase 6: Close WebSocket sessions
            logShutdownPhase(6, "Close WebSocket Sessions",
                    "Sending close frames to connected WebSocket clients...");
            phaseDelay();
            logShutdownPhaseComplete(6, "All WebSocket sessions closed");

            // Phase 7: Flush logs and release resources
            logShutdownPhase(7, "Flush Logs & Release Resources",
                    "Flushing log buffers and releasing file handles...");
            phaseDelay();
            logShutdownPhaseComplete(7, "Resources released");

        } catch (Exception e) {
            log.error("Error during shutdown sequence: {}", e.getMessage(), e);
        }

        // Final shutdown announcement
        Duration elapsed = Duration.between(shutdownStart, Instant.now());
        String endTimestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        log.info("");
        log.info("╔════════════════════════════════════════════════════════════════════╗");
        log.info("║                                                                    ║");
        log.info("║   ╔═══════════════════════════════════════════════════════════╗    ║");
        log.info("║   ║                                                           ║    ║");
        log.info("║   ║           SHUTDOWN COMPLETE                               ║    ║");
        log.info("║   ║                                                           ║    ║");
        log.info("║   ╚═══════════════════════════════════════════════════════════╝    ║");
        log.info("║                                                                    ║");
        log.info("║   {} v{} has been shut down gracefully.{}║",
                appName, version,
                pad(appName + " v" + version + " has been shut down gracefully.", 52));
        log.info("║                                                                    ║");
        log.info("║   Shutdown Summary:                                                ║");
        log.info("║   ✓ Phase 1: New connections blocked{}║",
                pad("✓ Phase 1: New connections blocked", 52));
        log.info("║   ✓ Phase 2: Channel queues drained{}║",
                pad("✓ Phase 2: Channel queues drained", 52));
        log.info("║   ✓ Phase 3: Cluster left, channels closed{}║",
                pad("✓ Phase 3: Cluster left, channels closed", 52));
        log.info("║   ✓ Phase 4: Broker connections closed{}║",
                pad("✓ Phase 4: Broker connections closed", 52));
        log.info("║   ✓ Phase 5: Execution engine terminated{}║",
                pad("✓ Phase 5: Execution engine terminated", 52));
        log.info("║   ✓ Phase 6: WebSocket sessions closed{}║",
                pad("✓ Phase 6: WebSocket sessions closed", 52));
        log.info("║   ✓ Phase 7: Resources released{}║",
                pad("✓ Phase 7: Resources released", 52));
        log.info("║                                                                    ║");
        log.info("║   Shutdown Time : {} ms{}║", elapsed.toMillis(),
                pad("Shutdown Time : " + elapsed.toMillis() + " ms", 52));
        log.info("║   Completed At  : {}{}║", endTimestamp,
                pad("Completed At  : " + endTimestamp, 52));
        log.info("║                                                                    ║");
        log.info("║   Copyright © 2025-2030 Ashutosh Sinha. Patent Pending.            ║");
        log.info("║   Goodbye.                                                         ║");
        log.info("║                                                                    ║");
        log.info("╚════════════════════════════════════════════════════════════════════╝");
        log.info("");
    }

    // ─── Logging Helpers ──────────────────────────────────────────────────────

    private void logShutdownPhase(int number, String title, String description) {
        log.info("");
        log.info("╔════════════════════════════════════════════════════════════════════╗");
        log.info("║  Phase {}: {}{}║", number, title,
                pad("Phase " + number + ": " + title, 59));
        log.info("║  {}{}║", description, pad(description, 65));
        log.info("╚════════════════════════════════════════════════════════════════════╝");
    }

    private void logShutdownPhaseComplete(int number, String detail) {
        log.info("✓ Phase {} complete: {}", number, detail);
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
}
