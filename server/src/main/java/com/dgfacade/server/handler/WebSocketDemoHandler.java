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

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.time.Instant;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * <b>WebSocketDemoHandler</b> — Demonstrates WebSocket-based handler execution.
 *
 * <p>This handler generates rich, simulated real-time data suitable for display
 * in the WebSocket Playground UI. Supports multiple operation modes:</p>
 * <ul>
 *   <li><b>SYSTEM_PROBE</b> — Collects live JVM and OS metrics (memory, threads, uptime, CPU load)</li>
 *   <li><b>MARKET_TICK</b> — Simulates a batch of market data ticks with random price movements</li>
 *   <li><b>PING_BURST</b> — Measures request round-trip latency with timestamps at each stage</li>
 *   <li><b>DATA_GENERATE</b> — Generates N random data records (for load/volume testing)</li>
 * </ul>
 *
 * <h3>Usage via WebSocket Playground</h3>
 * <pre>{@code
 * {
 *   "api_key": "dgf-admin-key-0001",
 *   "request_type": "WS_DEMO",
 *   "payload": {
 *     "operation": "MARKET_TICK",
 *     "symbols": ["AAPL", "GOOG", "MSFT", "TSLA"],
 *     "ticks_per_symbol": 5
 *   }
 * }
 * }</pre>
 */
public class WebSocketDemoHandler implements DGHandler {

    private static final Logger log = LoggerFactory.getLogger(WebSocketDemoHandler.class);

    private Map<String, Object> config;
    private volatile boolean stopped = false;

    @Override
    public void construct(Map<String, Object> config) {
        this.config = config != null ? config : Map.of();
    }

    @Override
    public DGResponse execute(DGRequest request) {
        if (stopped) return DGResponse.error(request.getRequestId(), "Handler stopped");
        Instant start = Instant.now();

        Map<String, Object> payload = request.getPayload();
        if (payload == null) payload = Map.of();
        String operation = str(payload, "operation", "SYSTEM_PROBE");

        try {
            Map<String, Object> result = switch (operation.toUpperCase()) {
                case "MARKET_TICK" -> executeMarketTick(payload);
                case "PING_BURST" -> executePingBurst(request, start);
                case "DATA_GENERATE" -> executeDataGenerate(payload);
                default -> executeSystemProbe();
            };

            result.put("_operation", operation);
            result.put("_handler", "WebSocketDemoHandler");
            result.put("_source", "WebSocket");
            result.put("_execution_ms", Duration.between(start, Instant.now()).toMillis());
            result.put("_timestamp", Instant.now().toString());

            return DGResponse.success(request.getRequestId(), result);
        } catch (Exception e) {
            log.error("WebSocketDemoHandler failed: {}", e.getMessage(), e);
            return DGResponse.error(request.getRequestId(), "WS_DEMO error: " + e.getMessage());
        }
    }

    // ── SYSTEM_PROBE: Live JVM + OS metrics ──────────────────────────────

    private Map<String, Object> executeSystemProbe() {
        Map<String, Object> result = new LinkedHashMap<>();

        // JVM Memory
        MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
        Map<String, Object> memory = new LinkedHashMap<>();
        memory.put("heap_used_mb", mem.getHeapMemoryUsage().getUsed() / (1024 * 1024));
        memory.put("heap_max_mb", mem.getHeapMemoryUsage().getMax() / (1024 * 1024));
        memory.put("heap_pct", mem.getHeapMemoryUsage().getMax() > 0
                ? Math.round(100.0 * mem.getHeapMemoryUsage().getUsed() / mem.getHeapMemoryUsage().getMax()) : 0);
        memory.put("non_heap_used_mb", mem.getNonHeapMemoryUsage().getUsed() / (1024 * 1024));
        result.put("memory", memory);

        // OS
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        Map<String, Object> system = new LinkedHashMap<>();
        system.put("os_name", os.getName());
        system.put("os_arch", os.getArch());
        system.put("available_processors", os.getAvailableProcessors());
        system.put("system_load_average", Math.round(os.getSystemLoadAverage() * 100.0) / 100.0);
        result.put("system", system);

        // Runtime
        RuntimeMXBean rt = ManagementFactory.getRuntimeMXBean();
        Map<String, Object> runtime = new LinkedHashMap<>();
        runtime.put("jvm_name", rt.getVmName());
        runtime.put("jvm_version", rt.getVmVersion());
        long uptimeMs = rt.getUptime();
        runtime.put("uptime_seconds", uptimeMs / 1000);
        runtime.put("uptime_display", formatDuration(uptimeMs));
        result.put("runtime", runtime);

        // Threads
        Map<String, Object> threads = new LinkedHashMap<>();
        threads.put("active_count", Thread.activeCount());
        threads.put("thread_count", ManagementFactory.getThreadMXBean().getThreadCount());
        threads.put("peak_thread_count", ManagementFactory.getThreadMXBean().getPeakThreadCount());
        threads.put("daemon_thread_count", ManagementFactory.getThreadMXBean().getDaemonThreadCount());
        result.put("threads", threads);

        return result;
    }

    // ── MARKET_TICK: Simulated market data ───────────────────────────────

