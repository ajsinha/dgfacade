/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential. Patent Pending.
 */
package com.dgfacade.server.service;

import com.dgfacade.common.util.JsonUtil;
import com.dgfacade.common.util.ConfigPropertyResolver;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service that manages broker configuration JSON files in the brokers config directory.
 *
 * <p>Each broker is a single JSON file named {@code <broker-id>.json} containing
 * free-form JSON with type, connection, ssl, and properties sections.
 * This service provides CRUD operations and simulated start/stop state management.</p>
 */
public class BrokerService {

    private static final Logger log = LoggerFactory.getLogger(BrokerService.class);

    private final String brokersDir;
    private ConfigPropertyResolver propertyResolver;
    private final Map<String, String> brokerStates = new ConcurrentHashMap<>(); // brokerId -> STOPPED|RUNNING|ERROR

    public BrokerService(String brokersDir) {
        this.brokersDir = brokersDir;
        ensureDir();
        // Initialize all existing brokers as STOPPED
        for (String id : listBrokerIds()) {
            brokerStates.put(id, "STOPPED");
        }
    }

    public void setPropertyResolver(ConfigPropertyResolver resolver) { this.propertyResolver = resolver; }

    /** List all broker IDs (filenames without .json extension). */
    public List<String> listBrokerIds() {
        File dir = new File(brokersDir);
        if (!dir.exists() || !dir.isDirectory()) return Collections.emptyList();
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) return Collections.emptyList();
        List<String> ids = new ArrayList<>();
        for (File f : files) {
            ids.add(f.getName().replace(".json", ""));
        }
        Collections.sort(ids);
        return ids;
    }

    /** Load a broker config as a raw Map. Returns null if not found. */
    public Map<String, Object> getBroker(String brokerId) {
        File file = brokerFile(brokerId);
        if (!file.exists()) return null;
        try {
            Map<String, Object> config = JsonUtil.fromFile(file, new TypeReference<Map<String, Object>>() {});
            if (propertyResolver != null) { try { propertyResolver.resolveMap(config); } catch (Exception e) { log.error("Property resolution failed for broker {}: {}", brokerId, e.getMessage()); } }
            config.put("_broker_id", brokerId);
            config.put("_state", brokerStates.getOrDefault(brokerId, "STOPPED"));
            return config;
        } catch (IOException e) {
            log.error("Failed to load broker {}", brokerId, e);
            return null;
        }
    }

    /** Load all broker configs. */
    public List<Map<String, Object>> getAllBrokers() {
        List<Map<String, Object>> brokers = new ArrayList<>();
        for (String id : listBrokerIds()) {
            Map<String, Object> b = getBroker(id);
            if (b != null) brokers.add(b);
        }
        return brokers;
    }

    /** Save a broker config. The brokerId determines the filename. */
    public void saveBroker(String brokerId, Map<String, Object> config) throws IOException {
        ensureDir();
        // Remove internal fields before saving
        Map<String, Object> clean = new LinkedHashMap<>(config);
        clean.remove("_broker_id");
        clean.remove("_state");
        JsonUtil.toFile(brokerFile(brokerId), clean);
        brokerStates.putIfAbsent(brokerId, "STOPPED");
        log.info("Saved broker config: {}", brokerId);
    }

    /** Delete a broker config file. */
    public boolean deleteBroker(String brokerId) {
        File file = brokerFile(brokerId);
        if (file.exists()) {
            boolean deleted = file.delete();
            if (deleted) {
                brokerStates.remove(brokerId);
                log.info("Deleted broker config: {}", brokerId);
            }
            return deleted;
        }
        return false;
    }

    /** Simulated start: mark broker as RUNNING. */
    public void startBroker(String brokerId) {
        Map<String, Object> config = getBroker(brokerId);
        if (config != null) {
            brokerStates.put(brokerId, "RUNNING");
            log.info("Broker started: {}", brokerId);
        }
    }

    /** Simulated stop: mark broker as STOPPED. */
    public void stopBroker(String brokerId) {
        brokerStates.put(brokerId, "STOPPED");
        log.info("Broker stopped: {}", brokerId);
    }

    /** Get the runtime state (STOPPED/RUNNING/ERROR) of a broker. */
    public String getBrokerState(String brokerId) {
        return brokerStates.getOrDefault(brokerId, "STOPPED");
    }

    private File brokerFile(String brokerId) {
        return new File(brokersDir, brokerId + ".json");
    }

    private void ensureDir() {
        File dir = new File(brokersDir);
        if (!dir.exists()) dir.mkdirs();
    }
}
