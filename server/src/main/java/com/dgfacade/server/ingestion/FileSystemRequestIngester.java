/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential. Patent Pending.
 */
package com.dgfacade.server.ingestion;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

/**
 * FileSystem-based request ingester. Reads connection settings (base_dir) from
 * the resolved broker config and directory name from the input channel destinations.
 * Polls the directory for JSON files, processes them in chronological order of
 * modification time (oldest first), one file = one request.
 *
 * <p>Resolution chain:</p>
 * <pre>
 *   ingester config → input_channel (destinations: directories) → broker (base_dir, file_pattern, poll settings)
 * </pre>
 *
 * <p>After processing, files are moved to a {@code processed/} subdirectory.
 * Malformed files are moved to an {@code error/} subdirectory.</p>
 */
public class FileSystemRequestIngester extends AbstractRequestIngester {

    private ScheduledExecutorService scheduler;
    private Path watchDir;
    private Path processedDir;
    private Path errorDir;
    private String filePattern;
    private int pollIntervalSeconds;
    private int maxFilesPerPoll;

    @Override
    public IngesterType getType() { return IngesterType.FILESYSTEM; }

    @SuppressWarnings("unchecked")
    @Override
    public void initialize(String id, Map<String, Object> config) {
        super.initialize(id, config);

        // Base dir from broker connection (flattened), destination name from channel
        String baseDir = (String) config.getOrDefault("base_dir", "./data/ingest");
        String destName = "requests"; // default

        Object destsObj = config.get("destinations");
        if (destsObj instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            if (first instanceof Map<?, ?> map) {
                String name = (String) map.get("name");
                if (name != null && !name.isBlank()) destName = name;
            }
        }

        this.watchDir = Paths.get(baseDir, destName);
        this.processedDir = watchDir.resolve("processed");
        this.errorDir = watchDir.resolve("error");
        this.filePattern = (String) config.getOrDefault("file_pattern", "*.json");
        this.pollIntervalSeconds = ((Number) config.getOrDefault("poll_interval_seconds", 10)).intValue();
        this.maxFilesPerPoll = ((Number) config.getOrDefault("max_files_per_poll", 100)).intValue();
    }

    @Override
    public void start() {
        if (running) return;

        log.info("[{}] ── FileSystem Ingester Starting ──", id);
        log.info("[{}]   watchDir    = {}", id, watchDir.toAbsolutePath());
        log.info("[{}]   filePattern = {}", id, filePattern);
        log.info("[{}]   pollInterval= {}s", id, pollIntervalSeconds);
        log.info("[{}]   maxPerPoll  = {}", id, maxFilesPerPoll);

        // Create directories
        try {
            Files.createDirectories(watchDir);
            Files.createDirectories(processedDir);
            Files.createDirectories(errorDir);
            log.info("[{}] Directories ready: watch={}, processed={}, error={}",
                    id, watchDir.toAbsolutePath(), processedDir.toAbsolutePath(), errorDir.toAbsolutePath());
        } catch (IOException e) {
            log.error("[{}] ✗ FAILED to create ingestion directories: {}", id, e.getMessage());
            return;
        }

        running = true;
        startedAt = Instant.now();

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ingester-fs-" + id);
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::pollDirectory,
                1, pollIntervalSeconds, TimeUnit.SECONDS);

        log.info("[{}] ── FileSystem Ingester RUNNING — polling every {}s ──", id, pollIntervalSeconds);
    }

    private void pollDirectory() {
        try {
            // List matching files sorted by modification time (oldest first = chronological)
            List<Path> files;
            try (Stream<Path> stream = Files.list(watchDir)) {
                PathMatcher matcher = watchDir.getFileSystem().getPathMatcher("glob:" + filePattern);
                files = stream
                        .filter(Files::isRegularFile)
                        .filter(p -> matcher.matches(p.getFileName()))
                        .sorted((a, b) -> {
                            try {
                                return Files.getLastModifiedTime(a).compareTo(Files.getLastModifiedTime(b));
                            } catch (IOException e) { return 0; }
                        })
                        .limit(maxFilesPerPoll)
                        .toList();
            }

            if (files.isEmpty()) return;

            log.info("[{}] ◀ POLL found {} file(s) matching '{}' in {}",
                    id, files.size(), filePattern, watchDir.toAbsolutePath());

            for (Path file : files) {
                processFile(file);
            }

        } catch (IOException e) {
            log.error("[{}] ✗ Error polling directory {}: {}", id, watchDir, e.getMessage());
        }
    }

    private void processFile(Path file) {
        String fileName = file.getFileName().toString();
        try {
            String json = Files.readString(file);
            long fileSize = Files.size(file);
            log.info("[{}] ◀ FILE picked up — name={}, size={}B", id, fileName, fileSize);

            if (json.isBlank()) {
                moveToError(file, "Empty file");
                requestsRejected.incrementAndGet();
                log.warn("[{}] ✗ REJECTED empty file: {}", id, fileName);
                return;
            }

            processMessage(json, "file:" + fileName);

            // Move to processed directory
            Path target = processedDir.resolve(fileName);
            // Append timestamp if file already exists
            if (Files.exists(target)) {
                String baseName = fileName.contains(".")
                        ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
                String ext = fileName.contains(".")
                        ? fileName.substring(fileName.lastIndexOf('.')) : "";
                target = processedDir.resolve(baseName + "_" +
                        System.currentTimeMillis() + ext);
            }
            Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
            log.info("[{}] ✓ Processed and archived: {} → processed/{}", id, fileName, target.getFileName());

        } catch (IOException e) {
            requestsFailed.incrementAndGet();
            log.error("[{}] ✗ Error reading file {}: {}", id, fileName, e.getMessage());
            moveToError(file, e.getMessage());
        }
    }

    private void moveToError(Path file, String reason) {
        try {
            Path target = errorDir.resolve(file.getFileName());
            Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
            log.warn("[{}] Moved to error dir: {} ({})", id, file.getFileName(), reason);
        } catch (IOException e) {
            log.error("[{}] Failed to move {} to error dir: {}", id, file.getFileName(), e.getMessage());
        }
    }

    @Override
    public void stop() {
        running = false;
        if (scheduler != null) scheduler.shutdownNow();
        log.info("[{}] FileSystem ingester stopped — received={}, submitted={}, failed={}, rejected={}",
                id, requestsReceived.get(), requestsSubmitted.get(),
                requestsFailed.get(), requestsRejected.get());
    }

    @Override
    protected String getSourceDescription() {
        return "file://" + watchDir.toAbsolutePath() + " [" + filePattern + "]";
    }
}
