/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential. Patent Pending.
 */
package com.dgfacade.server.service;

import com.dgfacade.common.model.ApiKeyInfo;
import com.dgfacade.common.model.UserInfo;
import com.dgfacade.common.util.JsonUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages user accounts and API keys from a JSON file.
 * Thread-safe with ConcurrentHashMap-based caches.
 */
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final String usersFilePath;
    private final String apiKeysFilePath;
    private Map<String, UserInfo> usersById = new ConcurrentHashMap<>();
    private Map<String, UserInfo> usersByUsername = new ConcurrentHashMap<>();
    private Map<String, ApiKeyInfo> apiKeyMap = new ConcurrentHashMap<>();

    public UserService(String usersFilePath, String apiKeysFilePath) {
        this.usersFilePath = usersFilePath;
        this.apiKeysFilePath = apiKeysFilePath;
        reload();
    }

    public void reload() {
        loadUsers();
        loadApiKeys();
    }

    private void loadUsers() {
        File file = new File(usersFilePath);
        if (!file.exists()) {
            log.warn("Users file not found: {}. Creating default.", usersFilePath);
            createDefaultUsers(file);
        }
        try {
            List<UserInfo> users = JsonUtil.fromFile(file, new TypeReference<List<UserInfo>>() {});
            usersById.clear();
            usersByUsername.clear();
            for (UserInfo u : users) {
                usersById.put(u.getUserId(), u);
                usersByUsername.put(u.getUsername(), u);
            }
            log.info("Loaded {} users from {}", users.size(), usersFilePath);
        } catch (IOException e) {
            log.error("Failed to load users", e);
        }
    }

    private void loadApiKeys() {
        File file = new File(apiKeysFilePath);
        if (!file.exists()) {
            log.warn("API keys file not found: {}. Creating default.", apiKeysFilePath);
            createDefaultApiKeys(file);
        }
        try {
            List<ApiKeyInfo> keys = JsonUtil.fromFile(file, new TypeReference<List<ApiKeyInfo>>() {});
            apiKeyMap.clear();
            for (ApiKeyInfo k : keys) apiKeyMap.put(k.getKey(), k);
            log.info("Loaded {} API keys from {}", keys.size(), apiKeysFilePath);
        } catch (IOException e) {
            log.error("Failed to load API keys", e);
        }
    }

    public String resolveUserFromApiKey(String apiKey) {
        ApiKeyInfo info = apiKeyMap.get(apiKey);
        if (info == null || !info.isEnabled()) return null;
        UserInfo user = usersById.get(info.getUserId());
        if (user == null || !user.isEnabled()) return null;
        return user.getUserId();
    }

    public Optional<UserInfo> getUserById(String userId) {
        return Optional.ofNullable(usersById.get(userId));
    }

    public Optional<UserInfo> getUserByUsername(String username) {
        return Optional.ofNullable(usersByUsername.get(username));
    }

    public List<UserInfo> getAllUsers() {
        return new ArrayList<>(usersById.values());
    }

    public List<ApiKeyInfo> getAllApiKeys() {
        return new ArrayList<>(apiKeyMap.values());
    }

    public List<ApiKeyInfo> getApiKeysForUser(String userId) {
        return apiKeyMap.values().stream()
                .filter(k -> userId.equals(k.getUserId()))
                .collect(Collectors.toList());
    }

    public void saveUsers(List<UserInfo> users) throws IOException {
        JsonUtil.toFile(new File(usersFilePath), users);
        reload();
    }

    public void saveApiKeys(List<ApiKeyInfo> keys) throws IOException {
        JsonUtil.toFile(new File(apiKeysFilePath), keys);
        reload();
    }

    private void createDefaultUsers(File file) {
        List<UserInfo> defaults = new ArrayList<>();
        UserInfo admin = new UserInfo();
        admin.setUserId("admin");
        admin.setUsername("admin");
        admin.setPassword("admin123");
        admin.setDisplayName("Administrator");
        admin.setEmail("admin@dgfacade.local");
        admin.setRoles(List.of("ADMIN", "USER"));
        admin.setEnabled(true);
        defaults.add(admin);

        UserInfo dev = new UserInfo();
        dev.setUserId("developer");
        dev.setUsername("developer");
        dev.setPassword("dev123");
        dev.setDisplayName("Developer");
        dev.setEmail("dev@dgfacade.local");
        dev.setRoles(List.of("USER"));
        dev.setEnabled(true);
        defaults.add(dev);

        try {
            file.getParentFile().mkdirs();
            JsonUtil.toFile(file, defaults);
        } catch (IOException e) {
            log.error("Failed to create default users file", e);
        }
    }

    private void createDefaultApiKeys(File file) {
        List<ApiKeyInfo> defaults = new ArrayList<>();
        ApiKeyInfo key1 = new ApiKeyInfo();
        key1.setKey("dgf-dev-key-001");
        key1.setUserId("admin");
        key1.setDescription("Default admin API key");
        defaults.add(key1);

        ApiKeyInfo key2 = new ApiKeyInfo();
        key2.setKey("dgf-dev-key-002");
        key2.setUserId("developer");
        key2.setDescription("Default developer API key");
        defaults.add(key2);

        try {
            file.getParentFile().mkdirs();
            JsonUtil.toFile(file, defaults);
        } catch (IOException e) {
            log.error("Failed to create default API keys file", e);
        }
    }
}
