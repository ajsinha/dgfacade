/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential.
 */
package com.dgfacade.common.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

/**
 * API Key descriptor binding a key to a user with optional restrictions.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiKeyInfo {

    @JsonProperty("key")
    private String key;

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("description")
    private String description;

    @JsonProperty("enabled")
    private boolean enabled = true;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("allowed_request_types")
    private List<String> allowedRequestTypes;

    @JsonProperty("rate_limit_per_minute")
    private int rateLimitPerMinute = 0; // 0 = unlimited

    public ApiKeyInfo() { this.createdAt = Instant.now(); }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public List<String> getAllowedRequestTypes() { return allowedRequestTypes; }
    public void setAllowedRequestTypes(List<String> allowedRequestTypes) { this.allowedRequestTypes = allowedRequestTypes; }
    public int getRateLimitPerMinute() { return rateLimitPerMinute; }
    public void setRateLimitPerMinute(int rateLimitPerMinute) { this.rateLimitPerMinute = rateLimitPerMinute; }
}
