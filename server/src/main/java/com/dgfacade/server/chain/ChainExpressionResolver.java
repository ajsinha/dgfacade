/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential.
 */
package com.dgfacade.server.chain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves expressions in chain step configurations.
 *
 * <p>Supports variable references in payload_mapping values and `when` conditions:</p>
 * <ul>
 *   <li><code>${payload}</code> — the original request payload (entire map)</li>
 *   <li><code>${payload.field}</code> — a field from the original payload</li>
 *   <li><code>${prev}</code> — previous step's full output</li>
 *   <li><code>${prev.field}</code> — a field from the previous step's output</li>
 *   <li><code>${steps.alias}</code> — a named step's full output</li>
 *   <li><code>${steps.alias.field}</code> — a field from a named step's output</li>
 *   <li><code>${chain.request_id}</code> — the chain's request ID</li>
 *   <li><code>${chain.step}</code> — current step number</li>
 * </ul>
 *
 * <h3>Conditional Expressions (when)</h3>
 * <p>Simple comparisons: <code>${prev.score} > 75</code>, <code>${prev.status} == 'ACTIVE'</code>,
 * <code>${prev.count} != 0</code>, <code>${payload.priority} == 'HIGH'</code></p>
 */
public class ChainExpressionResolver {

    private static final Logger log = LoggerFactory.getLogger(ChainExpressionResolver.class);
    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)}");
    private static final Pattern COMPARE_PATTERN = Pattern.compile(
            "(.+?)\\s*(==|!=|>=|<=|>|<)\\s*(.+)");

    private final Map<String, Object> originalPayload;
    private final Map<String, Map<String, Object>> stepOutputs; // alias -> output
    private Map<String, Object> previousOutput;
    private String requestId;
    private int currentStep;

    public ChainExpressionResolver(Map<String, Object> originalPayload, String requestId) {
        this.originalPayload = originalPayload != null ? originalPayload : Map.of();
        this.stepOutputs = new LinkedHashMap<>();
        this.previousOutput = Map.of();
        this.requestId = requestId;
        this.currentStep = 0;
    }

    /** Record a step's output for later reference. */
    public void recordStepOutput(String alias, Map<String, Object> output) {
        if (alias != null && output != null) {
            stepOutputs.put(alias, output);
            previousOutput = output;
        }
    }

    public void setCurrentStep(int step) { this.currentStep = step; }
    public Map<String, Object> getPreviousOutput() { return previousOutput; }

    /**
     * Resolve all expressions in a payload mapping.
     * Returns a new map with all ${...} references replaced with actual values.
     */
    public Map<String, Object> resolvePayloadMapping(Map<String, Object> mapping) {
        if (mapping == null || mapping.isEmpty()) return new LinkedHashMap<>(originalPayload);
        Map<String, Object> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : mapping.entrySet()) {
            resolved.put(entry.getKey(), resolveValue(entry.getValue()));
        }
        return resolved;
    }

    /**
     * Evaluate a `when` condition expression.
     * Returns true if the condition is met (or if when is null/blank).
     */
    public boolean evaluateWhen(String whenExpr) {
        if (whenExpr == null || whenExpr.isBlank()) return true;

        try {
            // Resolve all variables in the expression first
            String resolved = resolveString(whenExpr);

            // Parse comparison
            Matcher cm = COMPARE_PATTERN.matcher(resolved.trim());
            if (!cm.matches()) {
                // Treat as truthy check
                return isTruthy(resolved.trim());
            }

            String left = cm.group(1).trim();
            String op = cm.group(2).trim();
            String right = cm.group(3).trim();

            // Strip quotes from string literals
            left = stripQuotes(left);
            right = stripQuotes(right);

            // Try numeric comparison first
            try {
                double lnum = Double.parseDouble(left);
                double rnum = Double.parseDouble(right);
                return switch (op) {
                    case "==" -> lnum == rnum;
                    case "!=" -> lnum != rnum;
                    case ">"  -> lnum > rnum;
                    case "<"  -> lnum < rnum;
                    case ">=" -> lnum >= rnum;
                    case "<=" -> lnum <= rnum;
                    default -> false;
                };
            } catch (NumberFormatException e) {
                // String comparison
                int cmp = left.compareTo(right);
                return switch (op) {
                    case "==" -> left.equals(right);
                    case "!=" -> !left.equals(right);
                    case ">"  -> cmp > 0;
                    case "<"  -> cmp < 0;
                    case ">=" -> cmp >= 0;
                    case "<=" -> cmp <= 0;
                    default -> false;
                };
            }
        } catch (Exception e) {
            log.warn("Failed to evaluate when expression '{}': {}", whenExpr, e.getMessage());
            return false;
        }
    }

    /**
     * Merge step output into accumulated context based on merge strategy.
     */
    public Map<String, Object> mergeOutput(Map<String, Object> accumulated,
                                            Map<String, Object> stepOutput,
                                            String alias,
                                            com.dgfacade.common.model.ChainConfig.MergeStrategy strategy) {
        if (stepOutput == null) stepOutput = Map.of();
        return switch (strategy) {
            case REPLACE -> new LinkedHashMap<>(stepOutput);
            case MERGE_PREV -> {
                Map<String, Object> merged = new LinkedHashMap<>(accumulated);
                merged.putAll(stepOutput);
                yield merged;
            }
            case APPEND -> {
                Map<String, Object> appended = new LinkedHashMap<>(accumulated);
                appended.put(alias, stepOutput);
                yield appended;
            }
            case PASSTHROUGH -> new LinkedHashMap<>(accumulated);
        };
    }

    // ── Internal Resolution ──────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Object resolveValue(Object value) {
        if (value == null) return null;
        if (value instanceof String s) {
            // If the entire value is a single expression, return the actual object (not stringified)
            Matcher m = VAR_PATTERN.matcher(s);
            if (m.matches()) {
                return resolveReference(m.group(1));
            }
            // Otherwise, do string interpolation for embedded expressions
            if (s.contains("${")) {
                return resolveString(s);
            }
            return s;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> resolved = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                resolved.put(e.getKey().toString(), resolveValue(e.getValue()));
            }
            return resolved;
        }
        if (value instanceof List<?> list) {
            List<Object> resolved = new ArrayList<>();
            for (Object item : list) resolved.add(resolveValue(item));
            return resolved;
        }
        return value;
    }

    /** Resolve ${...} expressions embedded in a string. */
    private String resolveString(String template) {
        Matcher m = VAR_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            Object resolved = resolveReference(m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(
                    resolved != null ? resolved.toString() : ""));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Resolve a single reference path like "payload.field" or "steps.alias.field". */
    @SuppressWarnings("unchecked")
    private Object resolveReference(String path) {
        String[] parts = path.split("\\.", 2);
        String root = parts[0];
        String remainder = parts.length > 1 ? parts[1] : null;

        Object source = switch (root) {
            case "payload" -> remainder == null ? originalPayload : drill(originalPayload, remainder);
            case "prev" -> remainder == null ? previousOutput : drill(previousOutput, remainder);
            case "steps" -> {
                if (remainder == null) yield stepOutputs;
                String[] stepParts = remainder.split("\\.", 2);
                Map<String, Object> stepData = stepOutputs.get(stepParts[0]);
                yield stepParts.length > 1 ? drill(stepData, stepParts[1]) : stepData;
            }
            case "chain" -> {
                if ("request_id".equals(remainder)) yield requestId;
                if ("step".equals(remainder)) yield currentStep;
                yield null;
            }
            default -> drill(originalPayload, path); // fallback: treat as payload field
        };
        return source;
    }

    /** Drill into a nested map using dot-separated path. */
    @SuppressWarnings("unchecked")
    private Object drill(Map<String, Object> map, String path) {
        if (map == null || path == null) return null;
        String[] parts = path.split("\\.");
        Object current = map;
        for (String part : parts) {
            if (current instanceof Map<?, ?> m) {
                current = m.get(part);
            } else {
                return null;
            }
        }
        return current;
    }

    private static String stripQuotes(String s) {
        if (s.length() >= 2 && ((s.startsWith("'") && s.endsWith("'")) ||
                (s.startsWith("\"") && s.endsWith("\"")))) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private static boolean isTruthy(String s) {
        if (s == null || s.isBlank() || s.equals("null") || s.equals("false") || s.equals("0")) return false;
        return true;
    }
}
