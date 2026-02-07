/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, modification, or use is strictly prohibited without
 * explicit written permission from the copyright holder.
 * Patent Pending.
 */
package com.dgfacade.messaging.composite;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Configuration for the {@link CompositeMessageListener}.
 *
 * Holds connection details for Kafka and/or ActiveMQ. At least one
 * broker must be configured. The composite listener will only create
 * internal subscribers for brokers that are enabled and configured.
 *
 * <p>Usage with builder pattern:
 * <pre>{@code
 *   CompositeListenerConfig config = CompositeListenerConfig.builder()
 *       .kafkaEnabled(true)
 *       .kafkaBootstrapServers("broker1:9092,broker2:9092")
 *       .kafkaGroupId("my-app")
 *       .activeMqEnabled(true)
 *       .activeMqBrokerUrl("tcp://localhost:61616")
 *       .activeMqUsername("admin")
 *       .activeMqPassword("admin")
 *       .build();
 * }</pre>
 *
 * <p>Or from Spring properties:
 * <pre>{@code
 *   CompositeListenerConfig config = CompositeListenerConfig.fromProperties(properties);
 * }</pre>
 *
 * @since 1.1.0
 */
public class CompositeListenerConfig {

    // --- Kafka configuration ---
    private boolean kafkaEnabled = false;
    private String kafkaBootstrapServers = "localhost:9092";
    private String kafkaGroupId = "dgfacade-composite";
    private String kafkaAutoOffsetReset = "latest";
    private int kafkaConcurrency = 1;
    private Map<String, Object> kafkaExtraProperties = new HashMap<>();

    // --- ActiveMQ configuration ---
    private boolean activeMqEnabled = false;
    private String activeMqBrokerUrl = "tcp://localhost:61616";
    private String activeMqUsername;
    private String activeMqPassword;
    private int activeMqConcurrency = 1;
    private boolean activeMqPubSubDomain = true;  // true = topic, false = queue
    private Map<String, Object> activeMqExtraProperties = new HashMap<>();

    // --- General ---
    private String listenerName = "dgfacade-composite-listener";

    public CompositeListenerConfig() {}

    // ========== Builder ==========

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final CompositeListenerConfig config = new CompositeListenerConfig();

        public Builder kafkaEnabled(boolean enabled) {
            config.kafkaEnabled = enabled; return this;
        }
        public Builder kafkaBootstrapServers(String servers) {
            config.kafkaBootstrapServers = servers; return this;
        }
        public Builder kafkaGroupId(String groupId) {
            config.kafkaGroupId = groupId; return this;
        }
        public Builder kafkaAutoOffsetReset(String reset) {
            config.kafkaAutoOffsetReset = reset; return this;
        }
        public Builder kafkaConcurrency(int concurrency) {
            config.kafkaConcurrency = concurrency; return this;
        }
        public Builder kafkaExtraProperty(String key, Object value) {
            config.kafkaExtraProperties.put(key, value); return this;
        }
        public Builder activeMqEnabled(boolean enabled) {
            config.activeMqEnabled = enabled; return this;
        }
        public Builder activeMqBrokerUrl(String url) {
            config.activeMqBrokerUrl = url; return this;
        }
        public Builder activeMqUsername(String username) {
            config.activeMqUsername = username; return this;
        }
        public Builder activeMqPassword(String password) {
            config.activeMqPassword = password; return this;
        }
        public Builder activeMqConcurrency(int concurrency) {
            config.activeMqConcurrency = concurrency; return this;
        }
        public Builder activeMqPubSubDomain(boolean pubSub) {
            config.activeMqPubSubDomain = pubSub; return this;
        }
        public Builder activeMqExtraProperty(String key, Object value) {
            config.activeMqExtraProperties.put(key, value); return this;
        }
        public Builder listenerName(String name) {
            config.listenerName = name; return this;
        }

