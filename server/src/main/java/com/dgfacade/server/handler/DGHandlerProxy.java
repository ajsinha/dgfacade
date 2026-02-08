/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential. Patent Pending.
 */
package com.dgfacade.server.handler;

import com.dgfacade.common.model.DGRequest;
import com.dgfacade.common.model.DGResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.*;
import java.util.*;

/**
 * Dynamic proxy that wraps any POJO class to conform to the {@link DGHandler} interface.
 *
 * <p>When a handler class does NOT implement {@code DGHandler}, this proxy uses Java reflection
 * to discover and invoke methods that match a convention-based contract:</p>
 *
 * <h3>Method Discovery (in priority order):</h3>
 * <ol>
 *   <li><b>execute(DGRequest)</b> → DGResponse — exact DGHandler signature</li>
 *   <li><b>execute(Map)</b> → Map — payload-in, data-out convenience</li>
 *   <li><b>handle(DGRequest)</b> → DGResponse — alternate naming</li>
 *   <li><b>handle(Map)</b> → Map — alternate naming, payload-in/data-out</li>
 *   <li><b>process(DGRequest)</b> → DGResponse — alternate naming</li>
 *   <li><b>process(Map)</b> → Map — alternate naming, payload-in/data-out</li>
 *   <li><b>run(DGRequest)</b> → DGResponse — alternate naming</li>
 *   <li><b>run(Map)</b> → Map — alternate naming, payload-in/data-out</li>
 * </ol>
 *
 * <p>For lifecycle methods:</p>
 * <ul>
 *   <li><b>construct(Map)</b> or <b>init(Map)</b> or <b>initialize(Map)</b> → called on setup</li>
 *   <li><b>stop()</b> or <b>cancel()</b> or <b>abort()</b> → called on TTL breach</li>
 *   <li><b>cleanup()</b> or <b>close()</b> or <b>destroy()</b> → called on teardown</li>
 * </ul>
 *
 * <p>If none of the above methods are found, the proxy throws an exception at construction time
 * so the misconfiguration is detected early.</p>
 *
 * <h3>Example — A plain POJO handler (no DGHandler dependency):</h3>
 * <pre>{@code
 * public class MyCustomProcessor {
 *     private Map<String, Object> config;
 *
 *     public void init(Map<String, Object> config) { this.config = config; }
 *
 *     public Map<String, Object> process(Map<String, Object> payload) {
 *         String name = (String) payload.getOrDefault("name", "World");
 *         return Map.of("greeting", "Hello, " + name + "!");
 *     }
 *
 *     public void close() { config = null; }
 * }
 * }</pre>
 */
public class DGHandlerProxy implements DGHandler {

    private static final Logger log = LoggerFactory.getLogger(DGHandlerProxy.class);

    private final Object delegate;
    private final Method executeMethod;
    private final boolean executeAcceptsDGRequest;  // true if param is DGRequest, false if Map
    private final boolean executeReturnsDGResponse; // true if returns DGResponse, false if Map/Object
    private final Method constructMethod;  // nullable
    private final Method stopMethod;       // nullable
    private final Method cleanupMethod;    // nullable
    private volatile boolean stopped = false;

    /**
     * Wrap the given POJO instance as a DGHandler.
     *
     * @param delegate the POJO instance to proxy
     * @throws IllegalArgumentException if no suitable execute method is found
     */
    public DGHandlerProxy(Object delegate) {
        this.delegate = Objects.requireNonNull(delegate, "Delegate handler instance must not be null");
        Class<?> clazz = delegate.getClass();

        // ── Discover execute method ──
        MethodMatch match = findExecuteMethod(clazz);
        if (match == null) {
            throw new IllegalArgumentException(
                    "Handler class " + clazz.getName() + " does not implement DGHandler and has no " +
                    "discoverable execute/handle/process/run method. Expected one of: " +
                    "execute(DGRequest), execute(Map), handle(DGRequest), handle(Map), " +
                    "process(DGRequest), process(Map), run(DGRequest), run(Map)");
        }
        this.executeMethod = match.method;
        this.executeAcceptsDGRequest = match.acceptsDGRequest;
        this.executeReturnsDGResponse = match.returnsDGResponse;
        this.executeMethod.setAccessible(true);

        // ── Discover lifecycle methods ──
        this.constructMethod = findMethod(clazz, new String[]{"construct", "init", "initialize"}, Map.class);
        this.stopMethod      = findMethod(clazz, new String[]{"stop", "cancel", "abort"});
        this.cleanupMethod   = findMethod(clazz, new String[]{"cleanup", "close", "destroy"});

        if (constructMethod != null) constructMethod.setAccessible(true);
        if (stopMethod != null) stopMethod.setAccessible(true);
        if (cleanupMethod != null) cleanupMethod.setAccessible(true);

        log.info("DGHandlerProxy wrapping {} → execute={} (DGRequest={}, DGResponse={}), " +
                 "construct={}, stop={}, cleanup={}",
                clazz.getSimpleName(),
                executeMethod.getName(), executeAcceptsDGRequest, executeReturnsDGResponse,
                constructMethod != null ? constructMethod.getName() : "none",
                stopMethod != null ? stopMethod.getName() : "none",
                cleanupMethod != null ? cleanupMethod.getName() : "none");
    }

