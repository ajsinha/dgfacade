/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential.
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

    // ═══ CRUD for handler config files ═══

    /** List all handler config file IDs (filenames without .json). */
    public List<String> listHandlerFileIds() {
        File dir = new File(configDir);
        if (!dir.exists() || !dir.isDirectory()) return Collections.emptyList();
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json") && !name.endsWith(".new"));
        if (files == null) return Collections.emptyList();
        List<String> ids = new ArrayList<>();
        for (File f : files) ids.add(f.getName().replace(".json", ""));
        Collections.sort(ids);
        return ids;
    }

    /** Get a handler config file as a map of requestType -> HandlerConfig. */
    public Map<String, HandlerConfig> getHandlerFile(String fileId) {
        File file = new File(configDir, fileId + ".json");
        if (!file.exists()) return null;
        try {
            Map<String, HandlerConfig> rawMap = JsonUtil.fromFile(file,
                    new TypeReference<Map<String, HandlerConfig>>() {});
            for (Map.Entry<String, HandlerConfig> entry : rawMap.entrySet()) {
                entry.getValue().setRequestType(entry.getKey());
            }
            return rawMap;
        } catch (IOException e) {
            log.error("Failed to load handler file '{}': {}", fileId, e.getMessage());
            return null;
        }
    }

    /** Get all handler files with metadata. */
    public List<Map<String, Object>> getAllHandlerFiles() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (String fileId : listHandlerFileIds()) {
            Map<String, HandlerConfig> configs = getHandlerFile(fileId);
            if (configs != null) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("_file_id", fileId);
                entry.put("_handler_count", configs.size());
                entry.put("_handlers", configs);
                result.add(entry);
            }
        }
        return result;
    }

    /** Save a handler config file (map of requestType -> config). */
    public void saveHandlerFile(String fileId, Map<String, Object> rawConfig) throws IOException {
        ensureDir();
        JsonUtil.toFile(new File(configDir, fileId + ".json"), rawConfig);
        reload();
        log.info("Saved handler config file: {}", fileId);
    }

    /** Delete a handler config file. */
    public boolean deleteHandlerFile(String fileId) {
        File file = new File(configDir, fileId + ".json");
        if (file.exists()) {
            boolean deleted = file.delete();
            if (deleted) {
                if ("default".equals(fileId)) {
                    defaultConfigs = new ConcurrentHashMap<>();
                } else {
                    userConfigs.remove(fileId);
                }
                log.info("Deleted handler config file: {}", fileId);
            }
            return deleted;
        }
        return false;
    }

    private void ensureDir() {
        File dir = new File(configDir);
        if (!dir.exists()) dir.mkdirs();
    }
}
