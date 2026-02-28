/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential.
 */
package com.dgfacade.common.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves {@code ${key}} and {@code ${key:defaultValue}} placeholders in configuration values
 * using a waterfall resolution strategy:
 *
 * <ol>
 *   <li><strong>JVM system properties</strong> ({@code -Dkey=value})</li>
 *   <li><strong>application.properties</strong> file on classpath</li>
 *   <li><strong>Default value</strong> specified after colon: {@code ${key:defaultValue}}</li>
 *   <li>If none resolve → throws {@link ConfigResolutionException} and system should exit</li>
 * </ol>
 *
 * <p><strong>Escaping colons:</strong> Use {@code \:} in property values to include a literal colon.
 * Example: {@code ${db.url:jdbc\:postgresql\://localhost/db}} resolves the default to
 * {@code jdbc:postgresql://localhost/db}.</p>
 */
public class ConfigPropertyResolver {

    private static final Logger log = LoggerFactory.getLogger(ConfigPropertyResolver.class);
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)}");

    private final Properties appProperties;

    public ConfigPropertyResolver() {
        this.appProperties = new Properties();
    }

    public ConfigPropertyResolver(Properties properties) {
        this.appProperties = properties != null ? properties : new Properties();
    }

    /**
     * Load application.properties from the given path.
     */
    public void loadPropertiesFile(String path) {
        try (FileInputStream fis = new FileInputStream(path)) {
            appProperties.load(fis);
            log.info("Loaded {} properties from {}", appProperties.size(), path);
        } catch (IOException e) {
            log.warn("Could not load properties file {}: {}", path, e.getMessage());
        }
    }

    /**
     * Load from classpath resource.
     */
    public void loadClasspathProperties(String resource) {
        try (var is = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (is != null) {
                appProperties.load(is);
                log.info("Loaded {} properties from classpath:{}", appProperties.size(), resource);
            }
        } catch (IOException e) {
            log.warn("Could not load classpath properties {}: {}", resource, e.getMessage());
        }
    }

    /**
     * Resolve a single string value, replacing all ${...} placeholders.
     *
     * @throws ConfigResolutionException if a placeholder cannot be resolved
     */
    public String resolve(String value) {
        if (value == null || !value.contains("${")) return value;

        Matcher matcher = PLACEHOLDER.matcher(value);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String expr = matcher.group(1); // e.g. "key" or "key:default"
            String resolved = resolveExpression(expr);
            matcher.appendReplacement(result, Matcher.quoteReplacement(resolved));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Recursively resolve all string values in a map (modifies in place).
     */
    public void resolveMap(Map<String, Object> map) {
        if (map == null) return;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object val = entry.getValue();
            if (val instanceof String s) {
                entry.setValue(resolve(s));
            } else if (val instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nested = (Map<String, Object>) val;
                resolveMap(nested);
            } else if (val instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> list = (List<Object>) val;
                resolveList(list);
            }
        }
    }

    private void resolveList(List<Object> list) {
        for (int i = 0; i < list.size(); i++) {
            Object val = list.get(i);
            if (val instanceof String s) {
                list.set(i, resolve(s));
            } else if (val instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nested = (Map<String, Object>) val;
                resolveMap(nested);
            } else if (val instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> nested = (List<Object>) val;
                resolveList(nested);
            }
        }
    }

    private String resolveExpression(String expr) {
        // Split on first un-escaped colon to get key and optional default
        String key;
        String defaultValue = null;

        int colonIdx = findUnescapedColon(expr);
        if (colonIdx >= 0) {
            key = expr.substring(0, colonIdx).trim();
            defaultValue = unescape(expr.substring(colonIdx + 1));
        } else {
            key = expr.trim();
        }

        // Waterfall 1: JVM system property
        String val = System.getProperty(key);
        if (val != null) {
            log.debug("Resolved ${{{}}}} from JVM system property: {}", key, val);
            return val;
        }

        // Waterfall 2: application.properties
        val = appProperties.getProperty(key);
        if (val != null) {
            log.debug("Resolved ${{{}}}} from application.properties: {}", key, val);
            return val;
        }

        // Waterfall 3: Environment variable (bonus — common need)
        val = System.getenv(key);
        if (val != null) {
            log.debug("Resolved ${{{}}}} from environment variable: {}", key, val);
            return val;
        }

        // Waterfall 4: Default value
        if (defaultValue != null) {
            log.debug("Resolved ${{{}}}} using default: {}", key, defaultValue);
            return defaultValue;
        }

        // Nothing resolved — fatal
        String msg = "Cannot resolve configuration placeholder ${" + key + "}. " +
                "Provide it as: (1) JVM arg -D" + key + "=value, " +
                "(2) application.properties entry, " +
                "(3) environment variable, or " +
                "(4) inline default ${" + key + ":defaultValue}";
        log.error(msg);
        throw new ConfigResolutionException(msg);
    }

    /**
     * Find the first un-escaped colon in the expression.
     * Escaped colons are {@code \:}
     */
    private int findUnescapedColon(String expr) {
        for (int i = 0; i < expr.length(); i++) {
            if (expr.charAt(i) == ':' && (i == 0 || expr.charAt(i - 1) != '\\')) {
                return i;
            }
        }
        return -1;
    }

    private String unescape(String value) {
        return value.replace("\\:", ":");
    }

    /**
     * Exception thrown when a placeholder cannot be resolved through the waterfall.
     */
    public static class ConfigResolutionException extends RuntimeException {
        public ConfigResolutionException(String message) {
            super(message);
        }
    }
}
