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
 * Universal request object for DGFacade.
 * Every request — regardless of channel (REST, WebSocket, Kafka, ActiveMQ, FileSystem) —
 * is normalized into this canonical form before handler dispatch.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DGRequest {

    @JsonProperty("request_id")
    private String requestId;

    @JsonProperty("request_type")
    private String requestType;

    @JsonProperty("api_key")
    private String apiKey;

    @JsonProperty("payload")
    private Map<String, Object> payload;

    @JsonProperty("delivery_destination")
    private String deliveryDestination;

    @JsonProperty("ttl_minutes")
    private int ttlMinutes = 30;

    // Internal fields set by the server
    private String resolvedUserId;
    private String sourceChannel;
    private Instant receivedAt;
    private Instant executionStartedAt;

    public DGRequest() {
        this.receivedAt = Instant.now();
    }

    public DGRequest(String requestType, String apiKey, Map<String, Object> payload) {
        this();
        this.requestId = UUID.randomUUID().toString();
        this.requestType = requestType;
        this.apiKey = apiKey;
        this.payload = payload;
    }

    // --- Getters and Setters ---
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getRequestType() { return requestType; }
    public void setRequestType(String requestType) { this.requestType = requestType; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(Map<String, Object> payload) { this.payload = payload; }

    public String getDeliveryDestination() { return deliveryDestination; }
    public void setDeliveryDestination(String deliveryDestination) { this.deliveryDestination = deliveryDestination; }

    public int getTtlMinutes() { return ttlMinutes; }
    public void setTtlMinutes(int ttlMinutes) { this.ttlMinutes = ttlMinutes; }

    public String getResolvedUserId() { return resolvedUserId; }
    public void setResolvedUserId(String resolvedUserId) { this.resolvedUserId = resolvedUserId; }

    public String getSourceChannel() { return sourceChannel; }
    public void setSourceChannel(String sourceChannel) { this.sourceChannel = sourceChannel; }

    public Instant getReceivedAt() { return receivedAt; }
    public void setReceivedAt(Instant receivedAt) { this.receivedAt = receivedAt; }

    public Instant getExecutionStartedAt() { return executionStartedAt; }
    public void setExecutionStartedAt(Instant executionStartedAt) { this.executionStartedAt = executionStartedAt; }

    @Override
    public String toString() {
        return "DGRequest{requestId='" + requestId + "', requestType='" + requestType +
               "', apiKey='" + (apiKey != null ? apiKey.substring(0, Math.min(8, apiKey.length())) + "..." : "null") +
               "', sourceChannel='" + sourceChannel + "'}";
    }
}
