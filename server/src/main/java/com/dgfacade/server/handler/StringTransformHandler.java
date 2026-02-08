/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential. Patent Pending.
 */
package com.dgfacade.server.handler;

import java.time.Instant;
import java.util.*;

/**
 * Sample POJO handler that does NOT implement DGHandler.
 * Demonstrates the dynamic proxy capability — DGFacade wraps this class
 * in a {@link DGHandlerProxy} at runtime using Java reflection.
 *
 * <p>Convention: the proxy discovers {@code process(Map)} as the execute method,
 * {@code init(Map)} as the construct method, and {@code close()} as cleanup.</p>
 *
 * <p>This handler performs a simple string transformation on the input payload:</p>
 * <ul>
 *   <li><b>operation=UPPER</b> — converts "text" to uppercase</li>
 *   <li><b>operation=LOWER</b> — converts "text" to lowercase</li>
 *   <li><b>operation=REVERSE</b> — reverses "text"</li>
 *   <li><b>operation=WORD_COUNT</b> — counts words in "text"</li>
 *   <li><b>operation=REPEAT</b> — repeats "text" N times (from "count")</li>
 * </ul>
 */
public class StringTransformHandler {

    private Map<String, Object> config;
    private volatile boolean cancelled = false;

    /**
     * Lifecycle: called by proxy's construct() → discovered as "init(Map)".
     */
    public void init(Map<String, Object> config) {
        this.config = config != null ? config : Map.of();
    }

    /**
     * Main processing: called by proxy's execute() → discovered as "process(Map)".
     * Accepts the request payload map and returns a result map.
     */
    public Map<String, Object> process(Map<String, Object> payload) {
        if (cancelled) {
            return Map.of("error", "Handler was cancelled");
        }

        String operation = String.valueOf(payload.getOrDefault("operation", "UPPER")).toUpperCase();
        String text = String.valueOf(payload.getOrDefault("text", ""));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("operation", operation);
        result.put("original_text", text);
        result.put("timestamp", Instant.now().toString());

        switch (operation) {
            case "UPPER" -> result.put("result", text.toUpperCase());
            case "LOWER" -> result.put("result", text.toLowerCase());
            case "REVERSE" -> result.put("result", new StringBuilder(text).reverse().toString());
            case "WORD_COUNT" -> {
                int count = text.isBlank() ? 0 : text.trim().split("\\s+").length;
                result.put("result", count);
            }
            case "REPEAT" -> {
                int count = payload.containsKey("count") ?
                        ((Number) payload.get("count")).intValue() : 3;
                result.put("result", text.repeat(Math.min(count, 100)));
                result.put("repeat_count", count);
            }
            case "TITLE_CASE" -> {
                String[] words = text.split("\\s+");
                StringBuilder sb = new StringBuilder();
                for (String w : words) {
                    if (!w.isEmpty()) {
                        sb.append(Character.toUpperCase(w.charAt(0)));
                        if (w.length() > 1) sb.append(w.substring(1).toLowerCase());
                        sb.append(' ');
                    }
                }
                result.put("result", sb.toString().trim());
            }
            case "CHAR_COUNT" -> {
                result.put("result", text.length());
                result.put("without_spaces", text.replace(" ", "").length());
            }
            default -> result.put("error", "Unknown operation: " + operation +
                    ". Supported: UPPER, LOWER, REVERSE, WORD_COUNT, REPEAT, TITLE_CASE, CHAR_COUNT");
        }

        // Include config metadata if present
        if (config != null && config.containsKey("metadata")) {
            result.put("handler_metadata", config.get("metadata"));
        }

        return result;
    }

    /**
     * Lifecycle: called by proxy's stop() → discovered as "cancel()".
     */
    public void cancel() {
        cancelled = true;
    }

    /**
     * Lifecycle: called by proxy's cleanup() → discovered as "close()".
     */
    public void close() {
        config = null;
    }
}