        public CompositeListenerConfig build() {
            if (!config.kafkaEnabled && !config.activeMqEnabled) {
                throw new IllegalStateException(
                        "At least one broker (Kafka or ActiveMQ) must be enabled");
            }
            return config;
        }
    }

    // ========== Factory from Spring-style properties ==========

    /**
     * Build configuration from a Properties object or Spring Environment.
     * Recognized keys:
     *   dgfacade.composite.kafka.enabled, dgfacade.composite.kafka.bootstrap-servers,
     *   dgfacade.composite.kafka.group-id, dgfacade.composite.kafka.auto-offset-reset,
     *   dgfacade.composite.kafka.concurrency,
     *   dgfacade.composite.activemq.enabled, dgfacade.composite.activemq.broker-url,
     *   dgfacade.composite.activemq.username, dgfacade.composite.activemq.password,
     *   dgfacade.composite.activemq.concurrency, dgfacade.composite.activemq.pub-sub-domain,
     *   dgfacade.composite.name
     */
    public static CompositeListenerConfig fromProperties(Properties props) {
        Builder b = builder();

        boolean kafkaEnabled = Boolean.parseBoolean(
                props.getProperty("dgfacade.composite.kafka.enabled", "false"));
        boolean amqEnabled = Boolean.parseBoolean(
                props.getProperty("dgfacade.composite.activemq.enabled", "false"));

        if (kafkaEnabled) {
            b.kafkaEnabled(true)
             .kafkaBootstrapServers(
                     props.getProperty("dgfacade.composite.kafka.bootstrap-servers", "localhost:9092"))
             .kafkaGroupId(
                     props.getProperty("dgfacade.composite.kafka.group-id", "dgfacade-composite"))
             .kafkaAutoOffsetReset(
                     props.getProperty("dgfacade.composite.kafka.auto-offset-reset", "latest"))
             .kafkaConcurrency(Integer.parseInt(
                     props.getProperty("dgfacade.composite.kafka.concurrency", "1")));
        }

        if (amqEnabled) {
            b.activeMqEnabled(true)
             .activeMqBrokerUrl(
                     props.getProperty("dgfacade.composite.activemq.broker-url", "tcp://localhost:61616"))
             .activeMqUsername(
                     props.getProperty("dgfacade.composite.activemq.username"))
             .activeMqPassword(
                     props.getProperty("dgfacade.composite.activemq.password"))
             .activeMqConcurrency(Integer.parseInt(
                     props.getProperty("dgfacade.composite.activemq.concurrency", "1")))
             .activeMqPubSubDomain(Boolean.parseBoolean(
                     props.getProperty("dgfacade.composite.activemq.pub-sub-domain", "true")));
        }

        b.listenerName(props.getProperty("dgfacade.composite.name", "dgfacade-composite-listener"));
        return b.build();
    }

    // ========== Getters ==========

    public boolean isKafkaEnabled() { return kafkaEnabled; }
    public String getKafkaBootstrapServers() { return kafkaBootstrapServers; }
    public String getKafkaGroupId() { return kafkaGroupId; }
    public String getKafkaAutoOffsetReset() { return kafkaAutoOffsetReset; }
    public int getKafkaConcurrency() { return kafkaConcurrency; }
    public Map<String, Object> getKafkaExtraProperties() { return kafkaExtraProperties; }

    public boolean isActiveMqEnabled() { return activeMqEnabled; }
    public String getActiveMqBrokerUrl() { return activeMqBrokerUrl; }
    public String getActiveMqUsername() { return activeMqUsername; }
    public String getActiveMqPassword() { return activeMqPassword; }
    public int getActiveMqConcurrency() { return activeMqConcurrency; }
    public boolean isActiveMqPubSubDomain() { return activeMqPubSubDomain; }
    public Map<String, Object> getActiveMqExtraProperties() { return activeMqExtraProperties; }

    public String getListenerName() { return listenerName; }

    @Override
    public String toString() {
        return "CompositeListenerConfig{kafka=" + kafkaEnabled +
               (kafkaEnabled ? "(servers=" + kafkaBootstrapServers + ", group=" + kafkaGroupId + ")" : "") +
               ", activeMq=" + activeMqEnabled +
               (activeMqEnabled ? "(broker=" + activeMqBrokerUrl + ")" : "") + "}";
    }
}
