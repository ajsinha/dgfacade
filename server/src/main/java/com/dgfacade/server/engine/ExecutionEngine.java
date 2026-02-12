/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential. Patent Pending.
 */
package com.dgfacade.server.engine;

import com.dgfacade.common.exception.*;
import com.dgfacade.common.model.*;
import com.dgfacade.server.actor.HandlerActor;
import com.dgfacade.server.actor.HandlerMessages;
import com.dgfacade.server.actor.HandlerSupervisor;
import com.dgfacade.server.channel.ChannelAccessor;
import com.dgfacade.server.cluster.ClusterService;
import com.dgfacade.server.config.HandlerConfigRegistry;
import com.dgfacade.server.metrics.MetricsService;
import com.dgfacade.server.service.UserService;
import com.dgfacade.common.util.CircularBuffer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Core execution engine that bridges incoming DGRequests with the Pekko actor system.
 *
 * Flow:
 *   1. Validate API key and resolve user
 *   2. Lookup handler config for (user, request_type)
 *   3. Submit to HandlerSupervisor actor for execution
 *   4. Return CompletableFuture<DGResponse> to the caller
 *
 * This design supports millions of concurrent handlers with Pekko's lightweight actors.
 */
public class ExecutionEngine {

    private static final Logger log = LoggerFactory.getLogger(ExecutionEngine.class);

    private final ActorSystem<HandlerMessages.SupervisorCommand> actorSystem;
    private final HandlerConfigRegistry configRegistry;
    private final UserService userService;
    private final CircularBuffer<HandlerState> recentStates;
    private volatile MetricsService metricsService;
    private volatile ChannelAccessor channelAccessor;
    private volatile ClusterService clusterService;
    private volatile HttpClient forwardingClient;
    private static final ObjectMapper forwardMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    public ExecutionEngine(HandlerConfigRegistry configRegistry, UserService userService) {
        this.configRegistry = configRegistry;
        this.userService = userService;
        this.actorSystem = ActorSystem.create(HandlerSupervisor.create(), "dgfacade-engine");
        this.recentStates = new CircularBuffer<>(1000, Duration.ofHours(1));
        log.info("ExecutionEngine initialized with Pekko actor system");
    }

