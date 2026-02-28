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
 * Configuration for a pub/sub channel (publisher or subscriber).
 * Each channel has a unique name and is bound to a broker.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChannelConfig {

    public enum ChannelDirection { PUBLISH, SUBSCRIBE }
    public enum DeliveryMode { EVENT_BASED, SCHEDULED }

    @JsonProperty("channel_name")
    private String channelName;

    @JsonProperty("direction")
    private ChannelDirection direction;

    @JsonProperty("broker_id")
    private String brokerId;

    @JsonProperty("destination")
    private String destination;

    @JsonProperty("delivery_mode")
    private DeliveryMode deliveryMode;

    @JsonProperty("schedule_cron")
    private String scheduleCron;

    @JsonProperty("schedule_interval_seconds")
    private int scheduleIntervalSeconds = 60;

    @JsonProperty("backpressure_max_depth")
    private int backpressureMaxDepth = 10000;

    @JsonProperty("batch_size")
    private int batchSize = 100;

    @JsonProperty("enabled")
    private boolean enabled = true;

    @JsonProperty("properties")
    private Map<String, String> properties;

    public ChannelConfig() {}

    public String getChannelName() { return channelName; }
    public void setChannelName(String channelName) { this.channelName = channelName; }
    public ChannelDirection getDirection() { return direction; }
    public void setDirection(ChannelDirection direction) { this.direction = direction; }
    public String getBrokerId() { return brokerId; }
    public void setBrokerId(String brokerId) { this.brokerId = brokerId; }
    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }
    public DeliveryMode getDeliveryMode() { return deliveryMode; }
    public void setDeliveryMode(DeliveryMode deliveryMode) { this.deliveryMode = deliveryMode; }
    public String getScheduleCron() { return scheduleCron; }
    public void setScheduleCron(String scheduleCron) { this.scheduleCron = scheduleCron; }
    public int getScheduleIntervalSeconds() { return scheduleIntervalSeconds; }
    public void setScheduleIntervalSeconds(int scheduleIntervalSeconds) { this.scheduleIntervalSeconds = scheduleIntervalSeconds; }
    public int getBackpressureMaxDepth() { return backpressureMaxDepth; }
    public void setBackpressureMaxDepth(int backpressureMaxDepth) { this.backpressureMaxDepth = backpressureMaxDepth; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Map<String, String> getProperties() { return properties; }
    public void setProperties(Map<String, String> properties) { this.properties = properties; }
}
