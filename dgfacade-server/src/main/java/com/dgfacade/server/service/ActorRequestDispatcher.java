/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, modification, or use is strictly prohibited without
 * explicit written permission from the copyright holder.
 * Patent Pending.
 */
package com.dgfacade.server.service;

import com.dgfacade.common.config.DGFacadeProperties;
import com.dgfacade.common.handler.DGHandler;
import com.dgfacade.common.handler.HandlerRegistry;
import com.dgfacade.common.handler.StreamingHandler;
import com.dgfacade.common.model.*;
import com.dgfacade.common.security.ApiKeyService;
import com.dgfacade.messaging.RequestDispatcher;
import com.dgfacade.messaging.StreamingPublisher;
import com.dgfacade.server.actor.HandlerActor;
import com.dgfacade.server.actor.HandlerProtocol;
import com.dgfacade.server.actor.StreamingHandlerActor;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Service
public class ActorRequestDispatcher implements RequestDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ActorRequestDispatcher.class);

    private final HandlerRegistry handlerRegistry;
    private final ApiKeyService apiKeyService;
    private final DGFacadeProperties properties;
    private final StreamingSessionManager sessionManager;
    private final StreamingPublisher streamingPublisher;

    private ActorSystem<HandlerProtocol.HandlerCommand> actorSystem;

    public ActorRequestDispatcher(HandlerRegistry handlerRegistry,
                                  ApiKeyService apiKeyService,
                                  DGFacadeProperties properties,
                                  StreamingSessionManager sessionManager,
                                  StreamingPublisher streamingPublisher) {
        this.handlerRegistry = handlerRegistry;
        this.apiKeyService = apiKeyService;
        this.properties = properties;
        this.sessionManager = sessionManager;
        this.streamingPublisher = streamingPublisher;
    }

    @PostConstruct
    public void init() {
        actorSystem = ActorSystem.create(
                HandlerActor.create(handlerRegistry), "dgfacade-actors");
        log.info("DGFacade Actor System initialized");
    }

    @PreDestroy
    public void shutdown() {
        if (actorSystem != null) {
            actorSystem.terminate();
            log.info("DGFacade Actor System terminated");
        }
    }

    @Override
    public CompletableFuture<DGResponse> dispatch(DGRequest request) {
        // Validate API key
        if (!apiKeyService.validate(request.getApiKey(), request.getRequestType())) {
            DGResponse unauthorized = DGResponse.error(request.getRequestId(),
                    "Invalid API key or unauthorized request type");
            unauthorized.setStatus(ResponseStatus.UNAUTHORIZED);
            return CompletableFuture.completedFuture(unauthorized);
        }

        // Check handler exists
        if (!handlerRegistry.hasHandler(request.getRequestType())) {
            DGResponse notFound = DGResponse.error(request.getRequestId(),
                    "No handler registered for request type: " + request.getRequestType());
            notFound.setStatus(ResponseStatus.HANDLER_NOT_FOUND);
            return CompletableFuture.completedFuture(notFound);
        }

        // If request asks for streaming OR handler IS streaming, route to streaming path
        Optional<DGHandler> handler = handlerRegistry.createHandler(request.getRequestType());
        if (handler.isPresent() && handler.get() instanceof StreamingHandler streamingHandler) {
            return dispatchStreaming(request, streamingHandler);
        }

        // One-shot handler — dispatch to actor system
        return dispatchOneShot(request);
    }

    private CompletableFuture<DGResponse> dispatchOneShot(DGRequest request) {
        Duration timeout = Duration.ofSeconds(properties.getActor().getHandlerTimeoutSeconds());
        CompletionStage<HandlerProtocol.HandlerResult> resultStage =
                AskPattern.ask(
                        actorSystem,
                        ref -> new HandlerProtocol.ExecuteRequest(request, ref),
                        timeout,
                        actorSystem.scheduler()
                );

        return resultStage.toCompletableFuture().thenApply(result -> {
            if (result instanceof HandlerProtocol.HandlerSuccess success) {
                return success.response();
            } else if (result instanceof HandlerProtocol.HandlerFailure failure) {
                return DGResponse.error(failure.requestId(), failure.errorMessage());
            }
            return DGResponse.error(request.getRequestId(), "Unknown actor response");
        }).exceptionally(ex -> {
            log.error("Actor dispatch failed for request {}", request.getRequestId(), ex);
            return DGResponse.error(request.getRequestId(), "Dispatch failed: " + ex.getMessage());
        });
    }

    /**
     * Dispatches a streaming handler request. Creates a StreamingSession,
     * spawns a dedicated StreamingHandlerActor, and returns an immediate
     * acknowledgement with the session ID.
     */
    private CompletableFuture<DGResponse> dispatchStreaming(DGRequest request,
                                                            StreamingHandler streamingHandler) {
        DGFacadeProperties.Streaming streamConfig = properties.getStreaming();

        if (!streamConfig.isEnabled()) {
            DGResponse disabled = DGResponse.error(request.getRequestId(),
                    "Streaming handlers are disabled in configuration");
            return CompletableFuture.completedFuture(disabled);
        }

        if (sessionManager.getActiveCount() >= streamConfig.getMaxConcurrentSessions()) {
            DGResponse limited = DGResponse.error(request.getRequestId(),
                    "Maximum concurrent streaming sessions reached (" +
                            streamConfig.getMaxConcurrentSessions() + ")");
            return CompletableFuture.completedFuture(limited);
        }

        // Determine TTL: request override > handler default > system default
        long ttl = streamingHandler.getDefaultTtlMinutes();
        if (request.getTtlMinutes() != null && request.getTtlMinutes() > 0) {
            ttl = Math.min(request.getTtlMinutes(), streamConfig.getMaxTtlMinutes());
        }

        // Determine response channels: request override > handler default > system default
        // Multi-channel: the session can publish to any combination simultaneously
        java.util.Set<ResponseChannel> channels = new java.util.LinkedHashSet<>();
        java.util.List<ResponseChannel> requestChannels = request.getEffectiveResponseChannels();
        if (!requestChannels.isEmpty()) {
            channels.addAll(requestChannels);
        } else {
            channels.addAll(streamingHandler.getDefaultResponseChannels());
        }
        // Fallback to system default if still empty
        if (channels.isEmpty()) {
            for (String ch : streamConfig.getDefaultResponseChannels().split(",")) {
                try { channels.add(ResponseChannel.valueOf(ch.trim())); } catch (Exception ignored) {}
            }
        }

        // Determine response topic (used by Kafka/ActiveMQ channels)
        String responseTopic = request.getResponseTopic();
        if (responseTopic == null || responseTopic.isBlank()) {
            responseTopic = "dgfacade-stream-" + request.getRequestType().toLowerCase();
        }

        // Build streaming session with multi-channel set
        String sessionId = UUID.randomUUID().toString();
        StreamingSession session = new StreamingSession(
                sessionId, request.getRequestId(), request.getRequestType(),
                channels, responseTopic, ttl, request.getApiKey());

        // Spawn a dedicated StreamingHandlerActor
        ActorRef<StreamingHandlerActor.StreamingCommand> streamingActor =
                actorSystem.systemActorOf(
                        StreamingHandlerActor.create(sessionManager),
                        "streaming-" + sessionId.substring(0, 8),
                        org.apache.pekko.actor.typed.Props.empty());

        Duration timeout = Duration.ofSeconds(30);
        CompletionStage<HandlerProtocol.HandlerResult> resultStage =
                AskPattern.ask(
                        streamingActor,
                        (ActorRef<HandlerProtocol.HandlerResult> replyTo) ->
                                new StreamingHandlerActor.StartStreaming(
                                        request, session, streamingHandler,
                                        streamingPublisher, replyTo),
                        timeout,
                        actorSystem.scheduler()
                );

        sessionManager.registerActorRef(sessionId, streamingActor);

        return resultStage.toCompletableFuture().thenApply(result -> {
            if (result instanceof HandlerProtocol.HandlerSuccess success) {
                return success.response();
            } else if (result instanceof HandlerProtocol.HandlerFailure failure) {
                sessionManager.unregister(sessionId);
                return DGResponse.error(failure.requestId(), failure.errorMessage());
            }
            return DGResponse.error(request.getRequestId(), "Unknown response from streaming actor");
        }).exceptionally(ex -> {
            log.error("Streaming dispatch failed for request {}", request.getRequestId(), ex);
            sessionManager.unregister(sessionId);
            return DGResponse.error(request.getRequestId(),
                    "Streaming dispatch failed: " + ex.getMessage());
        });
    }

    /**
     * Stop a streaming session by session ID.
     */
    public CompletableFuture<DGResponse> stopStreamingSession(String sessionId) {
        Optional<StreamingSession> sessionOpt = sessionManager.getSession(sessionId);
        if (sessionOpt.isEmpty()) {
            DGResponse resp = DGResponse.error(sessionId, "Streaming session not found: " + sessionId);
            resp.setStatus(ResponseStatus.HANDLER_NOT_FOUND);
            return CompletableFuture.completedFuture(resp);
        }

        Optional<ActorRef<StreamingHandlerActor.StreamingCommand>> actorRefOpt =
                sessionManager.getActorRef(sessionId);
        if (actorRefOpt.isPresent()) {
            actorRefOpt.get().tell(new StreamingHandlerActor.StopStreaming(sessionId));
        }

        DGResponse resp = new DGResponse();
        resp.setRequestId(sessionOpt.get().getRequestId());
        resp.setStatus(ResponseStatus.SUCCESS);
        resp.setHandlerType(sessionOpt.get().getHandlerType());
        resp.setMessage("Streaming session stop requested: " + sessionId);
        return CompletableFuture.completedFuture(resp);
    }

    public ActorSystem<HandlerProtocol.HandlerCommand> getActorSystem() {
        return actorSystem;
    }
}
