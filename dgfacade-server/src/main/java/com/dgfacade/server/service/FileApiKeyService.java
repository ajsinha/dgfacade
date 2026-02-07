/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, modification, or use is strictly prohibited without
 * explicit written permission from the copyright holder.
 * Patent Pending.
 */
package com.dgfacade.server.service;

import com.dgfacade.common.config.DGFacadeProperties;
import com.dgfacade.common.model.ApiKey;
import com.dgfacade.common.security.ApiKeyService;
import com.dgfacade.common.util.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FileApiKeyService implements ApiKeyService {

    private static final Logger log = LoggerFactory.getLogger(FileApiKeyService.class);
    private final DGFacadeProperties properties;
    private final Map<String, ApiKey> apiKeys = new ConcurrentHashMap<>();

    public FileApiKeyService(DGFacadeProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        reload();
    }

    @Override
    public boolean validate(String apiKey, String requestType) {
        if (apiKey == null || apiKey.isBlank()) return false;
        ApiKey key = apiKeys.get(apiKey);
        if (key == null || !key.isActive()) return false;
        List<String> allowed = key.getAllowedRequestTypes();
        if (allowed == null || allowed.isEmpty() || allowed.contains("*")) return true;
        return allowed.stream().anyMatch(t -> t.equalsIgnoreCase(requestType));
    }

    @Override
    public Optional<ApiKey> getApiKey(String key) {
        return Optional.ofNullable(apiKeys.get(key));
    }

    @Override
    public List<ApiKey> getAllApiKeys() {
        return new ArrayList<>(apiKeys.values());
    }

    @Override
    public void reload() {
        try {
            Path path = Paths.get(properties.getSecurity().getApiKeysFile());
            if (Files.exists(path)) {
                String json = Files.readString(path);
                List<ApiKey> keys = JsonUtils.fromJson(json, new TypeReference<List<ApiKey>>() {});
                apiKeys.clear();
                keys.forEach(k -> apiKeys.put(k.getKey(), k));
                log.info("Loaded {} API keys from {}", apiKeys.size(), path);
            } else {
                log.warn("API keys file not found: {}", path);
            }
        } catch (Exception e) {
            log.error("Failed to load API keys", e);
        }
    }
}
