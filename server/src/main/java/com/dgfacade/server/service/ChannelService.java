/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential. Patent Pending.
 */
package com.dgfacade.server.service;

import com.dgfacade.common.util.JsonUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service that manages channel configuration JSON files in the channels config directory.
 *
 * <p>Each channel is a single JSON file named {@code <channel-name>.json} containing
 * free-form JSON with type, broker, subscriptions, queue, and retry sections.</p>
 */
public class ChannelService {

    private static final Logger log = LoggerFactory.getLogger(ChannelService.class);

    private final String channelsDir;
    private final Map<String, String> channelStates = new ConcurrentHashMap<>();

    public ChannelService(String channelsDir) {
        this.channelsDir = channelsDir;
        ensureDir();
        for (String id : listChannelIds()) {
            channelStates.put(id, "STOPPED");
        }
    }

    public List<String> listChannelIds() {
        File dir = new File(channelsDir);
        if (!dir.exists() || !dir.isDirectory()) return Collections.emptyList();
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) return Collections.emptyList();
        List<String> ids = new ArrayList<>();
        for (File f : files) ids.add(f.getName().replace(".json", ""));
        Collections.sort(ids);
        return ids;
    }

    public Map<String, Object> getChannel(String channelId) {
        File file = channelFile(channelId);
        if (!file.exists()) return null;
        try {
            Map<String, Object> config = JsonUtil.fromFile(file, new TypeReference<Map<String, Object>>() {});
            config.put("_channel_id", channelId);
            config.put("_state", channelStates.getOrDefault(channelId, "STOPPED"));
            return config;
        } catch (IOException e) {
            log.error("Failed to load channel {}", channelId, e);
            return null;
        }
    }

    public List<Map<String, Object>> getAllChannels() {
        List<Map<String, Object>> channels = new ArrayList<>();
        for (String id : listChannelIds()) {
            Map<String, Object> c = getChannel(id);
            if (c != null) channels.add(c);
        }
        return channels;
    }

    public void saveChannel(String channelId, Map<String, Object> config) throws IOException {
        ensureDir();
        Map<String, Object> clean = new LinkedHashMap<>(config);
        clean.remove("_channel_id");
        clean.remove("_state");
        JsonUtil.toFile(channelFile(channelId), clean);
        channelStates.putIfAbsent(channelId, "STOPPED");
        log.info("Saved channel config: {}", channelId);
    }

    public boolean deleteChannel(String channelId) {
        File file = channelFile(channelId);
        if (file.exists()) {
            boolean deleted = file.delete();
            if (deleted) {
                channelStates.remove(channelId);
                log.info("Deleted channel config: {}", channelId);
            }
            return deleted;
        }
        return false;
    }

    public void startChannel(String channelId) {
        if (getChannel(channelId) != null) {
            channelStates.put(channelId, "RUNNING");
            log.info("Channel started: {}", channelId);
        }
    }

    public void stopChannel(String channelId) {
        channelStates.put(channelId, "STOPPED");
        log.info("Channel stopped: {}", channelId);
    }

    public String getChannelState(String channelId) {
        return channelStates.getOrDefault(channelId, "STOPPED");
    }

    private File channelFile(String channelId) {
        return new File(channelsDir, channelId + ".json");
    }

    private void ensureDir() {
        File dir = new File(channelsDir);
        if (!dir.exists()) dir.mkdirs();
    }
}
