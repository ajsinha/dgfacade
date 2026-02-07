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
import java.util.List;
import java.util.Map;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DGRequest {

    @JsonProperty("requestId")
    private String requestId;

    @JsonProperty("apiKey")
    private String apiKey;

    @JsonProperty("requestType")
    private String requestType;

    @JsonProperty("payload")
    private Map<String, Object> payload;

    @JsonProperty("source")
    private RequestSource source;

    @JsonProperty("timestamp")
    private Instant timestamp;

    @JsonProperty("correlationId")
    private String correlationId;

    @JsonProperty("streaming")
    private boolean streaming = false;

    /**
     * Single channel (backward-compatible). If set and responseChannels is empty,
     * this is used as the sole channel.
     */
    @JsonProperty("responseChannel")
    private ResponseChannel responseChannel;

    /**
     * Multiple channels for multi-channel fan-out.
     * Example: ["WEBSOCKET","KAFKA"] publishes every tick to both WebSocket AND Kafka.
     * Takes precedence over responseChannel if non-empty.
     */
    @JsonProperty("responseChannels")
    private List<ResponseChannel> responseChannels;

    @JsonProperty("responseTopic")
    private String responseTopic;

    @JsonProperty("ttlMinutes")
    private Long ttlMinutes;

    public DGRequest() {
        this.requestId = UUID.randomUUID().toString();
        this.timestamp = Instant.now();
    }

    public DGRequest(String apiKey, String requestType, Map<String, Object> payload) {
        this();
        this.apiKey = apiKey;
        this.requestType = requestType;
        this.payload = payload;
    }

    /**
     * Returns the effective list of response channels.
     * Prefers responseChannels (multi) over responseChannel (single).
     */
    public List<ResponseChannel> getEffectiveResponseChannels() {
        if (responseChannels != null && !responseChannels.isEmpty()) {
            return responseChannels;
        }
        if (responseChannel != null) {
            return List.of(responseChannel);
        }
        return List.of();
    }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getRequestType() { return requestType; }
    public void setRequestType(String requestType) { this.requestType = requestType; }
    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(Map<String, Object> payload) { this.payload = payload; }
    public RequestSource getSource() { return source; }
    public void setSource(RequestSource source) { this.source = source; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public boolean isStreaming() { return streaming; }
    public void setStreaming(boolean streaming) { this.streaming = streaming; }
    public ResponseChannel getResponseChannel() { return responseChannel; }
    public void setResponseChannel(ResponseChannel responseChannel) { this.responseChannel = responseChannel; }
    public List<ResponseChannel> getResponseChannels() { return responseChannels; }
    public void setResponseChannels(List<ResponseChannel> responseChannels) { this.responseChannels = responseChannels; }
    public String getResponseTopic() { return responseTopic; }
    public void setResponseTopic(String responseTopic) { this.responseTopic = responseTopic; }
    public Long getTtlMinutes() { return ttlMinutes; }
    public void setTtlMinutes(Long ttlMinutes) { this.ttlMinutes = ttlMinutes; }

    @Override
    public String toString() {
        return "DGRequest{requestId='" + requestId + "', requestType='" + requestType +
               "', source=" + source + ", timestamp=" + timestamp + "}";
    }
}