    /** Inject MetricsService after construction (avoids circular dependency). */
    public void setMetricsService(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    /** Inject ChannelAccessor for handler pub/sub access. */
    public void setChannelAccessor(ChannelAccessor channelAccessor) {
        this.channelAccessor = channelAccessor;
        log.info("ExecutionEngine: ChannelAccessor injected — handlers can now access Input/Output Channels");
    }

    /** Inject ClusterService for distributed execution. */
    public void setClusterService(ClusterService clusterService) {
        this.clusterService = clusterService;
        if (clusterService != null && clusterService.isClusterEnabled()) {
            this.forwardingClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            log.info("ExecutionEngine: ClusterService injected — distributed execution enabled");
        } else {
            log.info("ExecutionEngine: Standalone mode — all execution is local");
        }
    }

    /**
     * Submit a DGRequest for execution. Resolves the user from the API key,
     * finds the handler config, and dispatches to the actor system.
     * In cluster mode, may forward to a peer node for execution.
     *
     * @param request the incoming request
     * @return future that completes with the handler's response
     */
    public CompletableFuture<DGResponse> submit(DGRequest request) {
        // Cluster forwarding: if cluster is enabled and this is a GATEWAY node,
        // try to forward non-chain requests to an EXECUTOR peer.
        // Chain requests always execute locally for affinity.
        if (clusterService != null && clusterService.isClusterEnabled() &&
            clusterService.getSelf().getRole() == ClusterNode.Role.GATEWAY &&
            !"CHAIN".equalsIgnoreCase(request.getRequestType())) {

            ClusterNode target = clusterService.selectExecutorNode();
            if (target != null) {
                return forwardToNode(target, request);
            }
            // No executor available, fall through to local execution
        }
        return submitLocal(request);
    }

    /**
     * Execute a request locally on this node. Always used in standalone mode,
     * and used as fallback when no cluster peers are available.
     */
    public CompletableFuture<DGResponse> submitLocal(DGRequest request) {
        long submitTimeMs = System.currentTimeMillis();
        HandlerState state = null;
        try {
            // 1. Validate API key and resolve user
            String userId = userService.resolveUserFromApiKey(request.getApiKey());
            if (userId == null) {
                return CompletableFuture.completedFuture(
                    DGResponse.error(request.getRequestId(), "Invalid or disabled API key"));
            }
            request.setResolvedUserId(userId);

            // 2. Lookup handler config
            Optional<HandlerConfig> handlerConfigOpt = configRegistry.findHandler(userId, request.getRequestType());
            if (handlerConfigOpt.isEmpty()) {
                return CompletableFuture.completedFuture(
                    DGResponse.error(request.getRequestId(),
                        "No handler found for request_type='" + request.getRequestType() + "'"));
            }

            HandlerConfig handlerConfig = handlerConfigOpt.get();
            String handlerId = "hdl-" + UUID.randomUUID().toString().substring(0, 12);

            // Track state
            state = new HandlerState(handlerId, request.getRequestId(), request.getRequestType());
            state.setUserId(userId);
            state.setHandlerClass(handlerConfig.getHandlerClass());
            state.setSourceChannel(request.getSourceChannel());
            state.setRequestPayload(request.getPayload());
            // Build full request JSON for the detail view
            try {
                Map<String, Object> fullReq = new java.util.LinkedHashMap<>();
                fullReq.put("api_key", request.getApiKey());
                fullReq.put("request_type", request.getRequestType());
                fullReq.put("request_id", request.getRequestId());
                fullReq.put("payload", request.getPayload());
                if (request.getDeliveryDestination() != null) fullReq.put("delivery_destination", request.getDeliveryDestination());
                if (request.getTtlMinutes() != 30) fullReq.put("ttl_minutes", request.getTtlMinutes());
                state.setRequestJson(new com.fasterxml.jackson.databind.ObjectMapper()
                        .writerWithDefaultPrettyPrinter().writeValueAsString(fullReq));
            } catch (Exception ignored) {}
            recentStates.add(state);

            // ── Prometheus: record request start ──
            if (metricsService != null) {
                metricsService.recordRequestStart(request.getRequestType(), userId, request.getSourceChannel());
                if (request.getPayload() != null) {
                    metricsService.recordPayloadSize(request.getRequestType(), request.getPayload().toString().length());
                }
            }

            // 3. Submit to actor system
            CompletableFuture<DGResponse> responseFuture = new CompletableFuture<>();
            HandlerMessages.ExecuteRequest execReq = new HandlerMessages.ExecuteRequest(
                    request, handlerConfig, handlerId, responseFuture, state, channelAccessor);

            actorSystem.tell(new HandlerMessages.WrappedExecute(execReq));

            log.info("Submitted request {} -> handler {} (type={}, user={})",
                    request.getRequestId(), handlerId, request.getRequestType(), userId);

            // Apply TTL as a safeguard on the future itself
            int ttl = handlerConfig.getTtlMinutes();
            final HandlerState stateRef = state;
            return responseFuture.orTimeout(ttl, TimeUnit.MINUTES)
                    .thenApply(response -> {
                        // Store response data in state for the detail view
                        stateRef.setResponseData(response.getData());
                        try {
                            stateRef.setResponseJson(new com.fasterxml.jackson.databind.ObjectMapper()
                                    .writerWithDefaultPrettyPrinter().writeValueAsString(response));
                        } catch (Exception ignored) {}
                        // ── Prometheus: record completion ──
                        long durationMs = System.currentTimeMillis() - submitTimeMs;
                        if (metricsService != null) {
                            if (response.getStatus() == DGResponse.Status.SUCCESS) {
                                metricsService.recordRequestSuccess(
                                        request.getRequestType(), userId, request.getSourceChannel(),
                                        handlerConfig.getHandlerClass(), durationMs);
                            } else {
                                metricsService.recordRequestError(
                                        request.getRequestType(), userId, request.getSourceChannel(),
                                        handlerConfig.getHandlerClass(),
                                        response.getStatus() != null ? response.getStatus().name() : "UNKNOWN",
                                        durationMs);
                            }
                        }
                        return response;
                    })
                    .exceptionally(ex -> {
                        stateRef.markTimedOut();
                        DGResponse timeoutResp = DGResponse.timeout(request.getRequestId());
                        // Capture response JSON even on timeout/error for handler detail view
                        try {
                            stateRef.setResponseJson(new com.fasterxml.jackson.databind.ObjectMapper()
                                    .writerWithDefaultPrettyPrinter().writeValueAsString(timeoutResp));
                        } catch (Exception ignored) {}
                        // ── Prometheus: record timeout ──
                        if (metricsService != null) {
                            metricsService.recordRequestTimeout(request.getRequestType());
                        }
                        return timeoutResp;
                    });

        } catch (Exception e) {
            log.error("Error submitting request {}", request.getRequestId(), e);
            DGResponse errorResp = DGResponse.error(request.getRequestId(), "Internal error: " + e.getMessage());
            // Capture response JSON for handler detail view
            if (state != null) {
                try {
                    state.setResponseJson(new com.fasterxml.jackson.databind.ObjectMapper()
                            .writerWithDefaultPrettyPrinter().writeValueAsString(errorResp));
                } catch (Exception ignored) {}
            }
            return CompletableFuture.completedFuture(errorResp);
        }
    }

    /**
     * Forward a request to a remote cluster node for execution.
     */
    private CompletableFuture<DGResponse> forwardToNode(ClusterNode target, DGRequest request) {
        try {
            String json = forwardMapper.writeValueAsString(Map.of(
                    "api_key", request.getApiKey() != null ? request.getApiKey() : "",
                    "request_type", request.getRequestType(),
                    "request_id", request.getRequestId(),
                    "payload", request.getPayload() != null ? request.getPayload() : Map.of(),
                    "ttl_minutes", request.getTtlMinutes()
            ));

            HttpRequest httpReq = HttpRequest.newBuilder()
                    .uri(URI.create(target.getBaseUrl() + "/api/v1/request"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMinutes(request.getTtlMinutes()))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            log.info("Forwarding request {} to cluster node {} ({}:{})",
                    request.getRequestId(), target.getNodeId(), target.getHost(), target.getPort());

            if (clusterService != null) clusterService.incrementForwarded();

            return forwardingClient.sendAsync(httpReq, HttpResponse.BodyHandlers.ofString())
                    .thenApply(resp -> {
                        try {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> body = forwardMapper.readValue(resp.body(), Map.class);
                            DGResponse dgResp = new DGResponse();
                            dgResp.setRequestId(request.getRequestId());
                            dgResp.setStatus(DGResponse.Status.valueOf(
                                    String.valueOf(body.getOrDefault("status", "SUCCESS"))));
                            if (body.get("data") instanceof Map<?, ?> data) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> dataMap = (Map<String, Object>) data;
                                dgResp.setData(dataMap);
                            }
                            dgResp.setErrorMessage(String.valueOf(body.getOrDefault("error_message", "")));
                            // Tag response with forwarding metadata
                            if (dgResp.getData() == null) dgResp.setData(new LinkedHashMap<>());
                            dgResp.getData().put("_forwarded_to", target.getNodeId());
                            dgResp.getData().put("_forwarded_host", target.getHost() + ":" + target.getPort());
                            return dgResp;
                        } catch (Exception e) {
                            return DGResponse.error(request.getRequestId(),
                                    "Failed to parse forwarded response: " + e.getMessage());
                        }
                    })
                    .exceptionally(ex -> {
                        log.warn("Forward to {} failed, executing locally: {}",
                                target.getNodeId(), ex.getMessage());
                        // Fallback to local execution
                        try { return submitLocal(request).get(30, TimeUnit.SECONDS); }
                        catch (Exception e) { return DGResponse.error(request.getRequestId(),
                                "Forward failed and local fallback timed out"); }
                    });

        } catch (Exception e) {
            log.error("Failed to forward request to {}: {}", target.getNodeId(), e.getMessage());
            return submitLocal(request);
        }
    }

    public List<HandlerState> getRecentStates() {
        return recentStates.getAll();
    }

    public Set<String> getRegisteredRequestTypes() {
        return configRegistry.getAllRequestTypes();
    }

    public void reloadConfigs() {
        configRegistry.reload();
    }

    public void shutdown() {
        actorSystem.terminate();
    }
}
