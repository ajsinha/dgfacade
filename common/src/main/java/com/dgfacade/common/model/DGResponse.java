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

/**
 * Universal response object returned by handlers.
 * Serialized back to the requester via the originating or designated delivery channel.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DGResponse {

    @JsonProperty("request_id")
    private String requestId;

    @JsonProperty("status")
    private Status status;

    @JsonProperty("data")
    private Map<String, Object> data;

    @JsonProperty("error_message")
    private String errorMessage;

    @JsonProperty("handler_id")
    private String handlerId;

    @JsonProperty("execution_time_ms")
    private long executionTimeMs;

    @JsonProperty("timestamp")
    private Instant timestamp;

    @JsonProperty("is_streaming_update")
    private boolean streamingUpdate = false;

    @JsonProperty("sequence_number")
    private int sequenceNumber = 0;

    public enum Status {
        SUCCESS, ERROR, TIMEOUT, PARTIAL, STREAMING_UPDATE, STREAMING_COMPLETE
    }

    public DGResponse() {
        this.timestamp = Instant.now();
    }

    public static DGResponse success(String requestId, Map<String, Object> data) {
        DGResponse r = new DGResponse();
        r.requestId = requestId;
        r.status = Status.SUCCESS;
        r.data = data;
        return r;
    }

    public static DGResponse error(String requestId, String errorMessage) {
        DGResponse r = new DGResponse();
        r.requestId = requestId;
        r.status = Status.ERROR;
        r.errorMessage = errorMessage;
        return r;
    }

    public static DGResponse timeout(String requestId) {
        DGResponse r = new DGResponse();
        r.requestId = requestId;
        r.status = Status.TIMEOUT;
        r.errorMessage = "Request exceeded TTL and was terminated";
        return r;
    }

    public static DGResponse streamingUpdate(String requestId, Map<String, Object> data, int seq) {
        DGResponse r = new DGResponse();
        r.requestId = requestId;
        r.status = Status.STREAMING_UPDATE;
        r.data = data;
        r.streamingUpdate = true;
        r.sequenceNumber = seq;
        return r;
    }

    // --- Getters and Setters ---
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Map<String, Object> getData() { return data; }
    public void setData(Map<String, Object> data) { this.data = data; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getHandlerId() { return handlerId; }
    public void setHandlerId(String handlerId) { this.handlerId = handlerId; }
    public long getExecutionTimeMs() { return executionTimeMs; }
    public void setExecutionTimeMs(long executionTimeMs) { this.executionTimeMs = executionTimeMs; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public boolean isStreamingUpdate() { return streamingUpdate; }
    public void setStreamingUpdate(boolean streamingUpdate) { this.streamingUpdate = streamingUpdate; }
    public int getSequenceNumber() { return sequenceNumber; }
    public void setSequenceNumber(int sequenceNumber) { this.sequenceNumber = sequenceNumber; }
}
