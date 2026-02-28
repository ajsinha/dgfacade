/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential.
 */
package com.dgfacade.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Periodically reloads all mutable configuration from disk so that any
 * changes to handlers, brokers, channels, or ingesters become active
 * within a bounded window (default: 5 minutes).
 *
 * <h3>Design</h3>
 * <ul>
 *   <li>A single daemon thread runs every {@code intervalSeconds} (default 300 = 5 min).</li>
 *   <li>Each cycle compares file last-modified timestamps against a cache.
 *       Only directories that have <em>actually changed</em> trigger a reload,
 *       keeping the hot path nearly zero-cost.</li>
 *   <li>Registrants add their config directory + reload callback via
 *       {@link #register(String, String, Runnable)}.</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * Constructed in AppConfig, then each service registers itself:
 * <pre>
 *   autoReload.register("handlers", "config/handlers", handlerRegistry::reload);
 *   autoReload.register("brokers",  "config/brokers",  brokerService::reload);
 * </pre>
 */
public class ConfigAutoReloadService {

    private static final Logger log = LoggerFactory.getLogger(ConfigAutoReloadService.class);

    /** Default reload check interval: 5 minutes. */
    private static final int DEFAULT_INTERVAL_SECONDS = 300;

    private final int intervalSeconds;
    private final List<WatchEntry> entries = new CopyOnWriteArrayList<>();
    private ScheduledExecutorService scheduler;

    // ─── State: last-known modification fingerprint per directory ────
    private final Map<String, Long> lastKnownFingerprints = new ConcurrentHashMap<>();

    public ConfigAutoReloadService() {
        this(DEFAULT_INTERVAL_SECONDS);
    }

    public ConfigAutoReloadService(int intervalSeconds) {
        this.intervalSeconds = Math.max(30, intervalSeconds);
    }

    /**
     * Register a config directory for periodic reload.
     *
     * @param label   human-readable label for logging (e.g. "handlers")
     * @param dirPath path to the config directory
     * @param reload  callback to invoke when a change is detected
     */
    public void register(String label, String dirPath, Runnable reload) {
        entries.add(new WatchEntry(label, dirPath, reload));
        // Seed the fingerprint so the first cycle doesn't false-trigger
        lastKnownFingerprints.put(dirPath, computeFingerprint(dirPath));
        log.info("ConfigAutoReload: registered '{}' → {}", label, dirPath);
    }

    /**
     * Start the background reload scheduler.
     * Safe to call multiple times — subsequent calls are no-ops.
     */
    public void start() {
        if (scheduler != null && !scheduler.isShutdown()) return;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "config-auto-reload");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(
                this::checkAndReload,
                intervalSeconds,   // initial delay — skip the first cycle (just booted)
                intervalSeconds,
                TimeUnit.SECONDS);
        log.info("ConfigAutoReload: started — checking every {}s ({} min)",
                intervalSeconds, intervalSeconds / 60);
    }

    /** Stop the scheduler. Called on application shutdown. */
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            log.info("ConfigAutoReload: stopped");
        }
    }

    /** Force an immediate reload of all registered directories (ignores fingerprints). */
    public void forceReloadAll() {
        log.info("ConfigAutoReload: forced reload of all {} registered configs", entries.size());
        for (WatchEntry entry : entries) {
            safeReload(entry);
            lastKnownFingerprints.put(entry.dirPath, computeFingerprint(entry.dirPath));
        }
    }

    // ─── Internal ────────────────────────────────────────────────────

    private void checkAndReload() {
        for (WatchEntry entry : entries) {
            try {
                long currentFp = computeFingerprint(entry.dirPath);
                Long previousFp = lastKnownFingerprints.get(entry.dirPath);
                if (previousFp == null || currentFp != previousFp) {
                    log.info("ConfigAutoReload: change detected in '{}' ({}), reloading…",
                            entry.label, entry.dirPath);
                    safeReload(entry);
                    lastKnownFingerprints.put(entry.dirPath, currentFp);
                }
            } catch (Exception e) {
                log.error("ConfigAutoReload: error checking '{}': {}", entry.label, e.getMessage());
            }
        }
    }

    private void safeReload(WatchEntry entry) {
        try {
            entry.reload.run();
            log.info("ConfigAutoReload: '{}' reloaded successfully", entry.label);
        } catch (Exception e) {
            log.error("ConfigAutoReload: failed to reload '{}': {}", entry.label, e.getMessage(), e);
        }
    }

    /**
     * Compute a cheap fingerprint for a directory based on file count,
     * names, sizes, and last-modified times. Any file change will alter
     * the fingerprint.
     */
    private long computeFingerprint(String dirPath) {
        File dir = new File(dirPath);
        if (!dir.exists() || !dir.isDirectory()) return 0L;
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null || files.length == 0) return 0L;

        long fp = files.length;
        for (File f : files) {
            fp = 31 * fp + f.getName().hashCode();
            fp = 31 * fp + f.length();
            fp = 31 * fp + f.lastModified();
        }
        return fp;
    }

    // ─── Entry record ────────────────────────────────────────────────

    private record WatchEntry(String label, String dirPath, Runnable reload) {}
}
