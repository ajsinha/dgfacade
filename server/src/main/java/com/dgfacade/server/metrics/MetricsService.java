/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential. Patent Pending.
 */
package com.dgfacade.server.metrics;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Centralised metrics instrumentation for DGFacade using Micrometer.
 *
 * <p>Registers counters, timers, gauges, and distribution summaries with the
 * provided {@link MeterRegistry}. When a Prometheus registry is on the
 * classpath (via {@code micrometer-registry-prometheus}), all metrics are
 * automatically exposed at {@code /actuator/prometheus} in OpenMetrics format.</p>
 *
 * <h3>Metric Catalogue</h3>
 * <table>
 *   <tr><th>Metric</th><th>Type</th><th>Tags</th></tr>
 *   <tr><td>dgfacade.requests.total</td><td>Counter</td><td>request_type, user, channel, status</td></tr>
 *   <tr><td>dgfacade.requests.active</td><td>Gauge</td><td>—</td></tr>
 *   <tr><td>dgfacade.handler.execution.duration</td><td>Timer</td><td>request_type, handler_class, status</td></tr>
 *   <tr><td>dgfacade.handler.errors.total</td><td>Counter</td><td>request_type, error_type</td></tr>
 *   <tr><td>dgfacade.handler.timeouts.total</td><td>Counter</td><td>request_type</td></tr>
 *   <tr><td>dgfacade.api.http.requests</td><td>Timer</td><td>method, uri, status</td></tr>
 *   <tr><td>dgfacade.users.active</td><td>Gauge</td><td>—</td></tr>
 *   <tr><td>dgfacade.apikeys.total</td><td>Gauge</td><td>—</td></tr>
 *   <tr><td>dgfacade.handlers.registered</td><td>Gauge</td><td>—</td></tr>
 *   <tr><td>dgfacade.brokers.configured</td><td>Gauge</td><td>—</td></tr>
 *   <tr><td>dgfacade.channels.configured</td><td>Gauge</td><td>—</td></tr>
 *   <tr><td>dgfacade.handler.execution.payload_size</td><td>DistributionSummary</td><td>request_type</td></tr>
 *   <tr><td>dgfacade.uptime.seconds</td><td>TimeGauge</td><td>—</td></tr>
 * </table>
 */
public class MetricsService {

    private static final Logger log = LoggerFactory.getLogger(MetricsService.class);

    private final MeterRegistry registry;
    private final Instant startupTime;

    // Active request tracking
    private final AtomicInteger activeRequests = new AtomicInteger(0);

    // Gauge-backing atomics
    private final AtomicInteger userCount = new AtomicInteger(0);
    private final AtomicInteger apiKeyCount = new AtomicInteger(0);
    private final AtomicInteger handlerCount = new AtomicInteger(0);
    private final AtomicInteger brokerCount = new AtomicInteger(0);
    private final AtomicInteger channelCount = new AtomicInteger(0);

    // Counters cache for high-throughput paths (avoid repeated lookups)
    private final Map<String, Counter> requestCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> errorCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> timeoutCounters = new ConcurrentHashMap<>();
    private final Map<String, Timer> executionTimers = new ConcurrentHashMap<>();
    private final Map<String, DistributionSummary> payloadSummaries = new ConcurrentHashMap<>();

    public MetricsService(MeterRegistry registry) {
        this.registry = registry;
        this.startupTime = Instant.now();

        // Register gauges
        Gauge.builder("dgfacade.requests.active", activeRequests, AtomicInteger::get)
                .description("Number of handler requests currently executing")
                .register(registry);

        Gauge.builder("dgfacade.users.active", userCount, AtomicInteger::get)
                .description("Number of configured user accounts")
                .register(registry);

        Gauge.builder("dgfacade.apikeys.total", apiKeyCount, AtomicInteger::get)
                .description("Number of configured API keys")
                .register(registry);

        Gauge.builder("dgfacade.handlers.registered", handlerCount, AtomicInteger::get)
                .description("Number of registered handler request types")
                .register(registry);

        Gauge.builder("dgfacade.brokers.configured", brokerCount, AtomicInteger::get)
                .description("Number of configured message brokers")
                .register(registry);

        Gauge.builder("dgfacade.channels.configured", channelCount, AtomicInteger::get)
                .description("Number of configured data channels")
                .register(registry);

        TimeGauge.builder("dgfacade.uptime.seconds", this, TimeUnit.SECONDS,
                        ms -> Duration.between(startupTime, Instant.now()).toSeconds())
                .description("DGFacade uptime in seconds")
                .register(registry);

        log.info("MetricsService initialized — Prometheus metrics registered");
    }

    // ─── Request Lifecycle ─────────────────────────────────────────────────

    /**
     * Record the start of a request execution.
     */
    public void recordRequestStart(String requestType, String userId, String sourceChannel) {
        activeRequests.incrementAndGet();

        String key = requestType + "|" + userId + "|" + sourceChannel + "|submitted";
        requestCounters.computeIfAbsent(key, k ->
                Counter.builder("dgfacade.requests.total")
                        .description("Total requests processed")
                        .tag("request_type", requestType)
                        .tag("user", userId != null ? userId : "unknown")
                        .tag("channel", sourceChannel != null ? sourceChannel : "unknown")
                        .tag("status", "submitted")
                        .register(registry)
        ).increment();
    }

