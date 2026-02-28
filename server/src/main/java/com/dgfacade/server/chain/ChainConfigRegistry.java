/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential.
 */
package com.dgfacade.server.chain;

import com.dgfacade.common.model.ChainConfig;
import com.dgfacade.common.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads and caches chain pipeline configurations from JSON files.
 * Reads from: config/chains/*.json
 *
 * Each JSON file contains a single chain definition with chain_id, steps, and config.
 */
public class ChainConfigRegistry {

    private static final Logger log = LoggerFactory.getLogger(ChainConfigRegistry.class);

    private final String configDir;
    private final Map<String, ChainConfig> chains = new ConcurrentHashMap<>();

    public ChainConfigRegistry(String configDir) {
        this.configDir = configDir;
        reload();
    }

    /** Find a chain by its chain_id. */
    public Optional<ChainConfig> findChain(String chainId) {
        ChainConfig config = chains.get(chainId);
        if (config != null && config.isEnabled()) return Optional.of(config);
        return Optional.empty();
    }

    /** Get all registered chain IDs. */
    public Set<String> getAllChainIds() {
        return Collections.unmodifiableSet(chains.keySet());
    }

    /** Get all chain configs. */
    public Map<String, ChainConfig> getAllChains() {
        return Collections.unmodifiableMap(chains);
    }

    /** Reload all chain configurations from disk. */
    public void reload() {
        log.info("Loading chain configurations from: {}", configDir);
        File dir = new File(configDir);
        if (!dir.exists()) {
            dir.mkdirs();
            log.warn("Chain config directory created: {}", configDir);
            return;
        }
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) return;

        chains.clear();
        for (File file : files) {
            try {
                ChainConfig config = JsonUtil.fromFile(file, ChainConfig.class);
                if (config.getChainId() == null || config.getChainId().isBlank()) {
                    config.setChainId(file.getName().replace(".json", "").toUpperCase());
                }
                chains.put(config.getChainId(), config);
                log.info("Loaded chain: {} ({} steps) from {}", config.getChainId(),
                        config.getSteps() != null ? config.getSteps().size() : 0, file.getName());
            } catch (IOException e) {
                log.error("Failed to load chain config: {}", file.getName(), e);
            }
        }
        log.info("Total chains loaded: {}", chains.size());
    }
}