    /**
     * Static factory: create a proxy from a class name.
     */
    public static DGHandlerProxy createFrom(String className) throws Exception {
        Class<?> clazz = Class.forName(className);
        Object instance = clazz.getDeclaredConstructor().newInstance();
        return new DGHandlerProxy(instance);
    }

    /**
     * Static factory: create a proxy from an already-loaded class.
     */
    public static DGHandlerProxy createFrom(Class<?> clazz) throws Exception {
        Object instance = clazz.getDeclaredConstructor().newInstance();
        return new DGHandlerProxy(instance);
    }

    // ─── DGHandler Interface Implementation ───────────────────────────────────

    @Override
    public void construct(Map<String, Object> config) {
        if (constructMethod != null) {
            try {
                constructMethod.invoke(delegate, config);
            } catch (InvocationTargetException e) {
                throw new RuntimeException("Proxied construct() failed: " + e.getTargetException().getMessage(),
                        e.getTargetException());
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Cannot access construct method", e);
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public DGResponse execute(DGRequest request) {
        if (stopped) return DGResponse.error(request.getRequestId(), "Handler was stopped (proxy)");

        try {
            Object result;
            if (executeAcceptsDGRequest) {
                result = executeMethod.invoke(delegate, request);
            } else {
                // Pass payload Map instead of DGRequest
                result = executeMethod.invoke(delegate, request.getPayload());
            }

            if (result == null) {
                return DGResponse.success(request.getRequestId(), Map.of("result", "null"));
            }

            if (executeReturnsDGResponse) {
                return (DGResponse) result;
            } else if (result instanceof Map) {
                return DGResponse.success(request.getRequestId(), (Map<String, Object>) result);
            } else {
                // Wrap primitive/String/Object return in a result map
                return DGResponse.success(request.getRequestId(), Map.of("result", result));
            }

        } catch (InvocationTargetException e) {
            Throwable cause = e.getTargetException();
            return DGResponse.error(request.getRequestId(),
                    "Proxied handler failed: " + cause.getClass().getSimpleName() + ": " + cause.getMessage());
        } catch (Exception e) {
            return DGResponse.error(request.getRequestId(),
                    "Proxy invocation error: " + e.getMessage());
        }
    }

    @Override
    public void stop() {
        stopped = true;
        if (stopMethod != null) {
            try {
                stopMethod.invoke(delegate);
            } catch (Exception e) {
                log.warn("Proxied stop() failed: {}", e.getMessage());
            }
        }
    }

    @Override
    public void cleanup() {
        if (cleanupMethod != null) {
            try {
                cleanupMethod.invoke(delegate);
            } catch (Exception e) {
                log.warn("Proxied cleanup() failed: {}", e.getMessage());
            }
        }
    }

    @Override
    public String getHandlerId() {
        return "proxy:" + delegate.getClass().getSimpleName() + "-" + Thread.currentThread().getId();
    }

    /**
     * @return the underlying delegate object
     */
    public Object getDelegate() { return delegate; }

    // ─── Method Discovery ─────────────────────────────────────────────────────

    private record MethodMatch(Method method, boolean acceptsDGRequest, boolean returnsDGResponse) {}

    private static MethodMatch findExecuteMethod(Class<?> clazz) {
        String[] names = {"execute", "handle", "process", "run"};
        for (String name : names) {
            // Try DGRequest parameter first
            try {
                Method m = clazz.getMethod(name, DGRequest.class);
                boolean retDGR = DGResponse.class.isAssignableFrom(m.getReturnType());
                return new MethodMatch(m, true, retDGR);
            } catch (NoSuchMethodException ignored) {}

            // Try Map parameter
            try {
                Method m = clazz.getMethod(name, Map.class);
                boolean retDGR = DGResponse.class.isAssignableFrom(m.getReturnType());
                return new MethodMatch(m, false, retDGR);
            } catch (NoSuchMethodException ignored) {}
        }
        return null;
    }

    private static Method findMethod(Class<?> clazz, String[] names, Class<?>... paramTypes) {
        for (String name : names) {
            try {
                Method m = clazz.getMethod(name, paramTypes);
                return m;
            } catch (NoSuchMethodException ignored) {}
            // Also try declared methods (including private)
            try {
                Method m = clazz.getDeclaredMethod(name, paramTypes);
                return m;
            } catch (NoSuchMethodException ignored) {}
        }
        return null;
    }
}
