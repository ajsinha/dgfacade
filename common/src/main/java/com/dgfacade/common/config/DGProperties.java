/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential.
 */
package com.dgfacade.common.config;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Application-wide property accessor for non-Spring POJOs.
 *
 * <p>This singleton is initialized once by Spring at startup (via
 * {@code DGPropertiesInitializer}) and is then available everywhere — including
 * POJOs, handler classes, utility code, and anywhere else that Spring does not
 * manage the lifecycle.</p>
 *
 * <h3>Usage in any POJO</h3>
 * <pre>{@code
 *   // Simple typed access with defaults
 *   int port = DGProperties.get().getInt("server.port", 8090);
 *   boolean ssl = DGProperties.get().getBoolean("dgfacade.ssl.enabled", false);
 *   String dir = DGProperties.get().getString("dgfacade.config.handlers-dir", "config/handlers");
 *
 *   // Convenience shortcuts for the dgfacade.* namespace
 *   String version = DGProperties.get().app("version", "0.0.0");
 *   int ttl = DGProperties.get().getInt("dgfacade.handler.default-ttl", 30);
 *
 *   // Grab an entire namespace as a Map
 *   Map<String, String> brokerProps = DGProperties.get().getSubProperties("dgfacade.brokers.");
 * }</pre>
 *
 * <h3>Thread Safety</h3>
 * <p>The singleton is initialized exactly once before any request processing
 * begins (Spring {@code @PostConstruct} phase). After initialization the
 * property map is effectively immutable — reads are safe from any thread.
 * Runtime property additions via {@link #setProperty} are supported through
 * a {@link ConcurrentHashMap}.</p>
 */
public final class DGProperties {

    // ─── Singleton ──────────────────────────────────────────────────

    private static volatile DGProperties INSTANCE;

    private final Map<String, String> properties;

    private DGProperties(Map<String, String> properties) {
        this.properties = new ConcurrentHashMap<>(properties);
    }

    /**
     * Initialize the singleton. Called exactly once by the Spring initializer.
     *
     * @param props all resolved properties from Spring Environment
     * @throws IllegalStateException if called more than once
     */
    public static synchronized void init(Map<String, String> props) {
        if (INSTANCE != null) {
            // Allow re-init during tests or hot-reload — merge new values
            INSTANCE.properties.putAll(props);
            return;
        }
        INSTANCE = new DGProperties(props);
    }

    /**
     * Get the singleton instance.
     *
     * @return the initialized DGProperties instance
     * @throws IllegalStateException if not yet initialized (Spring hasn't started)
     */
    public static DGProperties get() {
        if (INSTANCE == null) {
            throw new IllegalStateException(
                "DGProperties not initialized — Spring context has not started yet. " +
                "Ensure DGPropertiesInitializer is on the component scan path.");
        }
        return INSTANCE;
    }

    /**
     * Check whether the singleton has been initialized.
     * Useful for guard clauses in code that may run before Spring starts.
     */
    public static boolean isInitialized() {
        return INSTANCE != null;
    }

    // ─── Core Typed Getters ─────────────────────────────────────────

    /**
     * Raw property value, or {@code null} if absent.
     */
    public String getString(String key) {
        return properties.get(key);
    }

    /**
     * Property value with a fallback default.
     */
    public String getString(String key, String defaultValue) {
        return properties.getOrDefault(key, defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        String val = properties.get(key);
        if (val == null || val.isBlank()) return defaultValue;
        try { return Integer.parseInt(val.trim()); }
        catch (NumberFormatException e) { return defaultValue; }
    }

    public long getLong(String key, long defaultValue) {
        String val = properties.get(key);
        if (val == null || val.isBlank()) return defaultValue;
        try { return Long.parseLong(val.trim()); }
        catch (NumberFormatException e) { return defaultValue; }
    }

    public double getDouble(String key, double defaultValue) {
        String val = properties.get(key);
        if (val == null || val.isBlank()) return defaultValue;
        try { return Double.parseDouble(val.trim()); }
        catch (NumberFormatException e) { return defaultValue; }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String val = properties.get(key);
        if (val == null || val.isBlank()) return defaultValue;
        return "true".equalsIgnoreCase(val.trim());
    }

    /**
     * Parse a duration string. Supports:
     * <ul>
     *   <li>Plain number → interpreted as {@code unit} (default: milliseconds)</li>
     *   <li>{@code "30s"} → 30 seconds</li>
     *   <li>{@code "5m"} → 5 minutes</li>
     *   <li>{@code "2h"} → 2 hours</li>
     *   <li>ISO-8601 ({@code "PT30S"}) via {@link Duration#parse}</li>
     * </ul>
     */
    public Duration getDuration(String key, Duration defaultValue) {
        String val = properties.get(key);
        if (val == null || val.isBlank()) return defaultValue;
        val = val.trim().toLowerCase();
        try {
            if (val.startsWith("pt")) return Duration.parse(val.toUpperCase());
            if (val.endsWith("ms")) return Duration.ofMillis(Long.parseLong(val.replace("ms", "").trim()));
            if (val.endsWith("s"))  return Duration.ofSeconds(Long.parseLong(val.replace("s", "").trim()));
            if (val.endsWith("m"))  return Duration.ofMinutes(Long.parseLong(val.replace("m", "").trim()));
            if (val.endsWith("h"))  return Duration.ofHours(Long.parseLong(val.replace("h", "").trim()));
            return Duration.ofMillis(Long.parseLong(val));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Parse a comma-separated property value into a list.
     * Trims whitespace from each element and skips blanks.
     * <p>Example: {@code "node1:8090, node2:8090"} → {@code ["node1:8090", "node2:8090"]}</p>
     */
    public List<String> getList(String key) {
        String val = properties.get(key);
        if (val == null || val.isBlank()) return Collections.emptyList();
        return Arrays.stream(val.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    public List<String> getList(String key, List<String> defaultValue) {
        List<String> result = getList(key);
        return result.isEmpty() ? defaultValue : result;
    }

    // ─── Namespace Helpers ──────────────────────────────────────────

    /**
     * Get all properties under a given prefix as a flat map with the prefix stripped.
     * <p>Example: {@code getSubProperties("dgfacade.brokers.")} returns
     * {@code {"config-dir" → "config/brokers", "connection-timeout" → "30000", ...}}</p>
     */
    public Map<String, String> getSubProperties(String prefix) {
        return properties.entrySet().stream()
                .filter(e -> e.getKey().startsWith(prefix))
                .collect(Collectors.toMap(
                        e -> e.getKey().substring(prefix.length()),
                        Map.Entry::getValue,
                        (a, b) -> b,
                        LinkedHashMap::new
                ));
    }

    /**
     * Shortcut for the {@code dgfacade.*} namespace.
     * <p>Example: {@code app("version", "0.0.0")} → reads {@code dgfacade.version}</p>
     */
    public String app(String suffix, String defaultValue) {
        return getString("dgfacade." + suffix, defaultValue);
    }

    /**
     * Shortcut: reads {@code dgfacade.<suffix>} as an int.
     */
    public int appInt(String suffix, int defaultValue) {
        return getInt("dgfacade." + suffix, defaultValue);
    }

    /**
     * Shortcut: reads {@code dgfacade.<suffix>} as a boolean.
     */
    public boolean appBoolean(String suffix, boolean defaultValue) {
        return getBoolean("dgfacade." + suffix, defaultValue);
    }

    // ─── Runtime Mutation (use sparingly) ────────────────────────────

    /**
     * Set a property at runtime. Useful for test overrides or dynamic config.
     * Thread-safe via ConcurrentHashMap.
     */
    public void setProperty(String key, String value) {
        properties.put(key, value);
    }

    // ─── Introspection ──────────────────────────────────────────────

    /**
     * Check if a property exists (even if its value is blank).
     */
    public boolean hasProperty(String key) {
        return properties.containsKey(key);
    }

    /**
     * Total number of loaded properties.
     */
    public int size() {
        return properties.size();
    }

    /**
     * Unmodifiable view of all properties. Useful for diagnostics/debugging.
     */
    public Map<String, String> getAll() {
        return Collections.unmodifiableMap(properties);
    }

    @Override
    public String toString() {
        return "DGProperties{count=" + properties.size() + "}";
    }

    // ─── Testing Support ────────────────────────────────────────────

    /**
     * Reset the singleton. <b>Only for unit tests.</b>
     */
    public static synchronized void reset() {
        INSTANCE = null;
    }
}
