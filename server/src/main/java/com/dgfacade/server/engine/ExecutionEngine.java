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
import com.dgfacade.server.config.HandlerConfigRegistry;
import com.dgfacade.server.metrics.MetricsService;
import com.dgfacade.server.service.UserService;
import com.dgfacade.common.util.CircularBuffer;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    /**
     * Submit a DGRequest for execution. Resolves the user from the API key,
     * finds the handler config, and dispatches to the actor system.
     *
     * @param request the incoming request
     * @return future that completes with the handler's response
     */
    public CompletableFuture<DGResponse> submit(DGRequest request) {
        long submitTimeMs = System.currentTimeMillis();
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
            HandlerState state = new HandlerState(handlerId, request.getRequestId(), request.getRequestType());
            state.setUserId(userId);
            state.setHandlerClass(handlerConfig.getHandlerClass());
            state.setSourceChannel(request.getSourceChannel());
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
                    request, handlerConfig, handlerId, responseFuture);

            actorSystem.tell(new HandlerMessages.WrappedExecute(execReq));

            log.info("Submitted request {} -> handler {} (type={}, user={})",
                    request.getRequestId(), handlerId, request.getRequestType(), userId);

            // Apply TTL as a safeguard on the future itself
            int ttl = handlerConfig.getTtlMinutes();
            return responseFuture.orTimeout(ttl, TimeUnit.MINUTES)
                    .thenApply(response -> {
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
                        state.markTimedOut();
                        // ── Prometheus: record timeout ──
                        if (metricsService != null) {
                            metricsService.recordRequestTimeout(request.getRequestType());
                        }
                        return DGResponse.timeout(request.getRequestId());
                    });

        } catch (Exception e) {
            log.error("Error submitting request {}", request.getRequestId(), e);
            return CompletableFuture.completedFuture(
                    DGResponse.error(request.getRequestId(), "Internal error: " + e.getMessage()));
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
