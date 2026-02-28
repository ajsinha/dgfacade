/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential.
 */
package com.dgfacade.server.handler;

import com.dgfacade.common.model.DGRequest;
import com.dgfacade.common.model.DGResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <b>ChainHandler</b> — Declarative pipeline composition engine for DGFacade.
 *
 * <p>Executes a sequence of handler steps defined in JSON configuration, piping
 * each step's output into the next step's input. Supports three execution modes:</p>
 * <ul>
 *   <li><b>Phase 1 — Linear Chains:</b> Sequential step execution with payload mapping and merge strategies</li>
 *   <li><b>Phase 2 — Conditional Steps:</b> "when" expressions to skip steps based on accumulated context</li>
 *   <li><b>Phase 3 — Parallel Fan-Out:</b> Concurrent step execution with join strategies</li>
 * </ul>
 *
 * <h3>Variable Resolution</h3>
 * <ul>
 *   <li>${payload} — original request payload</li>
 *   <li>${payload.field} — specific field from original payload</li>
 *   <li>${prev} — previous step's full output</li>
 *   <li>${prev.field} — specific field from previous step</li>
 *   <li>${steps.alias} — named step's full output by alias</li>
 *   <li>${steps.alias.field} — specific field from named step</li>
 * </ul>
 */
public class ChainHandler implements DGHandler {

    private static final Logger log = LoggerFactory.getLogger(ChainHandler.class);
    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    private Map<String, Object> config;
    private volatile boolean stopped = false;

    @Override
    public void construct(Map<String, Object> config) {
        this.config = config != null ? config : Map.of();
    }

