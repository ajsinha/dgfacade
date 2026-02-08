/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential. Patent Pending.
 */
package com.dgfacade.server.handler;

import com.dgfacade.common.model.DGRequest;
import com.dgfacade.common.model.DGResponse;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Admin-only handler that reports JVM and system information.
 *
 * <p>Useful for diagnostics, capacity planning, and health dashboards.
 * Returns metrics about memory, CPU, JVM version, uptime, threads, and OS details.</p>
 *
 * <p>Supported operations via {@code payload.section}:</p>
 * <ul>
 *   <li><b>ALL</b> (default) — full system snapshot</li>
 *   <li><b>MEMORY</b> — heap and non-heap memory usage</li>
 *   <li><b>CPU</b> — available processors and system load</li>
 *   <li><b>JVM</b> — Java version, vendor, VM info</li>
 *   <li><b>OS</b> — operating system name, version, architecture</li>
 *   <li><b>THREADS</b> — thread count and peak</li>
 *   <li><b>UPTIME</b> — JVM uptime breakdown</li>
 * </ul>
 */
public class SystemInfoHandler implements DGHandler {

    private volatile boolean stopped = false;

    @Override
    public void construct(Map<String, Object> config) { /* no config needed */ }

    @Override
    public DGResponse execute(DGRequest request) {
        if (stopped) return DGResponse.error(request.getRequestId(), "Handler was stopped");
        try {
            String section = "ALL";
            if (request.getPayload() != null) {
                section = String.valueOf(request.getPayload().getOrDefault("section", "ALL")).toUpperCase();
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("timestamp", Instant.now().toString());

            switch (section) {
                case "MEMORY" -> result.put("memory", getMemoryInfo());
                case "CPU" -> result.put("cpu", getCpuInfo());
                case "JVM" -> result.put("jvm", getJvmInfo());
                case "OS" -> result.put("os", getOsInfo());
                case "THREADS" -> result.put("threads", getThreadInfo());
                case "UPTIME" -> result.put("uptime", getUptimeInfo());
                default -> {
                    result.put("memory", getMemoryInfo());
                    result.put("cpu", getCpuInfo());
                    result.put("jvm", getJvmInfo());
                    result.put("os", getOsInfo());
                    result.put("threads", getThreadInfo());
                    result.put("uptime", getUptimeInfo());
                    result.put("environment", getEnvSummary());
                }
            }

            return DGResponse.success(request.getRequestId(), result);
        } catch (Exception e) {
            return DGResponse.error(request.getRequestId(), "SystemInfo error: " + e.getMessage());
        }
    }

    private Map<String, Object> getMemoryInfo() {
        MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
        Runtime rt = Runtime.getRuntime();
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("heap_used_mb", mem.getHeapMemoryUsage().getUsed() / (1024 * 1024));
        info.put("heap_max_mb", mem.getHeapMemoryUsage().getMax() / (1024 * 1024));
        info.put("heap_committed_mb", mem.getHeapMemoryUsage().getCommitted() / (1024 * 1024));
        info.put("non_heap_used_mb", mem.getNonHeapMemoryUsage().getUsed() / (1024 * 1024));
        info.put("free_memory_mb", rt.freeMemory() / (1024 * 1024));
        info.put("total_memory_mb", rt.totalMemory() / (1024 * 1024));
        info.put("max_memory_mb", rt.maxMemory() / (1024 * 1024));
        double usedPct = 100.0 * mem.getHeapMemoryUsage().getUsed() / mem.getHeapMemoryUsage().getMax();
        info.put("heap_usage_percent", Math.round(usedPct * 100.0) / 100.0);
        return info;
    }

    private Map<String, Object> getCpuInfo() {
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("available_processors", os.getAvailableProcessors());
        info.put("system_load_average", os.getSystemLoadAverage());
        info.put("arch", os.getArch());
        return info;
    }

    private Map<String, Object> getJvmInfo() {
        RuntimeMXBean rt = ManagementFactory.getRuntimeMXBean();
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("java_version", System.getProperty("java.version"));
        info.put("java_vendor", System.getProperty("java.vendor"));
        info.put("vm_name", rt.getVmName());
        info.put("vm_version", rt.getVmVersion());
        info.put("spec_version", rt.getSpecVersion());
        info.put("classpath_entries", rt.getClassPath().split(System.getProperty("path.separator")).length);
        info.put("input_arguments", rt.getInputArguments());
        return info;
    }

    private Map<String, Object> getOsInfo() {
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", os.getName());
        info.put("version", os.getVersion());
        info.put("arch", os.getArch());
        info.put("user_name", System.getProperty("user.name"));
        info.put("user_dir", System.getProperty("user.dir"));
        info.put("file_encoding", System.getProperty("file.encoding"));
        return info;
    }

    private Map<String, Object> getThreadInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("active_threads", Thread.activeCount());
        info.put("daemon_thread_count", ManagementFactory.getThreadMXBean().getDaemonThreadCount());
        info.put("peak_thread_count", ManagementFactory.getThreadMXBean().getPeakThreadCount());
        info.put("total_started_threads", ManagementFactory.getThreadMXBean().getTotalStartedThreadCount());
        return info;
    }

    private Map<String, Object> getUptimeInfo() {
        RuntimeMXBean rt = ManagementFactory.getRuntimeMXBean();
        long uptimeMs = rt.getUptime();
        Duration d = Duration.ofMillis(uptimeMs);
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("uptime_ms", uptimeMs);
        info.put("uptime_human", String.format("%dd %dh %dm %ds",
                d.toDays(), d.toHoursPart(), d.toMinutesPart(), d.toSecondsPart()));
        info.put("start_time", Instant.ofEpochMilli(rt.getStartTime()).toString());
        return info;
    }

    private Map<String, Object> getEnvSummary() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("java_home", System.getProperty("java.home"));
        info.put("temp_dir", System.getProperty("java.io.tmpdir"));
        info.put("timezone", TimeZone.getDefault().getID());
        info.put("locale", Locale.getDefault().toString());
        return info;
    }

    @Override
    public void stop() { stopped = true; }

    @Override
    public void cleanup() {}
}
