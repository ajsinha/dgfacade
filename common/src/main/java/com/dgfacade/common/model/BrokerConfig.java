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
 * Configuration for a messaging broker (Kafka, ActiveMQ, FileSystem, SQL).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BrokerConfig {

    @JsonProperty("broker_id")
    private String brokerId;

    @JsonProperty("broker_type")
    private BrokerType brokerType;

    @JsonProperty("display_name")
    private String displayName;

    @JsonProperty("connection_uri")
    private String connectionUri;

    @JsonProperty("enabled")
    private boolean enabled = true;

    @JsonProperty("auto_start")
    private boolean autoStart = false;

    @JsonProperty("reconnect_interval_seconds")
    private int reconnectIntervalSeconds = 60;

    @JsonProperty("properties")
    private Map<String, String> properties;

    public enum BrokerType {
        KAFKA, CONFLUENT_KAFKA, ACTIVEMQ, RABBITMQ, IBMMQ, FILESYSTEM, SQL
    }

    public BrokerConfig() {}

    public String getBrokerId() { return brokerId; }
    public void setBrokerId(String brokerId) { this.brokerId = brokerId; }
    public BrokerType getBrokerType() { return brokerType; }
    public void setBrokerType(BrokerType brokerType) { this.brokerType = brokerType; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getConnectionUri() { return connectionUri; }
    public void setConnectionUri(String connectionUri) { this.connectionUri = connectionUri; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isAutoStart() { return autoStart; }
    public void setAutoStart(boolean autoStart) { this.autoStart = autoStart; }
    public int getReconnectIntervalSeconds() { return reconnectIntervalSeconds; }
    public void setReconnectIntervalSeconds(int reconnectIntervalSeconds) { this.reconnectIntervalSeconds = reconnectIntervalSeconds; }
    public Map<String, String> getProperties() { return properties; }
    public void setProperties(Map<String, String> properties) { this.properties = properties; }
}