    @Override
    @SuppressWarnings("unchecked")
    public DGResponse execute(DGRequest request) {
        if (stopped) return DGResponse.error(request.getRequestId(), "Chain was stopped");
        Instant chainStart = Instant.now();

        String chainId = str(config, "chain_id", "UNNAMED_CHAIN");
        String errorStrategy = str(config, "error_strategy", "ABORT");
        List<Map<String, Object>> steps = (List<Map<String, Object>>) config.getOrDefault("steps", List.of());

        if (steps.isEmpty()) {
            return DGResponse.error(request.getRequestId(), "Chain '" + chainId + "' has no steps defined");
        }

        log.info("Chain [{}] starting with {} steps, error_strategy={}", chainId, steps.size(), errorStrategy);

        Map<String, Object> originalPayload = request.getPayload() != null ? request.getPayload() : Map.of();
        Map<String, Map<String, Object>> stepOutputs = new LinkedHashMap<>();
        Map<String, Object> prevOutput = new LinkedHashMap<>(originalPayload);
        List<Map<String, Object>> chainTrace = new ArrayList<>();

        for (Map<String, Object> stepDef : steps) {
            if (stopped) break;

            // ── Phase 3: Parallel fan-out ────────────────────────────────
            if (stepDef.containsKey("parallel")) {
                Map<String, Object> parallelResult = executeParallelStep(
                        stepDef, request, originalPayload, prevOutput, stepOutputs, chainTrace);
                if (parallelResult.containsKey("_error") && "ABORT".equalsIgnoreCase(errorStrategy)) {
                    return buildErrorResponse(request, chainId, chainTrace, chainStart,
                            "Parallel step failed: " + parallelResult.get("_error"));
                }
                String joinStrategy = str(stepDef, "join_strategy", "KEYED");
                prevOutput = applyParallelJoin(prevOutput, parallelResult, joinStrategy);
                continue;
            }

            // ── Phase 2: Conditional "when" check ────────────────────────
            String whenExpr = str(stepDef, "when", null);
            if (whenExpr != null) {
                boolean conditionMet = evaluateCondition(whenExpr, originalPayload, prevOutput, stepOutputs);
                if (!conditionMet) {
                    String alias = str(stepDef, "alias", "step_" + stepDef.get("step"));
                    Map<String, Object> skipTrace = new LinkedHashMap<>();
                    skipTrace.put("step", stepDef.get("step"));
                    skipTrace.put("alias", alias);
                    skipTrace.put("handler", stepDef.get("handler"));
                    skipTrace.put("status", "SKIPPED");
                    skipTrace.put("reason", "Condition not met: " + whenExpr);
                    skipTrace.put("duration_ms", 0);
                    chainTrace.add(skipTrace);
                    log.info("Chain [{}] step {} '{}' SKIPPED (when: {})", chainId, stepDef.get("step"), alias, whenExpr);
                    continue;
                }
            }

            // ── Phase 1: Linear step execution ───────────────────────────
            Map<String, Object> stepResult = executeStep(
                    stepDef, request, originalPayload, prevOutput, stepOutputs);

            String alias = str(stepDef, "alias", "step_" + stepDef.get("step"));
            Map<String, Object> trace = new LinkedHashMap<>();
            trace.put("step", stepDef.get("step"));
            trace.put("alias", alias);
            trace.put("handler", stepDef.get("handler"));

            if (stepResult.containsKey("_error")) {
                trace.put("status", "FAILED");
                trace.put("error", stepResult.get("_error"));
                trace.put("duration_ms", stepResult.getOrDefault("_duration_ms", 0));
                chainTrace.add(trace);

                if ("ABORT".equalsIgnoreCase(errorStrategy)) {
                    return buildErrorResponse(request, chainId, chainTrace, chainStart,
                            "Step " + stepDef.get("step") + " '" + alias + "' failed: " + stepResult.get("_error"));
                } else if ("FALLBACK".equalsIgnoreCase(errorStrategy)) {
                    Map<String, Object> fallback = getFallbackValue(stepDef);
                    stepOutputs.put(alias, fallback);
                    prevOutput = applyMergeStrategy(prevOutput, fallback, str(stepDef, "merge_strategy", "REPLACE"), alias);
                }
                // SKIP strategy: continue with prevOutput unchanged
            } else {
                trace.put("status", "SUCCESS");
                trace.put("duration_ms", stepResult.getOrDefault("_duration_ms", 0));
                chainTrace.add(trace);

                Map<String, Object> cleanResult = new LinkedHashMap<>(stepResult);
                cleanResult.remove("_duration_ms");

                stepOutputs.put(alias, cleanResult);
                prevOutput = applyMergeStrategy(prevOutput, cleanResult, str(stepDef, "merge_strategy", "REPLACE"), alias);
            }
            log.info("Chain [{}] step {} '{}' completed: {}", chainId, stepDef.get("step"), alias, trace.get("status"));
        }

        long chainDurationMs = Duration.between(chainStart, Instant.now()).toMillis();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("chain_id", chainId);
        result.put("data", prevOutput);
        result.put("chain_trace", chainTrace);
        result.put("total_steps", steps.size());
        result.put("executed_steps", chainTrace.stream().filter(t -> "SUCCESS".equals(t.get("status"))).count());
        result.put("skipped_steps", chainTrace.stream().filter(t -> "SKIPPED".equals(t.get("status"))).count());
        result.put("failed_steps", chainTrace.stream().filter(t -> "FAILED".equals(t.get("status"))).count());
        result.put("chain_duration_ms", chainDurationMs);
        result.put("stopped", stopped);
        return DGResponse.success(request.getRequestId(), result);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  PHASE 1: Linear Step Execution
    // ═══════════════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private Map<String, Object> executeStep(Map<String, Object> stepDef, DGRequest request,
            Map<String, Object> originalPayload, Map<String, Object> prevOutput,
            Map<String, Map<String, Object>> stepOutputs) {
        String handlerType = str(stepDef, "handler", null);
        if (handlerType == null) return Map.of("_error", "Missing 'handler' in step");

        Instant start = Instant.now();
        try {
            Map<String, Object> mappedPayload;
            Object payloadMapping = stepDef.get("payload_mapping");
            if (payloadMapping instanceof Map) {
                mappedPayload = resolveVariables((Map<String, Object>) payloadMapping, originalPayload, prevOutput, stepOutputs);
            } else {
                mappedPayload = new LinkedHashMap<>(prevOutput);
            }

            DGRequest stepRequest = new DGRequest(handlerType, request.getApiKey(), mappedPayload);
            stepRequest.setRequestId(request.getRequestId());
            stepRequest.setResolvedUserId(request.getResolvedUserId());
            stepRequest.setSourceChannel("chain");

            DGHandler handler = createHandler(handlerType);
            if (handler == null) return Map.of("_error", "Handler not found: " + handlerType, "_duration_ms", 0L);

            handler.construct(getStepHandlerConfig(stepDef));
            DGResponse response = handler.execute(stepRequest);
            handler.cleanup();

            long durationMs = Duration.between(start, Instant.now()).toMillis();
            if (response.getStatus() == DGResponse.Status.SUCCESS || response.getStatus() == DGResponse.Status.PARTIAL) {
                Map<String, Object> result = new LinkedHashMap<>(response.getData() != null ? response.getData() : Map.of());
                result.put("_duration_ms", durationMs);
                return result;
            } else {
                return Map.of("_error", response.getErrorMessage() != null ? response.getErrorMessage() : "Unknown error", "_duration_ms", durationMs);
            }
        } catch (Exception e) {
            long durationMs = Duration.between(start, Instant.now()).toMillis();
            log.error("Chain step error: {}", e.getMessage(), e);
            return Map.of("_error", e.getMessage(), "_duration_ms", durationMs);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  PHASE 2: Conditional Evaluation
    // ═══════════════════════════════════════════════════════════════════

    private boolean evaluateCondition(String expr, Map<String, Object> payload,
            Map<String, Object> prev, Map<String, Map<String, Object>> stepOutputs) {
        try {
            Matcher m = Pattern.compile("\\$\\{([^}]+)}\\s*(==|!=|>|<|>=|<=|contains|exists)\\s*(.*)").matcher(expr.trim());
            if (m.matches()) {
                String ref = m.group(1).trim();
                String op = m.group(2).trim();
                String valueStr = m.group(3).trim();
                Object resolved = resolveReference(ref, payload, prev, stepOutputs);

                if ("exists".equals(op)) return resolved != null;
                if (op.equals("!=") && "null".equals(valueStr)) return resolved != null;
                if (op.equals("==") && "null".equals(valueStr)) return resolved == null;
                if (resolved == null) return false;
                if ("contains".equals(op)) return resolved.toString().contains(valueStr.replaceAll("^\"|\"$", ""));

                if (resolved instanceof Number && isNumeric(valueStr)) {
                    double left = ((Number) resolved).doubleValue();
                    double right = Double.parseDouble(valueStr);
                    return switch (op) {
                        case ">" -> left > right; case "<" -> left < right;
                        case ">=" -> left >= right; case "<=" -> left <= right;
                        case "==" -> left == right; case "!=" -> left != right;
                        default -> false;
                    };
                }
                String clean = valueStr.replaceAll("^\"|\"$", "");
                return switch (op) {
                    case "==" -> resolved.toString().equals(clean);
                    case "!=" -> !resolved.toString().equals(clean);
                    default -> false;
                };
            }
            Matcher simpleM = VAR_PATTERN.matcher(expr.trim());
            if (simpleM.matches()) {
                Object resolved = resolveReference(simpleM.group(1), payload, prev, stepOutputs);
                return resolved != null && !"false".equalsIgnoreCase(resolved.toString()) && !"0".equals(resolved.toString());
            }
            return true;
        } catch (Exception e) {
            log.warn("Condition eval error for '{}': {}", expr, e.getMessage());
            return false;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  PHASE 3: Parallel Execution
    // ═══════════════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private Map<String, Object> executeParallelStep(Map<String, Object> stepDef, DGRequest request,
            Map<String, Object> originalPayload, Map<String, Object> prevOutput,
            Map<String, Map<String, Object>> stepOutputs, List<Map<String, Object>> chainTrace) {

        List<Map<String, Object>> branches = (List<Map<String, Object>>) stepDef.get("parallel");
        if (branches == null || branches.isEmpty()) return Map.of("_error", "Empty parallel branches");

        int stepNum = intVal(stepDef, "step", 0);
        log.info("Chain parallel step {} with {} branches", stepNum, branches.size());

        ExecutorService executor = Executors.newFixedThreadPool(Math.min(branches.size(), 8));
        Map<String, Future<Map<String, Object>>> futures = new LinkedHashMap<>();

        for (int i = 0; i < branches.size(); i++) {
            Map<String, Object> branch = branches.get(i);
            String branchAlias = str(branch, "alias", "branch_" + i);
            futures.put(branchAlias, executor.submit(() ->
                    executeStep(branch, request, originalPayload, prevOutput, stepOutputs)));
        }

        Map<String, Object> mergedResult = new LinkedHashMap<>();
        String joinStrategy = str(stepDef, "join_strategy", "KEYED");

        for (Map.Entry<String, Future<Map<String, Object>>> entry : futures.entrySet()) {
            String branchAlias = entry.getKey();
            try {
                Map<String, Object> branchResult = entry.getValue().get(60, TimeUnit.SECONDS);
                Map<String, Object> trace = new LinkedHashMap<>();
                trace.put("step", stepNum); trace.put("alias", branchAlias); trace.put("parallel", true);
                trace.put("handler", futures.keySet().stream().toList().indexOf(branchAlias) < branches.size()
                        ? branches.get(futures.keySet().stream().toList().indexOf(branchAlias)).get("handler") : "unknown");

                if (branchResult.containsKey("_error")) {
                    trace.put("status", "FAILED"); trace.put("error", branchResult.get("_error"));
                    mergedResult.put("_error", branchResult.get("_error"));
                } else {
                    trace.put("status", "SUCCESS");
                    Map<String, Object> clean = new LinkedHashMap<>(branchResult);
                    clean.remove("_duration_ms");
                    if ("KEYED".equalsIgnoreCase(joinStrategy)) mergedResult.put(branchAlias, clean);
                    else if ("MERGE_ALL".equalsIgnoreCase(joinStrategy)) mergedResult.putAll(clean);
                    else if ("FIRST_SUCCESS".equalsIgnoreCase(joinStrategy) && !mergedResult.containsKey("_first_done")) {
                        mergedResult.putAll(clean); mergedResult.put("_first_done", true);
                    }
                    stepOutputs.put(branchAlias, clean);
                }
                trace.put("duration_ms", branchResult.getOrDefault("_duration_ms", 0));
                chainTrace.add(trace);
            } catch (Exception e) {
                chainTrace.add(new LinkedHashMap<>(Map.of("step", stepNum, "alias", branchAlias, "parallel", true, "status", "FAILED", "error", e.getMessage())));
            }
        }
        executor.shutdownNow();
        mergedResult.remove("_first_done");
        return mergedResult;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Variable Resolution & Merge Strategies
    // ═══════════════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveVariables(Map<String, Object> template,
            Map<String, Object> payload, Map<String, Object> prev,
            Map<String, Map<String, Object>> stepOutputs) {
        Map<String, Object> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : template.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String s) resolved.put(entry.getKey(), resolveStringValue(s, payload, prev, stepOutputs));
            else if (value instanceof Map) resolved.put(entry.getKey(), resolveVariables((Map<String, Object>) value, payload, prev, stepOutputs));
            else if (value instanceof List) {
                List<Object> list = new ArrayList<>();
                for (Object item : (List<?>) value) {
                    if (item instanceof String s) list.add(resolveStringValue(s, payload, prev, stepOutputs));
                    else if (item instanceof Map) list.add(resolveVariables((Map<String, Object>) item, payload, prev, stepOutputs));
                    else list.add(item);
                }
                resolved.put(entry.getKey(), list);
            } else resolved.put(entry.getKey(), value);
        }
        return resolved;
    }

    private Object resolveStringValue(String value, Map<String, Object> payload,
            Map<String, Object> prev, Map<String, Map<String, Object>> stepOutputs) {
        Matcher full = Pattern.compile("^\\$\\{([^}]+)}$").matcher(value.trim());
        if (full.matches()) {
            Object resolved = resolveReference(full.group(1), payload, prev, stepOutputs);
            return resolved != null ? resolved : value;
        }
        Matcher m = VAR_PATTERN.matcher(value);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            Object resolved = resolveReference(m.group(1), payload, prev, stepOutputs);
            m.appendReplacement(sb, Matcher.quoteReplacement(resolved != null ? resolved.toString() : m.group(0)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private Object resolveReference(String ref, Map<String, Object> payload,
            Map<String, Object> prev, Map<String, Map<String, Object>> stepOutputs) {
        String[] parts = ref.split("\\.", 2);
        String root = parts[0]; String field = parts.length > 1 ? parts[1] : null;

        if ("steps".equals(root) && field != null) {
            String[] sp = field.split("\\.", 2);
            Map<String, Object> sd = stepOutputs.get(sp[0]);
            if (sd == null) return null;
            return sp.length == 1 ? sd : navigatePath(sd, sp[1]);
        }

        Map<String, Object> source = switch (root) {
            case "payload" -> payload;
            case "prev" -> prev;
            default -> stepOutputs.get(root);
        };
        if (source == null) return null;
        return field == null ? source : navigatePath(source, field);
    }

    @SuppressWarnings("unchecked")
    private Object navigatePath(Map<String, Object> map, String path) {
        Object current = map;
        for (String part : path.split("\\.")) {
            if (current instanceof Map) current = ((Map<String, Object>) current).get(part);
            else return null;
        }
        return current;
    }

    private Map<String, Object> applyMergeStrategy(Map<String, Object> prev,
            Map<String, Object> stepOutput, String strategy, String alias) {
        return switch (strategy.toUpperCase()) {
            case "MERGE_PREV" -> { Map<String, Object> m = new LinkedHashMap<>(prev); m.putAll(stepOutput); yield m; }
            case "APPEND" -> { Map<String, Object> m = new LinkedHashMap<>(prev); m.put(alias, stepOutput); yield m; }
            case "PASSTHROUGH" -> new LinkedHashMap<>(prev);
            default -> new LinkedHashMap<>(stepOutput); // REPLACE
        };
    }

    private Map<String, Object> applyParallelJoin(Map<String, Object> prev,
            Map<String, Object> parallelResult, String joinStrategy) {
        Map<String, Object> clean = new LinkedHashMap<>(parallelResult);
        clean.remove("_error");
        return switch (joinStrategy.toUpperCase()) {
            case "MERGE_ALL" -> { Map<String, Object> m = new LinkedHashMap<>(prev); m.putAll(clean); yield m; }
            case "FIRST_SUCCESS" -> clean.isEmpty() ? prev : new LinkedHashMap<>(clean);
            default -> { Map<String, Object> m = new LinkedHashMap<>(prev); m.put("parallel_results", clean); yield m; } // KEYED
        };
    }

    private DGHandler createHandler(String handlerType) {
        Map<String, String> handlers = Map.ofEntries(
                Map.entry("ECHO", "com.dgfacade.server.handler.EchoHandler"),
                Map.entry("ARITHMETIC", "com.dgfacade.server.handler.ArithmeticHandler"),
                Map.entry("STRING_TRANSFORM", "com.dgfacade.server.handler.StringTransformHandler"),
                Map.entry("HASH", "com.dgfacade.server.handler.HashHandler"),
                Map.entry("JSON_TRANSFORM", "com.dgfacade.server.handler.JsonTransformHandler"),
                Map.entry("SYSTEM_INFO", "com.dgfacade.server.handler.SystemInfoHandler"),
                Map.entry("HTTP_PROBE", "com.dgfacade.server.handler.HttpProbeHandler"),
                Map.entry("DELAYED", "com.dgfacade.server.handler.DelayedHandler"),
                Map.entry("WS_DEMO", "com.dgfacade.server.handler.WebSocketDemoHandler"));
        String cn = handlers.get(handlerType.toUpperCase());
        if (cn == null) { log.warn("Unknown handler in chain: {}", handlerType); return null; }
        try { return (DGHandler) Class.forName(cn).getDeclaredConstructor().newInstance(); }
        catch (Exception e) { log.error("Handler instantiation failed {}: {}", handlerType, e.getMessage()); return null; }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getStepHandlerConfig(Map<String, Object> s) {
        Object c = s.get("handler_config"); return c instanceof Map ? (Map<String, Object>) c : Map.of();
    }
    @SuppressWarnings("unchecked")
    private Map<String, Object> getFallbackValue(Map<String, Object> s) {
        Object f = s.get("fallback"); return f instanceof Map ? (Map<String, Object>) f : Map.of("_fallback", true);
    }
    private DGResponse buildErrorResponse(DGRequest req, String chainId, List<Map<String, Object>> trace, Instant start, String err) {
        return DGResponse.error(req.getRequestId(), err);
    }
    private boolean isNumeric(String s) { try { Double.parseDouble(s.trim()); return true; } catch (Exception e) { return false; } }

    @Override public void stop() { stopped = true; }
    @Override public void cleanup() { config = null; }
    private static String str(Map<String, Object> m, String k, String d) { Object v = m.get(k); return v != null ? v.toString() : d; }
    private static int intVal(Map<String, Object> m, String k, int d) { Object v = m.get(k); if (v instanceof Number n) return n.intValue(); if (v instanceof String s) { try { return Integer.parseInt(s); } catch (Exception e) { return d; } } return d; }
}
