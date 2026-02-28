/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential.
 */
package com.dgfacade.web.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Live Log Viewer — Enhancement 6.
 * Provides a Server-Sent Events (SSE) stream of log file tail output,
 * and a monitoring page with severity filtering and search.
 */
@Controller
public class LogStreamController {

    private static final Logger log = LoggerFactory.getLogger(LogStreamController.class);
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    @Value("${logging.file.name:logs/dgfacade.log}")
    private String mainLogFile;

    @Value("${dgfacade.handler-log.file:logs/handler-executions.log}")
    private String handlerLogFile;

    @Value("${dgfacade.app-name:DGFacade}")
    private String appName;

    @Value("${dgfacade.version:1.6.1}")
    private String version;

    /** GET /monitoring/logs — Live log viewer page. */
    @GetMapping("/monitoring/logs")
    public String logsPage(Model model) {
        model.addAttribute("appName", appName);
        model.addAttribute("version", version);
        return "pages/monitoring/logs";
    }

    /** GET /api/v1/logs/tail — Get last N lines of the main log file. */
    @GetMapping("/api/v1/logs/tail")
    @ResponseBody
    public Map<String, Object> tailLog(
            @RequestParam(defaultValue = "200") int lines,
            @RequestParam(defaultValue = "main") String source) {

        String filePath = "handler".equals(source) ? handlerLogFile : mainLogFile;
        List<String> tailLines = tailFile(filePath, lines);

        return Map.of(
                "source", source,
                "file", filePath,
                "lines", tailLines,
                "count", tailLines.size()
        );
    }

    /** GET /api/v1/logs/stream — SSE stream of new log lines. */
    @GetMapping(value = "/api/v1/logs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    public SseEmitter streamLog(@RequestParam(defaultValue = "main") String source) {
        SseEmitter emitter = new SseEmitter(300_000L); // 5 min timeout
        String filePath = "handler".equals(source) ? handlerLogFile : mainLogFile;

        Path path = Path.of(filePath);
        if (!Files.exists(path)) {
            try {
                emitter.send(SseEmitter.event().name("error").data("Log file not found: " + filePath));
                emitter.complete();
            } catch (IOException e) { /* ignore */ }
            return emitter;
        }

        // Track file position and poll for new content
        final long[] lastPos = { fileSize(path) };
        final ScheduledFuture<?>[] holder = new ScheduledFuture<?>[1];
        holder[0] = scheduler.scheduleAtFixedRate(() -> {
            try {
                long currentSize = fileSize(path);
                if (currentSize > lastPos[0]) {
                    List<String> newLines = readNewLines(path, lastPos[0]);
                    lastPos[0] = currentSize;
                    for (String line : newLines) {
                        emitter.send(SseEmitter.event().name("log").data(line));
                    }
                } else if (currentSize < lastPos[0]) {
                    // File was rotated
                    lastPos[0] = 0;
                }
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        }, 500, 500, TimeUnit.MILLISECONDS);

        emitter.onCompletion(() -> holder[0].cancel(true));
        emitter.onTimeout(() -> holder[0].cancel(true));
        emitter.onError(t -> holder[0].cancel(true));

        return emitter;
    }

    private List<String> tailFile(String filePath, int lines) {
        Path path = Path.of(filePath);
        if (!Files.exists(path)) return List.of("[Log file not found: " + filePath + "]");
        try {
            List<String> allLines = Files.readAllLines(path);
            int start = Math.max(0, allLines.size() - lines);
            return allLines.subList(start, allLines.size());
        } catch (IOException e) {
            return List.of("[Error reading log: " + e.getMessage() + "]");
        }
    }

    private List<String> readNewLines(Path path, long fromPos) {
        List<String> lines = new ArrayList<>();
        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {
            raf.seek(fromPos);
            String line;
            while ((line = raf.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException e) {
            log.debug("Error reading new log lines: {}", e.getMessage());
        }
        return lines;
    }

    private long fileSize(Path path) {
        try { return Files.size(path); } catch (IOException e) { return 0; }
    }
}
