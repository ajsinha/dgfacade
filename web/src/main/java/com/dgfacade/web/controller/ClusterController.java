/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential. Patent Pending.
 */
package com.dgfacade.web.controller;

import com.dgfacade.common.model.ClusterNode;
import com.dgfacade.server.cluster.ClusterService;
import com.dgfacade.server.engine.ExecutionEngine;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST controller for cluster operations and the cluster monitoring UI.
 *
 * <p>Exposes three cluster API endpoints used by peer nodes:</p>
 * <ul>
 *   <li>{@code POST /api/v1/cluster/heartbeat} — Receive a heartbeat from a peer</li>
 *   <li>{@code GET  /api/v1/cluster/nodes} — Return all known cluster nodes</li>
 *   <li>{@code GET  /api/v1/cluster/status} — Return cluster summary status</li>
 * </ul>
 *
 * <p>Also serves the cluster monitoring UI at {@code /monitoring/cluster}.</p>
 */
@Controller
public class ClusterController {

    private final ClusterService clusterService;
    private final ExecutionEngine executionEngine;

    public ClusterController(ClusterService clusterService, ExecutionEngine executionEngine) {
        this.clusterService = clusterService;
        this.executionEngine = executionEngine;
    }

    // ── Cluster API Endpoints (used by peer nodes) ──────────────────────────

    /**
     * Receive a heartbeat from a peer node. Returns this node's info.
     */
    @PostMapping("/api/v1/cluster/heartbeat")
    @ResponseBody
    public ResponseEntity<ClusterNode> receiveHeartbeat(@RequestBody ClusterNode peerNode) {
        clusterService.receiveHeartbeat(peerNode);
        // Update self metrics before responding
        clusterService.updateSelfMetrics(
                executionEngine.getRecentStates().size(),
                clusterService.getTotalRequestsReceived());
        return ResponseEntity.ok(clusterService.getSelf());
    }

    /**
     * Return all known cluster nodes (including self).
     */
    @GetMapping("/api/v1/cluster/nodes")
    @ResponseBody
    public ResponseEntity<List<ClusterNode>> getClusterNodes() {
        return ResponseEntity.ok(clusterService.getAllNodes());
    }

    /**
     * Return cluster summary status.
     */
    @GetMapping("/api/v1/cluster/status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getClusterStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("cluster_enabled", clusterService.isClusterEnabled());
        status.put("mode", clusterService.isClusterEnabled() ? "CLUSTER" : "STANDALONE");
        status.put("node_id", clusterService.getSelf().getNodeId());
        status.put("role", clusterService.getSelf().getRole().name());
        status.put("cluster_size", clusterService.getClusterSize());
        status.put("status_counts", clusterService.getStatusCounts());
        status.put("requests_forwarded", clusterService.getTotalRequestsForwarded());
        status.put("requests_received", clusterService.getTotalRequestsReceived());
        status.put("nodes", clusterService.getAllNodes());
        return ResponseEntity.ok(status);
    }

    // ── Cluster Monitoring UI ───────────────────────────────────────────────

    /**
     * Cluster monitoring page showing all nodes, their status, and metrics.
     */
    @GetMapping("/monitoring/cluster")
    public String clusterMonitoring(Model model) {
        // Update self metrics
        clusterService.updateSelfMetrics(
                executionEngine.getRecentStates().size(),
                clusterService.getTotalRequestsReceived());

        model.addAttribute("activePage", "cluster");
        model.addAttribute("clusterEnabled", clusterService.isClusterEnabled());
        model.addAttribute("selfNode", clusterService.getSelf());
        model.addAttribute("peers", clusterService.getPeers());
        model.addAttribute("allNodes", clusterService.getAllNodes());
        model.addAttribute("clusterSize", clusterService.getClusterSize());
        model.addAttribute("statusCounts", clusterService.getStatusCounts());
        model.addAttribute("requestsForwarded", clusterService.getTotalRequestsForwarded());
        model.addAttribute("requestsReceived", clusterService.getTotalRequestsReceived());
        return "pages/monitoring/cluster";
    }
}
