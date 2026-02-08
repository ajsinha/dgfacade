/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential. Patent Pending.
 */
package com.dgfacade.messaging.filesystem;

import com.dgfacade.messaging.core.*;
import java.io.*;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * FileSystem-based DataPublisher. Writes messages as individual files
 * in a directory structure: baseDir/topicName/messageId.json.
 * Default mode is scheduled (batch), but can be set to event-based.
 */
public class FileSystemDataPublisher extends AbstractPublisher {

    private Path baseDir;

    @Override
    protected void doConnect() {
        String dir = (String) config.getOrDefault("base_dir", "./data/publish");
        baseDir = Paths.get(dir);
        try {
            Files.createDirectories(baseDir);
            state = ConnectionState.CONNECTED;
            log.info("FileSystem publisher initialized at {}", baseDir.toAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to create publish directory", e);
            state = ConnectionState.ERROR;
        }
    }

    @Override
    protected CompletableFuture<Void> doPublish(String topic, MessageEnvelope envelope) {
        return CompletableFuture.runAsync(() -> {
            try {
                Path topicDir = baseDir.resolve(topic);
                Files.createDirectories(topicDir);
                Path file = topicDir.resolve(envelope.getMessageId() + ".json");
                Files.writeString(file, envelope.getPayload(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            } catch (IOException e) {
                throw new RuntimeException("FileSystem publish failed", e);
            }
        });
    }

    @Override
    protected void doDisconnect() { state = ConnectionState.DISCONNECTED; }

    @Override
    public boolean isConnected() { return state == ConnectionState.CONNECTED; }
}
