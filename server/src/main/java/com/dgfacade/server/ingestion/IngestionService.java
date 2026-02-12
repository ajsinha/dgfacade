/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential. Patent Pending.
 */
package com.dgfacade.server.ingestion;

import com.dgfacade.common.util.JsonUtil;
import com.dgfacade.server.engine.ExecutionEngine;
import com.dgfacade.server.service.BrokerService;
import com.dgfacade.server.service.InputChannelService;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all RequestIngester instances. Each ingester references an
 * <b>input channel</b> (which in turn references a <b>broker</b>).
 *
 * <p>Resolution chain: {@code ingester.json → input_channel → broker}<br>
 * The IngestionService resolves this chain at startup, merging broker connection
 * details + channel destinations + any ingester-level overrides into a single
 * configuration map passed to each ingester.</p>
 *
 * <p>Multiple ingesters of the same or different types can run concurrently.</p>
 */
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final String configDir;
    private final ExecutionEngine executionEngine;
    private final BrokerService brokerService;
    private final InputChannelService inputChannelService;
    private final Map<String, AbstractRequestIngester> ingesters = new ConcurrentHashMap<>();

    public IngestionService(String configDir, ExecutionEngine executionEngine,
                            BrokerService brokerService, InputChannelService inputChannelService) {
        this.configDir = configDir;
        this.executionEngine = executionEngine;
        this.brokerService = brokerService;
        this.inputChannelService = inputChannelService;
        ensureDir();
    }

    /**
     * Scan config directory and start all enabled ingesters.
     * Each ingester config must reference an {@code input_channel} which
     * in turn references a {@code broker}. The full resolution chain is:
     * <pre>
     *   ingester.json  →  input_channel  →  broker
     *                      (destinations)    (connection details)
     * </pre>
     * @return number of ingesters started
     */
    public int startAll() {
        int started = 0;
        List<String> ids = listIngesterIds();

        log.info("╔══════════════════════════════════════════════════════════╗");
        log.info("║  INGESTION SERVICE — STARTING ALL INGESTERS             ║");
        log.info("║  Config dir: {}  ║", configDir);
        log.info("║  Found {} ingester config(s): {}                 ║", ids.size(), ids);
        log.info("╚══════════════════════════════════════════════════════════╝");

        for (String id : ids) {
            try {
                Map<String, Object> ingesterConfig = loadConfig(id);
                if (ingesterConfig == null) {
                    log.warn("Ingester '{}': config file not found or unreadable — skipping", id);
                    continue;
                }

                boolean enabled = (boolean) ingesterConfig.getOrDefault("enabled", true);
                if (!enabled) {
                    log.info("Ingester '{}': disabled (enabled=false) — skipping", id);
                    continue;
                }

                log.info("Ingester '{}': enabled=true, input_channel={} — starting...",
                        id, ingesterConfig.get("input_channel"));
                String result = startIngester(id);
                if (result == null) {
                    started++;
                    log.info("Ingester '{}': ✓ STARTED SUCCESSFULLY", id);
                } else {
                    log.error("Ingester '{}': ✗ FAILED TO START — {}", id, result);
                }

            } catch (Exception e) {
                log.error("Ingester '{}': ✗ EXCEPTION during start — {}", id, e.getMessage(), e);
            }
        }

        if (started > 0) {
            log.info("IngestionService: {} ingester(s) started from {}", started, configDir);
        } else if (!ids.isEmpty()) {
            log.info("IngestionService: {} config(s) found but 0 ingesters started", ids.size());
        } else {
            log.info("IngestionService: no ingester configurations found in {}", configDir);
        }

        return started;
    }

    /**
     * Start a single ingester by ID. Resolves the input_channel → broker chain,
     * creates the ingester, and starts it.
     * @param id the ingester config file name (without .json)
     * @return null on success, or an error message string on failure
     */
    public String startIngester(String id) {
        // Already running?
        AbstractRequestIngester existing = ingesters.get(id);
        if (existing != null && existing.isRunning()) {
            return "Ingester '" + id + "' is already running";
        }

        Map<String, Object> ingesterConfig = loadConfig(id);
        if (ingesterConfig == null) {
            return "Ingester config '" + id + "' not found";
        }

        // ── Resolve input_channel → broker chain ──
        String channelId = (String) ingesterConfig.get("input_channel");
        if (channelId == null || channelId.isBlank()) {
            return "Ingester '" + id + "' has no 'input_channel'";
        }

        Map<String, Object> channelConfig = inputChannelService.getChannel(channelId);
        if (channelConfig == null) {
            return "Ingester '" + id + "' references unknown input_channel '" + channelId + "'";
        }

        String brokerId = (String) channelConfig.get("broker");
        Map<String, Object> brokerConfig = null;
        if (brokerId != null && !brokerId.isBlank()) {
            brokerConfig = brokerService.getBroker(brokerId);
            if (brokerConfig == null) {
                return "Channel '" + channelId + "' references unknown broker '" + brokerId + "'";
            }
        }

        // Determine type from channel (or broker)
        String typeStr = (String) channelConfig.get("type");
        if (typeStr == null && brokerConfig != null) {
            typeStr = (String) brokerConfig.get("type");
        }
        if (typeStr == null) {
            return "Cannot determine type from channel '" + channelId + "'";
        }

        // Normalize type: "jms" → "activemq" for ingester matching
        if ("jms".equalsIgnoreCase(typeStr)) typeStr = "activemq";

        RequestIngester.IngesterType type;
        try {
            type = RequestIngester.IngesterType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return "Unsupported ingester type '" + typeStr + "'";
        }

        // Build resolved config: ingester overrides + channel + broker
        Map<String, Object> resolvedConfig = buildResolvedConfig(ingesterConfig, channelConfig, brokerConfig);

        log.info("══════════════════════════════════════════════════════════");
        log.info("Ingester '{}': RESOLVED CONFIG CHAIN", id);
        log.info("  ingester config  : {}", id);
        log.info("  → input_channel  : {}", channelId);
        log.info("  → broker         : {}", brokerId != null ? brokerId : "N/A");
        log.info("  → type           : {}", typeStr);
        log.info("  Resolved keys    : {}", resolvedConfig.keySet());
        // Log key connection fields for debugging
        for (String key : List.of("bootstrap_servers", "broker_url", "base_dir",
                "host", "username", "group_id", "destinations")) {
            if (resolvedConfig.containsKey(key)) {
                Object val = resolvedConfig.get(key);
                if ("password".equals(key)) val = "****";
                log.info("  {}={}", key, val);
            }
        }
        log.info("══════════════════════════════════════════════════════════");

        AbstractRequestIngester ingester = createIngester(type);
        ingester.initialize(id, resolvedConfig);
        ingester.setExecutionEngine(executionEngine);
        ingester.start();

        ingesters.put(id, ingester);
        log.info("Ingester '{}' started successfully", id);
        return null; // success
    }

    /**
     * Stop a single ingester by ID.
     * @return null on success, or an error message string on failure
     */
    public String stopIngester(String id) {
        AbstractRequestIngester ingester = ingesters.get(id);
        if (ingester == null) {
            return "Ingester '" + id + "' is not running";
        }
        try {
            ingester.stop();
            ingesters.remove(id);
            log.info("Ingester '{}' stopped successfully", id);
            return null; // success
        } catch (Exception e) {
            log.error("Error stopping ingester '{}': {}", id, e.getMessage(), e);
            return "Error stopping ingester '" + id + "': " + e.getMessage();
        }
    }

    /**
     * Enable or disable an ingester by modifying its config file on disk.
     * Enabled ingesters auto-start at application startup; disabled ones are skipped.
     * @return null on success, or an error message string on failure
     */
    public String setIngesterEnabled(String id, boolean enabled) {
        Map<String, Object> config = loadConfig(id);
        if (config == null) {
            return "Ingester config '" + id + "' not found";
        }
        config.put("enabled", enabled);
        try {
            JsonUtil.toFile(new File(configDir, id + ".json"), config);
            log.info("Ingester '{}' {} in config", id, enabled ? "enabled" : "disabled");
            return null; // success
        } catch (IOException e) {
            log.error("Failed to update config for ingester '{}': {}", id, e.getMessage());
            return "Failed to write config: " + e.getMessage();
        }
    }

    /**
     * Merge the three config layers into a single resolved map:
     * <ol>
     *   <li>Broker connection details (base)</li>
     *   <li>Channel destinations and settings</li>
     *   <li>Ingester-level overrides</li>
     * </ol>
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> buildResolvedConfig(
            Map<String, Object> ingesterConfig,
            Map<String, Object> channelConfig,
            Map<String, Object> brokerConfig) {

        Map<String, Object> resolved = new LinkedHashMap<>();

        // 1. Copy broker connection details
        if (brokerConfig != null) {
            resolved.put("_broker_id", brokerConfig.get("_broker_id"));
            resolved.put("_broker_type", brokerConfig.get("type"));
            Object conn = brokerConfig.get("connection");
            if (conn instanceof Map<?, ?> connMap) {
                resolved.putAll((Map<String, Object>) connMap);
            }
            // Broker-level properties
            Object brokerProps = brokerConfig.get("properties");
            if (brokerProps instanceof Map<?, ?> propsMap) {
                resolved.put("properties", new LinkedHashMap<>((Map<String, Object>) propsMap));
            }
            // SSL config from broker
            if (brokerConfig.containsKey("ssl")) {
                resolved.put("ssl", brokerConfig.get("ssl"));
            }
        }

        // 2. Copy channel destinations and channel-level settings
        resolved.put("_channel_id", channelConfig.get("_channel_id"));
        if (channelConfig.containsKey("destinations")) {
            resolved.put("destinations", channelConfig.get("destinations"));
        }
        // Channel-level settings (poll interval, file pattern, etc.)
        for (String key : List.of("file_pattern", "poll_interval_seconds", "max_files_per_poll")) {
            if (channelConfig.containsKey(key)) {
                resolved.put(key, channelConfig.get(key));
            }
        }

        // 3. Copy ingester-level fields (description, enabled)
        resolved.put("description", ingesterConfig.getOrDefault("description", ""));
        resolved.put("enabled", ingesterConfig.getOrDefault("enabled", true));

        // 4. Apply ingester-level overrides (highest priority)
        Object overrides = ingesterConfig.get("overrides");
        if (overrides instanceof Map<?, ?> overridesMap) {
            for (Map.Entry<?, ?> entry : overridesMap.entrySet()) {
                resolved.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }

        return resolved;
    }

    /**
     * Stop all running ingesters.
     */
    public void stopAll() {
        for (Map.Entry<String, AbstractRequestIngester> entry : ingesters.entrySet()) {
            try {
                entry.getValue().stop();
            } catch (Exception e) {
                log.warn("Error stopping ingester '{}': {}", entry.getKey(), e.getMessage());
            }
        }
        ingesters.clear();
        log.info("IngestionService: all ingesters stopped");
    }

    /** Get all ingester stats. */
    public List<RequestIngester.IngesterStats> getAllStats() {
        List<RequestIngester.IngesterStats> stats = new ArrayList<>();
        for (AbstractRequestIngester ingester : ingesters.values()) {
            stats.add(ingester.getStats());
        }
        return stats;
    }

    /** Get a specific ingester by ID. */
    public AbstractRequestIngester getIngester(String id) {
        return ingesters.get(id);
    }

    /** Get a specific ingester's stats. */
    public RequestIngester.IngesterStats getStats(String id) {
        AbstractRequestIngester ingester = ingesters.get(id);
        return ingester != null ? ingester.getStats() : null;
    }

    /**
     * Submit a manual request through a specific ingester.
     * @return true if submitted, false if ingester not found or not running
     */
    public boolean submitManualRequest(String ingesterId, String jsonPayload) {
        AbstractRequestIngester ingester = ingesters.get(ingesterId);
        if (ingester == null) {
            log.warn("Manual submit: ingester '{}' not found", ingesterId);
            return false;
        }
        if (!ingester.isRunning()) {
            log.warn("Manual submit: ingester '{}' is not running", ingesterId);
            return false;
        }
        ingester.processMessage(jsonPayload, "manual-submit");
        return true;
    }

    /** Get configuration for all loaded ingesters (with resolution info). */
    public List<Map<String, Object>> getAllConfigs() {
        List<Map<String, Object>> configs = new ArrayList<>();
        for (String id : listIngesterIds()) {
            Map<String, Object> cfg = loadConfig(id);
            if (cfg != null) {
                cfg.put("_id", id);
                AbstractRequestIngester running = ingesters.get(id);
                cfg.put("_running", running != null && running.isRunning());
                // Resolve channel and broker names for display
                String channelId = (String) cfg.get("input_channel");
                if (channelId != null) {
                    Map<String, Object> ch = inputChannelService.getChannel(channelId);
                    if (ch != null) {
                        cfg.put("_channel_type", ch.get("type"));
                        cfg.put("_broker_id", ch.get("broker"));
                        cfg.put("_destinations", ch.get("destinations"));
                    }
                }
                // Include runtime stats if ingester is/was running
                if (running != null) {
                    RequestIngester.IngesterStats s = running.getStats();
                    cfg.put("_stats_received", s.requestsReceived());
                    cfg.put("_stats_submitted", s.requestsSubmitted());
                    cfg.put("_stats_failed", s.requestsFailed());
                    cfg.put("_stats_rejected", s.requestsRejected());
                }
                configs.add(cfg);
            }
        }
        return configs;
    }

    /** Get the number of active (running) ingesters. */
    public int getActiveCount() {
        return (int) ingesters.values().stream().filter(RequestIngester::isRunning).count();
    }

    /** Get all registered ingester IDs. */
    public Set<String> getIngesterIds() {
        return Collections.unmodifiableSet(ingesters.keySet());
    }

    /** List all config files (ingester IDs) in the config directory. */
    public List<String> listIngesterIds() {
        File dir = new File(configDir);
        if (!dir.exists() || !dir.isDirectory()) return Collections.emptyList();
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) return Collections.emptyList();
        List<String> ids = new ArrayList<>();
        for (File f : files) ids.add(f.getName().replace(".json", ""));
        Collections.sort(ids);
        return ids;
    }

    // ─── Private ────────────────────────────────────────────────────────

    private AbstractRequestIngester createIngester(RequestIngester.IngesterType type) {
        return switch (type) {
            case KAFKA -> new KafkaRequestIngester();
            case ACTIVEMQ -> new ActiveMQRequestIngester();
            case FILESYSTEM -> new FileSystemRequestIngester();
        };
    }

    /**
     * Get raw config for a single ingester by ID (for UI display).
     */
    public Map<String, Object> getConfig(String id) {
        return loadConfig(id);
    }

    private Map<String, Object> loadConfig(String id) {
        File file = new File(configDir, id + ".json");
        if (!file.exists()) return null;
        try {
            return JsonUtil.fromFile(file, new TypeReference<Map<String, Object>>() {});
        } catch (IOException e) {
            log.error("Failed to load ingester config '{}': {}", id, e.getMessage());
            return null;
        }
    }

    private void ensureDir() {
        File dir = new File(configDir);
        if (!dir.exists()) dir.mkdirs();
    }
}
