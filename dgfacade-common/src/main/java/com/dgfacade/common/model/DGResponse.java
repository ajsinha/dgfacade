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
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DGResponse {

    @JsonProperty("requestId")
    private String requestId;

    @JsonProperty("status")
    private ResponseStatus status;

    @JsonProperty("message")
    private String message;

    @JsonProperty("result")
    private Map<String, Object> result;

    @JsonProperty("handlerType")
    private String handlerType;

    @JsonProperty("timestamp")
    private Instant timestamp;

    @JsonProperty("executionTimeMs")
    private long executionTimeMs;

    public DGResponse() {
        this.timestamp = Instant.now();
    }

    public static DGResponse success(String requestId, String handlerType, Map<String, Object> result) {
        DGResponse resp = new DGResponse();
        resp.requestId = requestId;
        resp.status = ResponseStatus.SUCCESS;
        resp.handlerType = handlerType;
        resp.result = result;
        resp.message = "Request processed successfully";
        return resp;
    }

    public static DGResponse error(String requestId, String message) {
        DGResponse resp = new DGResponse();
        resp.requestId = requestId;
        resp.status = ResponseStatus.ERROR;
        resp.message = message;
        return resp;
    }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public ResponseStatus getStatus() { return status; }
    public void setStatus(ResponseStatus status) { this.status = status; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Map<String, Object> getResult() { return result; }
    public void setResult(Map<String, Object> result) { this.result = result; }
    public String getHandlerType() { return handlerType; }
    public void setHandlerType(String handlerType) { this.handlerType = handlerType; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public long getExecutionTimeMs() { return executionTimeMs; }
    public void setExecutionTimeMs(long executionTimeMs) { this.executionTimeMs = executionTimeMs; }
}
