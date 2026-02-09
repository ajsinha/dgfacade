/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential. Patent Pending.
 */
package com.dgfacade.server.cluster;

import com.dgfacade.common.model.ClusterNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lightweight HTTP-based cluster service for distributed DGFacade deployments.
 *
 * <p>DGFacade can run in two modes:</p>
 * <ul>
 *   <li><b>Standalone</b> (default) — No seed nodes configured. The node operates
 *       independently with all roles (gateway + executor). Zero additional overhead.</li>
 *   <li><b>Cluster</b> — One or more seed node URLs are configured. The node joins
 *       the cluster via HTTP heartbeats, discovers peers, and can distribute handler
 *       execution across multiple nodes.</li>
 * </ul>
 *
 * <h3>Cluster Protocol</h3>
 * <p>Nodes communicate via simple REST endpoints:</p>
 * <ul>
 *   <li>{@code POST /api/v1/cluster/heartbeat} — Send node state to a peer</li>
 *   <li>{@code GET  /api/v1/cluster/nodes} — Get all known nodes from a peer</li>
 *   <li>{@code POST /api/v1/cluster/forward} — Forward a DGRequest for remote execution</li>
 * </ul>
 *
 * <h3>Node Health</h3>
 * <p>A node is marked SUSPECT after 2 missed heartbeats and DOWN after 5.
 * DOWN nodes are evicted after 10 minutes.</p>
 */
public class ClusterService {

    private static final Logger log = LoggerFactory.getLogger(ClusterService.class);
    private static final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    // --- Configuration ---
    private final ClusterNode self;
    private final List<String> seedNodes;
    private final int heartbeatIntervalSeconds;
    private final boolean clusterEnabled;

    // --- State ---
    private final ConcurrentHashMap<String, ClusterNode> peers = new ConcurrentHashMap<>();
    private final AtomicLong totalRequestsForwarded = new AtomicLong(0);
    private final AtomicLong totalRequestsReceived = new AtomicLong(0);
    private ScheduledExecutorService scheduler;
    private HttpClient httpClient;

    // Round-robin index for node selection
    private int roundRobinIndex = 0;

    /**
     * Create a new ClusterService.
     *
     * @param host       this node's hostname or IP
     * @param port       this node's HTTP port
     * @param version    application version
     * @param role       node role (BOTH, GATEWAY, EXECUTOR)
     * @param seedNodes  comma-separated list of seed node URLs (empty = standalone)
     * @param heartbeatSeconds heartbeat interval in seconds
     */
    public ClusterService(String host, int port, String version,
                          String role, String seedNodes, int heartbeatSeconds) {
        this.self = new ClusterNode(host, port, version);
        try {
            this.self.setRole(ClusterNode.Role.valueOf(role.toUpperCase()));
        } catch (Exception e) {
            this.self.setRole(ClusterNode.Role.BOTH);
        }

        // Parse seed nodes
        this.seedNodes = new ArrayList<>();
        if (seedNodes != null && !seedNodes.isBlank()) {
            for (String seed : seedNodes.split(",")) {
                String trimmed = seed.trim();
                if (!trimmed.isBlank()) {
                    this.seedNodes.add(trimmed);
                }
            }
        }

        this.heartbeatIntervalSeconds = Math.max(heartbeatSeconds, 5);
        this.clusterEnabled = !this.seedNodes.isEmpty();

        log.info("ClusterService initialized: nodeId={}, host={}:{}, role={}, cluster={}",
                self.getNodeId(), host, port, self.getRole(),
                clusterEnabled ? "ENABLED (" + this.seedNodes.size() + " seed nodes)" : "STANDALONE");
    }

