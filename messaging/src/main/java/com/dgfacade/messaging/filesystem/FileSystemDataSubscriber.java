/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential. Patent Pending.
 */
package com.dgfacade.messaging.filesystem;

import com.dgfacade.messaging.core.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * FileSystem-based DataSubscriber. Polls configured directories at scheduled intervals.
 * Messages are read from files in: baseDir/topicName/*.json.
 * Processed files are moved to a "processed" subdirectory.
 * Schedule-driven by default with configurable polling interval.
 */
public class FileSystemDataSubscriber extends AbstractSubscriber {

    private Path baseDir;
    private ScheduledExecutorService pollScheduler;
    private int pollIntervalSeconds = 30;

    @Override
    protected void doConnect() {
        String dir = (String) config.getOrDefault("base_dir", "./data/subscribe");
        pollIntervalSeconds = (int) config.getOrDefault("poll_interval_seconds", 30);
        baseDir = Paths.get(dir);
        try {
            Files.createDirectories(baseDir);
            state = ConnectionState.CONNECTED;
            log.info("FileSystem subscriber initialized at {}", baseDir.toAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to create subscribe directory", e);
            state = ConnectionState.ERROR;
        }
    }

    @Override
    public void start() {
        super.start();
        pollScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "fs-poll");
            t.setDaemon(true);
            return t;
        });
        pollScheduler.scheduleAtFixedRate(this::pollDirectories,
                0, pollIntervalSeconds, TimeUnit.SECONDS);
    }

    private void pollDirectories() {
        if (paused || internalQueue.size() >= backpressureMaxDepth) return;
        for (String topic : listeners.keySet()) {
            Path topicDir = baseDir.resolve(topic);
            if (!Files.exists(topicDir)) continue;
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(topicDir, "*.json")) {
                for (Path file : stream) {
                    if (internalQueue.size() >= backpressureMaxDepth) break;
                    String content = Files.readString(file);
                    MessageEnvelope envelope = new MessageEnvelope(topic, content);
                    envelope.setMessageId(file.getFileName().toString().replace(".json", ""));
                    enqueue(envelope);
                    // Move to processed
                    Path processedDir = topicDir.resolve("processed");
                    Files.createDirectories(processedDir);
                    Files.move(file, processedDir.resolve(file.getFileName()),
                              StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                log.error("Error polling directory for topic {}", topic, e);
            }
        }
    }

    @Override
    protected void doSubscribe(String topicOrQueue) {
        try {
            Files.createDirectories(baseDir.resolve(topicOrQueue));
        } catch (IOException e) {
            log.error("Failed to create topic directory: {}", topicOrQueue, e);
        }
    }

    @Override
    protected void doUnsubscribe(String topicOrQueue) { /* directory remains */ }

    @Override
    protected void doDisconnect() {
        if (pollScheduler != null) pollScheduler.shutdownNow();
    }

    @Override
    public boolean isConnected() { return state == ConnectionState.CONNECTED; }
}
