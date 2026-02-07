/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, modification, or use is strictly prohibited without
 * explicit written permission from the copyright holder.
 * Patent Pending.
 */
package com.dgfacade.common.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.*;

/**
 * Represents an active streaming handler session.
 * Supports multi-channel fan-out: a single session can publish data
 * to any combination of WEBSOCKET, KAFKA, and ACTIVEMQ simultaneously.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StreamingSession {

    @JsonProperty("sessionId")
    private String sessionId;

    @JsonProperty("requestId")
    private String requestId;

    @JsonProperty("handlerType")
    private String handlerType;

    /**
     * Set of channels this session publishes to.
     * Can contain one or more of: WEBSOCKET, KAFKA, ACTIVEMQ.
     */
    @JsonProperty("responseChannels")
    private Set<ResponseChannel> responseChannels = new LinkedHashSet<>();

    /**
     * Topic/destination used for Kafka and/or ActiveMQ publishing.
     * WebSocket always uses /topic/stream/{sessionId}.
     */
    @JsonProperty("responseTopic")
    private String responseTopic;

    @JsonProperty("ttlMinutes")
    private long ttlMinutes;

    @JsonProperty("status")
    private HandlerStatus status;

    @JsonProperty("startedAt")
    private Instant startedAt;

    @JsonProperty("expiresAt")
    private Instant expiresAt;

    @JsonProperty("messagesPublished")
    private long messagesPublished;

    @JsonProperty("apiKey")
    private String apiKey;

    public StreamingSession() {
        this.startedAt = Instant.now();
    }

    public StreamingSession(String sessionId, String requestId, String handlerType,
                            Set<ResponseChannel> responseChannels, String responseTopic,
                            long ttlMinutes, String apiKey) {
        this();
        this.sessionId = sessionId;
        this.requestId = requestId;
        this.handlerType = handlerType;
        this.responseChannels = responseChannels != null ? responseChannels : new LinkedHashSet<>();
        this.responseTopic = responseTopic;
        this.ttlMinutes = ttlMinutes;
        this.expiresAt = startedAt.plusSeconds(ttlMinutes * 60);
        this.status = HandlerStatus.STARTING;
        this.apiKey = apiKey;
    }

    /** Convenience: check if this session publishes to a specific channel. */
    public boolean publishesTo(ResponseChannel channel) {
        return responseChannels != null && responseChannels.contains(channel);
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public void incrementMessages() {
        this.messagesPublished++;
    }

    // Getters and setters
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getHandlerType() { return handlerType; }
    public void setHandlerType(String handlerType) { this.handlerType = handlerType; }
    public Set<ResponseChannel> getResponseChannels() { return responseChannels; }
    public void setResponseChannels(Set<ResponseChannel> responseChannels) { this.responseChannels = responseChannels; }
    public String getResponseTopic() { return responseTopic; }
    public void setResponseTopic(String responseTopic) { this.responseTopic = responseTopic; }
    public long getTtlMinutes() { return ttlMinutes; }
    public void setTtlMinutes(long ttlMinutes) { this.ttlMinutes = ttlMinutes; }
    public HandlerStatus getStatus() { return status; }
    public void setStatus(HandlerStatus status) { this.status = status; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public long getMessagesPublished() { return messagesPublished; }
    public void setMessagesPublished(long messagesPublished) { this.messagesPublished = messagesPublished; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    @Override
    public String toString() {
        return "StreamingSession{sessionId='" + sessionId + "', handlerType='" + handlerType +
               "', channels=" + responseChannels + ", ttl=" + ttlMinutes + "m, msgs=" + messagesPublished + "}";
    }
}