    private Map<String, Object> executeMarketTick(Map<String, Object> payload) {
        @SuppressWarnings("unchecked")
        List<String> symbols = payload.containsKey("symbols")
                ? (List<String>) payload.get("symbols")
                : List.of("AAPL", "GOOG", "MSFT", "AMZN");
        int ticksPerSymbol = intVal(payload, "ticks_per_symbol", 3);

        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> ticks = new ArrayList<>();
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        Map<String, Double> basePrices = Map.of(
                "AAPL", 185.0, "GOOG", 175.0, "MSFT", 420.0, "AMZN", 190.0,
                "TSLA", 250.0, "NVDA", 900.0, "META", 530.0, "NFLX", 850.0);

        int seq = 0;
        for (String sym : symbols) {
            double basePrice = basePrices.getOrDefault(sym, 100.0 + rng.nextDouble(200));
            double price = basePrice;
            for (int i = 0; i < ticksPerSymbol; i++) {
                double change = (rng.nextDouble() - 0.48) * 2.5;  // slight upward bias
                price = Math.max(1.0, price + change);
                Map<String, Object> tick = new LinkedHashMap<>();
                tick.put("seq", ++seq);
                tick.put("symbol", sym);
                tick.put("price", Math.round(price * 100.0) / 100.0);
                tick.put("change", Math.round(change * 100.0) / 100.0);
                tick.put("change_pct", Math.round((change / price) * 10000.0) / 100.0);
                tick.put("volume", rng.nextInt(1000, 50000));
                tick.put("bid", Math.round((price - rng.nextDouble(0.5)) * 100.0) / 100.0);
                tick.put("ask", Math.round((price + rng.nextDouble(0.5)) * 100.0) / 100.0);
                tick.put("timestamp", Instant.now().plusMillis(seq * 10L).toString());
                ticks.add(tick);
            }
        }

        result.put("market_data", ticks);
        result.put("total_ticks", ticks.size());
        result.put("symbols_count", symbols.size());
        result.put("exchange", "SIMULATED");
        return result;
    }

    // ── PING_BURST: Latency measurement ──────────────────────────────────

    private Map<String, Object> executePingBurst(DGRequest request, Instant handlerStart) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> pings = new ArrayList<>();

        for (int i = 1; i <= 5; i++) {
            Instant pingStart = Instant.now();
            // Simulate minimal work
            long hash = Objects.hash(request.getRequestId(), i, System.nanoTime());
            Instant pingEnd = Instant.now();
            Map<String, Object> ping = new LinkedHashMap<>();
            ping.put("ping_seq", i);
            ping.put("nanos", Duration.between(pingStart, pingEnd).toNanos());
            ping.put("hash", Long.toHexString(Math.abs(hash)));
            ping.put("timestamp", pingEnd.toString());
            pings.add(ping);
        }

        result.put("pings", pings);
        result.put("request_received_at", request.getReceivedAt() != null ? request.getReceivedAt().toString() : "N/A");
        result.put("handler_started_at", handlerStart.toString());
        result.put("total_pings", 5);
        result.put("avg_ping_nanos", pings.stream()
                .mapToLong(p -> ((Number) p.get("nanos")).longValue())
                .average().orElse(0));
        return result;
    }

    // ── DATA_GENERATE: Random record generation ──────────────────────────

    private Map<String, Object> executeDataGenerate(Map<String, Object> payload) {
        int count = Math.min(intVal(payload, "count", 10), 500);
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> records = new ArrayList<>();
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        String[] firstNames = {"Alice", "Bob", "Charlie", "Diana", "Eve", "Frank", "Grace", "Henry", "Iris", "Jack"};
        String[] departments = {"Engineering", "Product", "Design", "Marketing", "Sales", "Support", "Finance", "HR"};
        String[] statuses = {"ACTIVE", "ACTIVE", "ACTIVE", "PENDING", "REVIEW", "INACTIVE"};

        for (int i = 0; i < count; i++) {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("id", "rec-" + UUID.randomUUID().toString().substring(0, 8));
            record.put("name", firstNames[rng.nextInt(firstNames.length)] + " " + (char) ('A' + rng.nextInt(26)) + ".");
            record.put("department", departments[rng.nextInt(departments.length)]);
            record.put("status", statuses[rng.nextInt(statuses.length)]);
            record.put("score", rng.nextInt(60, 100));
            record.put("metric", Math.round(rng.nextDouble(1.0, 99.9) * 10.0) / 10.0);
            records.add(record);
        }

        result.put("records", records);
        result.put("total_generated", count);
        result.put("schema", List.of("id", "name", "department", "status", "score", "metric"));
        return result;
    }

    @Override
    public void stop() { stopped = true; }

    @Override
    public void cleanup() { config = null; }

    private static String str(Map<String, Object> m, String k, String d) { Object v = m.get(k); return v != null ? v.toString() : d; }
    private static int intVal(Map<String, Object> m, String k, int d) { Object v = m.get(k); if (v instanceof Number n) return n.intValue(); if (v instanceof String s) { try { return Integer.parseInt(s); } catch (Exception e) { return d; } } return d; }

    private static String formatDuration(long ms) {
        long s = ms / 1000; long h = s / 3600; long m = (s % 3600) / 60; long sec = s % 60;
        if (h > 0) return h + "h " + m + "m " + sec + "s";
        if (m > 0) return m + "m " + sec + "s";
        return sec + "s";
    }
}
