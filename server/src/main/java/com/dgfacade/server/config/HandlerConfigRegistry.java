/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential. Patent Pending.
 */
package com.dgfacade.server.config;

import com.dgfacade.common.model.HandlerConfig;
import com.dgfacade.common.util.JsonUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads and caches handler configurations from JSON files.
 * Looks for handler configs in: config/handlers/<userId>.json and config/handlers/default.json.
 * Reloads automatically when files change.
 */
public class HandlerConfigRegistry {

    private static final Logger log = LoggerFactory.getLogger(HandlerConfigRegistry.class);

    private final String configDir;
    // userId -> (requestType -> HandlerConfig)
    private final Map<String, Map<String, HandlerConfig>> userConfigs = new ConcurrentHashMap<>();
    private Map<String, HandlerConfig> defaultConfigs = new ConcurrentHashMap<>();

    public HandlerConfigRegistry(String configDir) {
        this.configDir = configDir;
        reload();
    }

    /**
     * Find the handler config for a given user and request type.
     * Falls back to default.json if no user-specific config exists.
     */
    public Optional<HandlerConfig> findHandler(String userId, String requestType) {
        // Check user-specific config first
        Map<String, HandlerConfig> userMap = userConfigs.get(userId);
        if (userMap != null) {
            HandlerConfig config = userMap.get(requestType);
            if (config != null && config.isEnabled()) return Optional.of(config);
        }
        // Fallback to default
        HandlerConfig config = defaultConfigs.get(requestType);
        if (config != null && config.isEnabled()) return Optional.of(config);
        return Optional.empty();
    }

    public Map<String, HandlerConfig> getDefaultConfigs() {
        return Collections.unmodifiableMap(defaultConfigs);
    }

    public Set<String> getAllRequestTypes() {
        Set<String> types = new HashSet<>(defaultConfigs.keySet());
        userConfigs.values().forEach(m -> types.addAll(m.keySet()));
        return types;
    }

    public void reload() {
        log.info("Loading handler configurations from: {}", configDir);
        File dir = new File(configDir);
        if (!dir.exists()) {
            dir.mkdirs();
            log.warn("Config directory created: {}", configDir);
            return;
        }
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) return;

        for (File file : files) {
            try {
                Map<String, HandlerConfig> rawMap = JsonUtil.fromFile(file,
                        new TypeReference<Map<String, HandlerConfig>>() {});
                Map<String, HandlerConfig> configMap = new HashMap<>();
                for (Map.Entry<String, HandlerConfig> entry : rawMap.entrySet()) {
                    HandlerConfig hc = entry.getValue();
                    hc.setRequestType(entry.getKey());
                    configMap.put(entry.getKey(), hc);
                }
                String name = file.getName().replace(".json", "");
                if ("default".equals(name)) {
                    defaultConfigs = new ConcurrentHashMap<>(configMap);
                    log.info("Loaded {} default handler configs", configMap.size());
                } else {
                    userConfigs.put(name, configMap);
                    log.info("Loaded {} handler configs for user: {}", configMap.size(), name);
                }
            } catch (IOException e) {
                log.error("Failed to load handler config: {}", file.getName(), e);
            }
        }
    }
}
