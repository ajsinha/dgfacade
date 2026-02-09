/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential. Patent Pending.
 */
package com.dgfacade.server.handler;

import com.dgfacade.common.model.DGRequest;
import com.dgfacade.common.model.DGResponse;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Handler for JSON document manipulation.
 *
 * <p>Operates on the {@code data} field within the request payload and supports
 * a variety of transformations useful for ETL pipelines, data normalization,
 * and API response shaping.</p>
 *
 * <p>Supported operations via {@code payload.operation}:</p>
 * <ul>
 *   <li><b>FLATTEN</b> — flatten nested maps into dot-notation keys</li>
 *   <li><b>UNFLATTEN</b> — expand dot-notation keys into nested maps</li>
 *   <li><b>MERGE</b> — deep-merge {@code data} and {@code merge_with}</li>
 *   <li><b>PICK</b> — select only the specified {@code keys} from data</li>
 *   <li><b>OMIT</b> — remove the specified {@code keys} from data</li>
 *   <li><b>RENAME_KEYS</b> — rename keys using a {@code mapping} dictionary</li>
 *   <li><b>KEYS</b> — return all top-level keys</li>
 *   <li><b>VALUES</b> — return all top-level values</li>
 *   <li><b>SIZE</b> — count top-level entries</li>
 *   <li><b>SORT_KEYS</b> — return data with keys in alphabetical order</li>
 * </ul>
 */
public class JsonTransformHandler implements DGHandler {

    private volatile boolean stopped = false;

    @Override
    public void construct(Map<String, Object> config) { /* no config needed */ }

    @Override
    @SuppressWarnings("unchecked")
    public DGResponse execute(DGRequest request) {
        if (stopped) return DGResponse.error(request.getRequestId(), "Handler was stopped");
        try {
            Map<String, Object> payload = request.getPayload();
            if (payload == null) return DGResponse.error(request.getRequestId(), "Payload is required");

            String operation = String.valueOf(payload.getOrDefault("operation", "FLATTEN")).toUpperCase();
            // Accept both 'data' and 'input' field names
            Map<String, Object> data = (Map<String, Object>) payload.get("data");
            if (data == null) data = (Map<String, Object>) payload.get("input");
            if (data == null && !Set.of("SIZE", "KEYS", "VALUES").contains(operation)) {
                return DGResponse.error(request.getRequestId(), "'data' (or 'input') field is required in payload");
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("operation", operation);

            switch (operation) {
                case "FLATTEN" -> {
                    Map<String, Object> flat = new LinkedHashMap<>();
                    flatten("", data, flat);
                    result.put("result", flat);
                    result.put("original_keys", data.size());
                    result.put("flattened_keys", flat.size());
                }
                case "UNFLATTEN" -> {
                    Map<String, Object> nested = unflatten(data);
                    result.put("result", nested);
                }
                case "MERGE" -> {
                    Map<String, Object> mergeWith = (Map<String, Object>) payload.get("merge_with");
                    if (mergeWith == null) return DGResponse.error(request.getRequestId(), "'merge_with' required for MERGE");
                    Map<String, Object> merged = deepMerge(new LinkedHashMap<>(data), mergeWith);
                    result.put("result", merged);
                }
                case "PICK" -> {
                    List<String> keys = (List<String>) payload.get("keys");
                    if (keys == null) return DGResponse.error(request.getRequestId(), "'keys' list required for PICK");
                    Map<String, Object> picked = new LinkedHashMap<>();
                    for (String k : keys) {
                        if (data.containsKey(k)) picked.put(k, data.get(k));
                    }
                    result.put("result", picked);
                    result.put("picked_count", picked.size());
                }
                case "OMIT" -> {
                    List<String> keys = (List<String>) payload.get("keys");
                    if (keys == null) return DGResponse.error(request.getRequestId(), "'keys' list required for OMIT");
                    Map<String, Object> remaining = new LinkedHashMap<>(data);
                    keys.forEach(remaining::remove);
                    result.put("result", remaining);
                    result.put("removed_count", data.size() - remaining.size());
                }
                case "RENAME_KEYS" -> {
                    Map<String, String> mapping = (Map<String, String>) payload.get("mapping");
                    if (mapping == null) return DGResponse.error(request.getRequestId(), "'mapping' dict required for RENAME_KEYS");
                    Map<String, Object> renamed = new LinkedHashMap<>();
                    for (Map.Entry<String, Object> entry : data.entrySet()) {
                        String newKey = mapping.getOrDefault(entry.getKey(), entry.getKey());
                        renamed.put(newKey, entry.getValue());
                    }
                    result.put("result", renamed);
                    result.put("renamed_count", mapping.size());
                }
                case "KEYS" -> {
                    result.put("result", new ArrayList<>(data.keySet()));
                    result.put("count", data.size());
                }
                case "VALUES" -> {
                    result.put("result", new ArrayList<>(data.values()));
                    result.put("count", data.size());
                }
                case "SIZE" -> {
                    result.put("result", data != null ? data.size() : 0);
                }
                case "SORT_KEYS" -> {
                    Map<String, Object> sorted = new TreeMap<>(data);
                    result.put("result", sorted);
                }
                default -> {
                    return DGResponse.error(request.getRequestId(),
                            "Unknown operation: " + operation +
                            ". Supported: FLATTEN, UNFLATTEN, MERGE, PICK, OMIT, RENAME_KEYS, KEYS, VALUES, SIZE, SORT_KEYS");
                }
            }

            return DGResponse.success(request.getRequestId(), result);
        } catch (ClassCastException e) {
            return DGResponse.error(request.getRequestId(), "Type error — check payload structure: " + e.getMessage());
        } catch (Exception e) {
            return DGResponse.error(request.getRequestId(), e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void flatten(String prefix, Map<String, Object> map, Map<String, Object> out) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            if (entry.getValue() instanceof Map) {
                flatten(key, (Map<String, Object>) entry.getValue(), out);
            } else {
                out.put(key, entry.getValue());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unflatten(Map<String, Object> flat) {
        Map<String, Object> nested = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : flat.entrySet()) {
            String[] parts = entry.getKey().split("\\.");
            Map<String, Object> current = nested;
            for (int i = 0; i < parts.length - 1; i++) {
                current = (Map<String, Object>) current.computeIfAbsent(parts[i], k -> new LinkedHashMap<>());
            }
            current.put(parts[parts.length - 1], entry.getValue());
        }
        return nested;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deepMerge(Map<String, Object> base, Map<String, Object> overlay) {
        for (Map.Entry<String, Object> entry : overlay.entrySet()) {
            if (base.containsKey(entry.getKey()) &&
                base.get(entry.getKey()) instanceof Map && entry.getValue() instanceof Map) {
                deepMerge((Map<String, Object>) base.get(entry.getKey()),
                          (Map<String, Object>) entry.getValue());
            } else {
                base.put(entry.getKey(), entry.getValue());
            }
        }
        return base;
    }

    @Override
    public void stop() { stopped = true; }

    @Override
    public void cleanup() {}
}
