/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential. Patent Pending.
 */
package com.dgfacade.common.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a single node in a DGFacade cluster.
 *
 * <p>Each DGFacade instance registers itself as a cluster node, either as a
 * standalone single-node cluster or as part of a multi-node deployment.
 * Nodes communicate via lightweight HTTP heartbeats and can distribute
 * handler execution across the cluster.</p>
 *
 * <h3>Node Roles</h3>
 * <ul>
 *   <li><b>BOTH</b> — Accepts ingestion traffic AND executes handlers (default, standalone)</li>
 *   <li><b>GATEWAY</b> — Accepts REST/WebSocket/Kafka traffic, forwards execution to EXECUTOR nodes</li>
 *   <li><b>EXECUTOR</b> — Executes handlers only; does not accept external traffic directly</li>
 * </ul>
 *
 * <h3>Node Status</h3>
 * <ul>
 *   <li><b>UP</b> — Node is healthy and accepting work</li>
 *   <li><b>SUSPECT</b> — Heartbeat missed; node may be down</li>
 *   <li><b>DOWN</b> — Multiple heartbeats missed; node considered unreachable</li>
 *   <li><b>LEAVING</b> — Node is shutting down gracefully</li>
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClusterNode {

    public enum Role { BOTH, GATEWAY, EXECUTOR }
    public enum Status { UP, SUSPECT, DOWN, LEAVING }

    @JsonProperty("node_id")
    private String nodeId;

    @JsonProperty("host")
    private String host;

    @JsonProperty("port")
    private int port;

    @JsonProperty("role")
    private Role role = Role.BOTH;

    @JsonProperty("status")
    private Status status = Status.UP;

    @JsonProperty("version")
    private String version;

    @JsonProperty("started_at")
    private Instant startedAt;

    @JsonProperty("last_heartbeat")
    private Instant lastHeartbeat;

    @JsonProperty("active_handlers")
    private int activeHandlers;

    @JsonProperty("total_requests_processed")
    private long totalRequestsProcessed;

    @JsonProperty("cpu_load")
    private double cpuLoad;

    @JsonProperty("heap_used_mb")
    private long heapUsedMb;

    @JsonProperty("heap_max_mb")
    private long heapMaxMb;

    public ClusterNode() {
        this.nodeId = "node-" + UUID.randomUUID().toString().substring(0, 8);
        this.startedAt = Instant.now();
        this.lastHeartbeat = Instant.now();
    }

    public ClusterNode(String host, int port, String version) {
        this();
        this.host = host;
        this.port = port;
        this.version = version;
    }

    /** The base URL for this node (e.g. http://host:port) */
    public String getBaseUrl() {
        return "http://" + host + ":" + port;
    }

    /** Update runtime metrics snapshot */
    public void updateMetrics(int activeHandlers, long totalRequests) {
        this.activeHandlers = activeHandlers;
        this.totalRequestsProcessed = totalRequests;
        this.lastHeartbeat = Instant.now();
        Runtime rt = Runtime.getRuntime();
        this.heapUsedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        this.heapMaxMb = rt.maxMemory() / (1024 * 1024);
        try {
            java.lang.management.OperatingSystemMXBean os =
                    java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            this.cpuLoad = os.getSystemLoadAverage();
        } catch (Exception e) { this.cpuLoad = -1; }
    }

    // --- Getters and Setters ---
    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getLastHeartbeat() { return lastHeartbeat; }
    public void setLastHeartbeat(Instant lastHeartbeat) { this.lastHeartbeat = lastHeartbeat; }
    public int getActiveHandlers() { return activeHandlers; }
    public void setActiveHandlers(int activeHandlers) { this.activeHandlers = activeHandlers; }
    public long getTotalRequestsProcessed() { return totalRequestsProcessed; }
    public void setTotalRequestsProcessed(long totalRequestsProcessed) { this.totalRequestsProcessed = totalRequestsProcessed; }
    public double getCpuLoad() { return cpuLoad; }
    public void setCpuLoad(double cpuLoad) { this.cpuLoad = cpuLoad; }
    public long getHeapUsedMb() { return heapUsedMb; }
    public void setHeapUsedMb(long heapUsedMb) { this.heapUsedMb = heapUsedMb; }
    public long getHeapMaxMb() { return heapMaxMb; }
    public void setHeapMaxMb(long heapMaxMb) { this.heapMaxMb = heapMaxMb; }
}