    /**
     * Record the successful completion of a request.
     */
    public void recordRequestSuccess(String requestType, String userId, String sourceChannel,
                                     String handlerClass, long durationMs) {
        activeRequests.decrementAndGet();

        // Status counter
        String key = requestType + "|" + userId + "|" + sourceChannel + "|success";
        requestCounters.computeIfAbsent(key, k ->
                Counter.builder("dgfacade.requests.total")
                        .description("Total requests processed")
                        .tag("request_type", requestType)
                        .tag("user", userId != null ? userId : "unknown")
                        .tag("channel", sourceChannel != null ? sourceChannel : "unknown")
                        .tag("status", "success")
                        .register(registry)
        ).increment();

        // Execution timer
        String timerKey = requestType + "|" + handlerClass + "|success";
        executionTimers.computeIfAbsent(timerKey, k ->
                Timer.builder("dgfacade.handler.execution.duration")
                        .description("Handler execution duration")
                        .tag("request_type", requestType)
                        .tag("handler_class", shortClassName(handlerClass))
                        .tag("status", "success")
                        .publishPercentiles(0.5, 0.9, 0.95, 0.99)
                        .publishPercentileHistogram()
                        .register(registry)
        ).record(durationMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Record a failed request.
     */
    public void recordRequestError(String requestType, String userId, String sourceChannel,
                                   String handlerClass, String errorType, long durationMs) {
        activeRequests.decrementAndGet();

        // Status counter
        String key = requestType + "|" + userId + "|" + sourceChannel + "|error";
        requestCounters.computeIfAbsent(key, k ->
                Counter.builder("dgfacade.requests.total")
                        .description("Total requests processed")
                        .tag("request_type", requestType)
                        .tag("user", userId != null ? userId : "unknown")
                        .tag("channel", sourceChannel != null ? sourceChannel : "unknown")
                        .tag("status", "error")
                        .register(registry)
        ).increment();

        // Error counter
        String errKey = requestType + "|" + errorType;
        errorCounters.computeIfAbsent(errKey, k ->
                Counter.builder("dgfacade.handler.errors.total")
                        .description("Total handler errors")
                        .tag("request_type", requestType)
                        .tag("error_type", errorType != null ? errorType : "unknown")
                        .register(registry)
        ).increment();

        // Execution timer (even for errors)
        if (durationMs > 0) {
            String timerKey = requestType + "|" + handlerClass + "|error";
            executionTimers.computeIfAbsent(timerKey, k ->
                    Timer.builder("dgfacade.handler.execution.duration")
                            .description("Handler execution duration")
                            .tag("request_type", requestType)
                            .tag("handler_class", shortClassName(handlerClass))
                            .tag("status", "error")
                            .publishPercentiles(0.5, 0.9, 0.95, 0.99)
                            .publishPercentileHistogram()
                            .register(registry)
            ).record(durationMs, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Record a timed-out request.
     */
    public void recordRequestTimeout(String requestType) {
        activeRequests.decrementAndGet();

        timeoutCounters.computeIfAbsent(requestType, k ->
                Counter.builder("dgfacade.handler.timeouts.total")
                        .description("Total handler timeouts (TTL exceeded)")
                        .tag("request_type", requestType)
                        .register(registry)
        ).increment();
    }

    /**
     * Record the size of a request payload.
     */
    public void recordPayloadSize(String requestType, int sizeEstimate) {
        payloadSummaries.computeIfAbsent(requestType, k ->
                DistributionSummary.builder("dgfacade.handler.execution.payload_size")
                        .description("Estimated payload size in bytes")
                        .tag("request_type", requestType)
                        .publishPercentiles(0.5, 0.9, 0.99)
                        .register(registry)
        ).record(sizeEstimate);
    }

    // ─── API HTTP Metrics ───────────────────────────────────────────────

    /**
     * Record an HTTP API call. Used by the ApiController.
     */
    public void recordHttpRequest(String method, String uri, int statusCode, long durationMs) {
        Timer.builder("dgfacade.api.http.requests")
                .description("HTTP API request duration")
                .tag("method", method)
                .tag("uri", uri)
                .tag("status", String.valueOf(statusCode))
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    // ─── Configuration Gauges ───────────────────────────────────────────

    public void updateUserCount(int count) { userCount.set(count); }
    public void updateApiKeyCount(int count) { apiKeyCount.set(count); }
    public void updateHandlerCount(int count) { handlerCount.set(count); }
    public void updateBrokerCount(int count) { brokerCount.set(count); }
    public void updateChannelCount(int count) { channelCount.set(count); }

    // ─── Utility ────────────────────────────────────────────────────────

    private String shortClassName(String fqcn) {
        if (fqcn == null) return "unknown";
        int dot = fqcn.lastIndexOf('.');
        return dot >= 0 ? fqcn.substring(dot + 1) : fqcn;
    }

    public MeterRegistry getRegistry() { return registry; }
    public int getActiveRequestCount() { return activeRequests.get(); }
    public Instant getStartupTime() { return startupTime; }
}
