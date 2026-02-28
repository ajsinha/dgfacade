/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential.
 */
package com.dgfacade.common.model;

import java.time.Instant;
import java.util.Map;

/**
 * Tracks the lifecycle state of a handler instance.
 * These records are kept in memory (up to 1000, going back 1 hour max)
 * and are viewable via the monitoring UI.
 */
public class HandlerState {

    public enum Phase {
        QUEUED, CONSTRUCTING, EXECUTING, COMPLETED, FAILED, TIMED_OUT, STOPPED
    }

    private String handlerId;
    private String requestId;
    private String requestType;
    private String userId;
    private String handlerClass;
    private String sourceChannel;
    private Phase phase;
    private Instant queuedAt;
    private Instant startedAt;
    private Instant completedAt;
    private long executionDurationMs;
    private boolean success;
    private String errorMessage;
    private String exceptionStackTrace;
    private Map<String, Object> artifacts;
    private Map<String, Object> requestPayload;
    private Map<String, Object> responseData;
    private String requestJson;
    private String responseJson;

    public HandlerState() {}

    public HandlerState(String handlerId, String requestId, String requestType) {
        this.handlerId = handlerId;
        this.requestId = requestId;
        this.requestType = requestType;
        this.phase = Phase.QUEUED;
        this.queuedAt = Instant.now();
    }

    public void markStarted() {
        this.phase = Phase.EXECUTING;
        this.startedAt = Instant.now();
    }

    public void markCompleted(boolean success, String errorMessage) {
        this.phase = success ? Phase.COMPLETED : Phase.FAILED;
        this.success = success;
        this.errorMessage = errorMessage;
        this.completedAt = Instant.now();
        if (startedAt != null) {
            this.executionDurationMs = java.time.Duration.between(startedAt, completedAt).toMillis();
        }
    }

    public void markTimedOut() {
        this.phase = Phase.TIMED_OUT;
        this.success = false;
        this.errorMessage = "Handler exceeded TTL";
        this.completedAt = Instant.now();
        if (startedAt != null) {
            this.executionDurationMs = java.time.Duration.between(startedAt, completedAt).toMillis();
        }
    }

    // --- Getters and Setters ---
    public String getHandlerId() { return handlerId; }
    public void setHandlerId(String handlerId) { this.handlerId = handlerId; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRequestType() { return requestType; }
    public void setRequestType(String requestType) { this.requestType = requestType; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getHandlerClass() { return handlerClass; }
    public void setHandlerClass(String handlerClass) { this.handlerClass = handlerClass; }
    public String getSourceChannel() { return sourceChannel; }
    public void setSourceChannel(String sourceChannel) { this.sourceChannel = sourceChannel; }
    /** Convenience getter for templates — maps Phase enum to display string. */
    public String getStatus() {
        if (phase == null) return "UNKNOWN";
        return switch (phase) {
            case QUEUED -> "QUEUED";
            case CONSTRUCTING -> "CONSTRUCTING";
            case EXECUTING -> "RUNNING";
            case COMPLETED -> "COMPLETED";
            case FAILED -> "FAILED";
            case TIMED_OUT -> "TIMED_OUT";
            case STOPPED -> "STOPPED";
        };
    }
    /** Convenience getter for templates — returns duration as Long (nullable). */
    public Long getDurationMs() {
        return (startedAt != null && completedAt != null) ? executionDurationMs : null;
    }
    public Phase getPhase() { return phase; }
    public void setPhase(Phase phase) { this.phase = phase; }
    public Instant getQueuedAt() { return queuedAt; }
    public void setQueuedAt(Instant queuedAt) { this.queuedAt = queuedAt; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public long getExecutionDurationMs() { return executionDurationMs; }
    public void setExecutionDurationMs(long executionDurationMs) { this.executionDurationMs = executionDurationMs; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getExceptionStackTrace() { return exceptionStackTrace; }
    public void setExceptionStackTrace(String exceptionStackTrace) { this.exceptionStackTrace = exceptionStackTrace; }
    public Map<String, Object> getArtifacts() { return artifacts; }
    public void setArtifacts(Map<String, Object> artifacts) { this.artifacts = artifacts; }
    public Map<String, Object> getRequestPayload() { return requestPayload; }
    public void setRequestPayload(Map<String, Object> requestPayload) { this.requestPayload = requestPayload; }
    public Map<String, Object> getResponseData() { return responseData; }
    public void setResponseData(Map<String, Object> responseData) { this.responseData = responseData; }
    public String getRequestJson() { return requestJson; }
    public void setRequestJson(String requestJson) { this.requestJson = requestJson; }
    public String getResponseJson() { return responseJson; }
    public void setResponseJson(String responseJson) { this.responseJson = responseJson; }
}
