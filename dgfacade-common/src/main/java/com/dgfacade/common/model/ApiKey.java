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
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiKey {
    private String key;
    private String owner;
    private String description;
    private List<String> allowedRequestTypes;
    private boolean active;

    public ApiKey() { this.active = true; }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<String> getAllowedRequestTypes() { return allowedRequestTypes; }
    public void setAllowedRequestTypes(List<String> allowedRequestTypes) { this.allowedRequestTypes = allowedRequestTypes; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
