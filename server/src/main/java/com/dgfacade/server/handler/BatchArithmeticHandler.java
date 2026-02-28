/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential.
 */
package com.dgfacade.server.handler;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * POJO handler (does NOT implement DGHandler) for batch arithmetic.
 * Wrapped automatically by {@link DGHandlerProxy} at runtime.
 *
 * <p>Demonstrates the dynamic proxy pattern with a handler that processes
 * arrays of operations in a single request — useful for bulk ETL calculations,
 * batch scoring, or financial computations.</p>
 *
 * <p>Proxy method discovery:</p>
 * <ul>
 *   <li>{@code setup(Map)} → construct</li>
 *   <li>{@code process(Map)} → execute</li>
 *   <li>{@code shutdown()} → stop</li>
 *   <li>{@code dispose()} → cleanup</li>
 * </ul>
 *
 * <p>Payload format:</p>
 * <pre>
 * {
 *   "operations": [
 *     {"op": "ADD", "a": 10, "b": 5},
 *     {"op": "MUL", "a": 3, "b": 7},
 *     {"op": "SQRT", "a": 144},
 *     {"op": "ABS", "a": -42}
 *   ]
 * }
 * </pre>
 *
 * <p>Supported ops: ADD, SUB, MUL, DIV, MOD, POW, SQRT, ABS, MIN, MAX, AVG, SUM</p>
 */
public class BatchArithmeticHandler {

    private Map<String, Object> config;
    private volatile boolean cancelled = false;
    private int precision = 10;

    /**
     * Lifecycle: proxy discovers as construct → "setup(Map)"
     */
    public void setup(Map<String, Object> config) {
        this.config = config != null ? config : Map.of();
        if (this.config.containsKey("precision")) {
            precision = ((Number) this.config.get("precision")).intValue();
        }
    }

    /**
     * Main processing: proxy discovers as execute → "process(Map)"
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> process(Map<String, Object> payload) {
        if (cancelled) {
            return Map.of("error", "Handler was cancelled");
        }

        List<Map<String, Object>> operations = (List<Map<String, Object>>) payload.get("operations");
        if (operations == null || operations.isEmpty()) {
            return Map.of("error", "'operations' list is required",
                    "example", Map.of("operations", List.of(
                            Map.of("op", "ADD", "a", 10, "b", 5),
                            Map.of("op", "SQRT", "a", 144))));
        }

        List<Map<String, Object>> results = new ArrayList<>();
        int successCount = 0;
        int errorCount = 0;

        for (int i = 0; i < operations.size(); i++) {
            if (cancelled) break;
            Map<String, Object> op = operations.get(i);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("index", i);
            try {
                String operation = String.valueOf(op.getOrDefault("op", "ADD")).toUpperCase();
                result.put("op", operation);
                double answer = compute(operation, op);
                result.put("result", answer);
                result.put("status", "OK");
                successCount++;
            } catch (Exception e) {
                result.put("error", e.getMessage());
                result.put("status", "ERROR");
                errorCount++;
            }
            results.add(result);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("results", results);
        response.put("total_operations", operations.size());
        response.put("success_count", successCount);
        response.put("error_count", errorCount);
        response.put("timestamp", Instant.now().toString());

        if (config != null && config.containsKey("metadata")) {
            response.put("handler_metadata", config.get("metadata"));
        }

        return response;
    }

    private double compute(String op, Map<String, Object> params) {
        double a = params.containsKey("a") ? ((Number) params.get("a")).doubleValue() : 0;
        double b = params.containsKey("b") ? ((Number) params.get("b")).doubleValue() : 0;

        return switch (op) {
            case "ADD" -> a + b;
            case "SUB", "SUBTRACT" -> a - b;
            case "MUL", "MULTIPLY" -> a * b;
            case "DIV", "DIVIDE" -> {
                if (b == 0) throw new ArithmeticException("Division by zero");
                yield a / b;
            }
            case "MOD" -> a % b;
            case "POW", "POWER" -> Math.pow(a, b);
            case "SQRT" -> {
                if (a < 0) throw new ArithmeticException("Square root of negative number");
                yield Math.sqrt(a);
            }
            case "ABS" -> Math.abs(a);
            case "MIN" -> Math.min(a, b);
            case "MAX" -> Math.max(a, b);
            case "AVG" -> (a + b) / 2.0;
            case "SUM" -> {
                // Sum all numeric values in params except "op"
                double sum = 0;
                for (Map.Entry<String, Object> e : params.entrySet()) {
                    if (!"op".equals(e.getKey()) && e.getValue() instanceof Number) {
                        sum += ((Number) e.getValue()).doubleValue();
                    }
                }
                yield sum;
            }
            default -> throw new IllegalArgumentException("Unknown op: " + op +
                    ". Supported: ADD, SUB, MUL, DIV, MOD, POW, SQRT, ABS, MIN, MAX, AVG, SUM");
        };
    }

    /**
     * Lifecycle: proxy discovers as stop → "shutdown()"
     */
    public void shutdown() {
        cancelled = true;
    }

    /**
     * Lifecycle: proxy discovers as cleanup → "dispose()"
     */
    public void dispose() {
        config = null;
    }
}
