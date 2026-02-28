/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential.
 */
package com.dgfacade.common.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Configuration for a handler as specified in handler JSON configuration files.
 * Maps request_type to a handler class with its configuration dictionary.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class HandlerConfig {

    @JsonProperty("request_type")
    private String requestType;

    @JsonProperty("handler_class")
    private String handlerClass;

    @JsonProperty("config")
    private Map<String, Object> config;

    @JsonProperty("ttl_minutes")
    private int ttlMinutes = 30;

    @JsonProperty("description")
    private String description;

    @JsonProperty("enabled")
    private boolean enabled = true;

    @JsonProperty("is_python")
    private boolean isPython = false;

    public HandlerConfig() {}

    public String getRequestType() { return requestType; }
    public void setRequestType(String requestType) { this.requestType = requestType; }
    public String getHandlerClass() { return handlerClass; }
    public void setHandlerClass(String handlerClass) { this.handlerClass = handlerClass; }
    public Map<String, Object> getConfig() { return config; }
    public void setConfig(Map<String, Object> config) { this.config = config; }
    public int getTtlMinutes() { return ttlMinutes; }
    public void setTtlMinutes(int ttlMinutes) { this.ttlMinutes = ttlMinutes; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isPython() { return isPython; }
    public void setPython(boolean python) { isPython = python; }
}