    /**
     * Start the cluster service. If cluster is enabled, begins heartbeat cycle.
     * If standalone, this is a no-op (the node simply tracks itself).
     */
    public void start() {
        if (!clusterEnabled) {
            log.info("Cluster mode: STANDALONE — no seed nodes configured. " +
                    "All execution is local. Set dgfacade.cluster.seed-nodes to enable clustering.");
            return;
        }

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "dgfacade-cluster-heartbeat");
            t.setDaemon(true);
            return t;
        });

        // Initial discovery from seed nodes
        scheduler.schedule(this::discoverPeers, 2, TimeUnit.SECONDS);

        // Periodic heartbeat
        scheduler.scheduleAtFixedRate(this::heartbeatCycle,
                heartbeatIntervalSeconds, heartbeatIntervalSeconds, TimeUnit.SECONDS);

        log.info("Cluster heartbeat started: interval={}s, seeds={}",
                heartbeatIntervalSeconds, seedNodes);
    }

    /**
     * Stop the cluster service. Sends a LEAVING notification to peers.
     */
    public void stop() {
        if (scheduler != null) {
            self.setStatus(ClusterNode.Status.LEAVING);
            // Best-effort notify peers
            for (ClusterNode peer : peers.values()) {
                try { sendHeartbeat(peer.getBaseUrl()); } catch (Exception ignored) {}
            }
            scheduler.shutdownNow();
        }
        if (httpClient != null) {
            httpClient = null;
        }
        log.info("ClusterService stopped");
    }

    // ─── Public API ──────────────────────────────────────────────────────────

    /** Is this node part of a multi-node cluster? */
    public boolean isClusterEnabled() { return clusterEnabled; }

    /** Get this node's info (always up-to-date with metrics). */
    public ClusterNode getSelf() { return self; }

    /** Get all known peer nodes (excludes self). */
    public Collection<ClusterNode> getPeers() { return peers.values(); }

    /** Get all cluster nodes including self. */
    public List<ClusterNode> getAllNodes() {
        List<ClusterNode> all = new ArrayList<>();
        all.add(self);
        all.addAll(peers.values());
        return all;
    }

    /** Total nodes in the cluster (including self). */
    public int getClusterSize() { return 1 + peers.size(); }

    /** Get counts by status. */
    public Map<String, Integer> getStatusCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("UP", 0);
        counts.put("SUSPECT", 0);
        counts.put("DOWN", 0);
        counts.put("LEAVING", 0);
        // Count self
        counts.merge(self.getStatus().name(), 1, Integer::sum);
        for (ClusterNode peer : peers.values()) {
            counts.merge(peer.getStatus().name(), 1, Integer::sum);
        }
        return counts;
    }

    /** Update this node's metrics (called from ExecutionEngine periodically). */
    public void updateSelfMetrics(int activeHandlers, long totalRequests) {
        self.updateMetrics(activeHandlers, totalRequests);
    }

    /** Record a received heartbeat from a peer. */
    public void receiveHeartbeat(ClusterNode peerNode) {
        if (peerNode == null || peerNode.getNodeId() == null) return;
        if (peerNode.getNodeId().equals(self.getNodeId())) return; // ignore self
        peerNode.setLastHeartbeat(Instant.now());
        if (peerNode.getStatus() != ClusterNode.Status.LEAVING) {
            peerNode.setStatus(ClusterNode.Status.UP);
        }
        ClusterNode existing = peers.get(peerNode.getNodeId());
        if (existing == null) {
            log.info("New cluster peer discovered: {} ({}:{}, role={})",
                    peerNode.getNodeId(), peerNode.getHost(), peerNode.getPort(), peerNode.getRole());
        }
        peers.put(peerNode.getNodeId(), peerNode);
    }

    /**
     * Select a peer node for work distribution (round-robin among UP executors).
     * Returns null if no suitable peers are available (execute locally).
     */
    public ClusterNode selectExecutorNode() {
        if (!clusterEnabled) return null;

        List<ClusterNode> candidates = new ArrayList<>();
        for (ClusterNode peer : peers.values()) {
            if (peer.getStatus() == ClusterNode.Status.UP &&
                (peer.getRole() == ClusterNode.Role.EXECUTOR ||
                 peer.getRole() == ClusterNode.Role.BOTH)) {
                candidates.add(peer);
            }
        }
        if (candidates.isEmpty()) return null;

        // Round-robin selection
        roundRobinIndex = (roundRobinIndex + 1) % candidates.size();
        return candidates.get(roundRobinIndex);
    }

    public long getTotalRequestsForwarded() { return totalRequestsForwarded.get(); }
    public void incrementForwarded() { totalRequestsForwarded.incrementAndGet(); }

    public long getTotalRequestsReceived() { return totalRequestsReceived.get(); }
    public void incrementReceived() { totalRequestsReceived.incrementAndGet(); }

    // ─── Heartbeat Cycle ─────────────────────────────────────────────────────

    private void heartbeatCycle() {
        try {
            // 1. Update self metrics
            self.setLastHeartbeat(Instant.now());

            // 2. Send heartbeat to all known peers
            Set<String> targetUrls = new HashSet<>(seedNodes);
            for (ClusterNode peer : peers.values()) {
                targetUrls.add(peer.getBaseUrl());
            }
            // Don't send to self
            targetUrls.remove(self.getBaseUrl());

            for (String url : targetUrls) {
                try {
                    sendHeartbeat(url);
                } catch (Exception e) {
                    log.debug("Heartbeat to {} failed: {}", url, e.getMessage());
                }
            }

            // 3. Check peer health
            Instant now = Instant.now();
            Iterator<Map.Entry<String, ClusterNode>> it = peers.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, ClusterNode> entry = it.next();
                ClusterNode peer = entry.getValue();
                long secondsSinceHeartbeat = Duration.between(peer.getLastHeartbeat(), now).toSeconds();

                if (peer.getStatus() == ClusterNode.Status.LEAVING) {
                    if (secondsSinceHeartbeat > 60) {
                        log.info("Evicting LEAVING node: {}", peer.getNodeId());
                        it.remove();
                    }
                } else if (secondsSinceHeartbeat > heartbeatIntervalSeconds * 5) {
                    if (peer.getStatus() != ClusterNode.Status.DOWN) {
                        log.warn("Cluster peer DOWN: {} (no heartbeat for {}s)",
                                peer.getNodeId(), secondsSinceHeartbeat);
                        peer.setStatus(ClusterNode.Status.DOWN);
                    }
                    // Evict after 10 minutes
                    if (secondsSinceHeartbeat > 600) {
                        log.info("Evicting DOWN node: {}", peer.getNodeId());
                        it.remove();
                    }
                } else if (secondsSinceHeartbeat > heartbeatIntervalSeconds * 2) {
                    if (peer.getStatus() == ClusterNode.Status.UP) {
                        log.warn("Cluster peer SUSPECT: {} (no heartbeat for {}s)",
                                peer.getNodeId(), secondsSinceHeartbeat);
                        peer.setStatus(ClusterNode.Status.SUSPECT);
                    }
                }
            }

        } catch (Exception e) {
            log.error("Heartbeat cycle error: {}", e.getMessage());
        }
    }

    private void sendHeartbeat(String peerBaseUrl) throws Exception {
        String json = mapper.writeValueAsString(self);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(peerBaseUrl + "/api/v1/cluster/heartbeat"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(3))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(resp -> {
                    if (resp.statusCode() == 200) {
                        try {
                            ClusterNode respNode = mapper.readValue(resp.body(), ClusterNode.class);
                            receiveHeartbeat(respNode);
                        } catch (Exception e) {
                            log.debug("Failed to parse heartbeat response from {}: {}", peerBaseUrl, e.getMessage());
                        }
                    }
                });
    }

    private void discoverPeers() {
        log.info("Discovering cluster peers from {} seed node(s)...", seedNodes.size());
        for (String seedUrl : seedNodes) {
            try {
                sendHeartbeat(seedUrl);
                // Also request their known nodes
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(seedUrl + "/api/v1/cluster/nodes"))
                        .header("Accept", "application/json")
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build();
                httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                        .thenAccept(resp -> {
                            if (resp.statusCode() == 200) {
                                try {
                                    ClusterNode[] nodes = mapper.readValue(resp.body(), ClusterNode[].class);
                                    for (ClusterNode node : nodes) {
                                        receiveHeartbeat(node);
                                    }
                                } catch (Exception e) {
                                    log.debug("Failed to parse node list from {}", seedUrl);
                                }
                            }
                        });
            } catch (Exception e) {
                log.warn("Could not reach seed node {}: {}", seedUrl, e.getMessage());
            }
        }
    }
}
