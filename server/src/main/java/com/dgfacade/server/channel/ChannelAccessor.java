/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential.
 */
package com.dgfacade.server.channel;

import com.dgfacade.messaging.config.MessagingFactory;
import com.dgfacade.messaging.core.DataPublisher;
import com.dgfacade.messaging.core.DataSubscriber;
import com.dgfacade.common.model.BrokerConfig;
import com.dgfacade.server.service.BrokerService;
import com.dgfacade.server.service.InputChannelService;
import com.dgfacade.server.service.OutputChannelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides handlers with access to Input and Output channels for real pub/sub messaging.
 *
 * <p>Handlers that need to subscribe to topics or publish messages (e.g. {@code PDCHandler})
 * receive a reference to this accessor via {@link com.dgfacade.server.handler.DGHandler#setChannelAccessor}.
 * The accessor resolves channel configurations, looks up the associated broker, creates
 * the appropriate {@link DataPublisher} or {@link DataSubscriber} via the {@link MessagingFactory},
 * and caches instances for reuse.</p>
 *
 * <h3>Resolution Flow</h3>
 * <pre>
 *   Channel ID  →  Channel Config (JSON)  →  Broker ID  →  Broker Config (JSON)
 *       │                  │                       │                │
 *       │                  │  destinations[]        │  connectionUri │
 *       │                  │  broker: "dev-kafka"   │  brokerType    │
 *       ▼                  ▼                       ▼                ▼
 *   getPublisher("order-notifications")  →  MessagingFactory.createPublisher(brokerConfig)
 *                                             → KafkaDataPublisher.initialize(connectionProps)
 * </pre>
 *
 * <h3>Thread Safety</h3>
 * <p>All caches use ConcurrentHashMap. Publishers and subscribers are created once
 * and reused across handler invocations. Call {@link #shutdown()} during application
 * shutdown to close all cached connections.</p>
 */
public class ChannelAccessor {

    private static final Logger log = LoggerFactory.getLogger(ChannelAccessor.class);

    private final BrokerService brokerService;
    private final InputChannelService inputChannelService;
    private final OutputChannelService outputChannelService;

    private final ConcurrentHashMap<String, DataPublisher> publisherCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DataSubscriber> subscriberCache = new ConcurrentHashMap<>();

    public ChannelAccessor(BrokerService brokerService,
                           InputChannelService inputChannelService,
                           OutputChannelService outputChannelService) {
        this.brokerService = brokerService;
        this.inputChannelService = inputChannelService;
        this.outputChannelService = outputChannelService;
    }

    /**
     * Get a DataPublisher for the given Output Channel ID.
     * The publisher is initialized with the broker connection properties
     * derived from the channel's broker configuration.
     *
     * @param outputChannelId the output channel ID (e.g. "order-notifications")
     * @return a ready-to-use DataPublisher
     * @throws IllegalArgumentException if the channel or its broker is not found
     */
    public DataPublisher getPublisher(String outputChannelId) {
        return publisherCache.computeIfAbsent(outputChannelId, id -> {
            log.info("Creating DataPublisher for output channel '{}'", id);
            Map<String, Object> channelConfig = outputChannelService.getChannel(id);
            if (channelConfig == null) {
                throw new IllegalArgumentException("Output channel not found: " + id);
            }
            ResolvedBroker resolved = resolveBroker(channelConfig, id);
            BrokerConfig brokerConfig = resolved.config();
            DataPublisher publisher = MessagingFactory.createPublisher(brokerConfig);
            publisher.initialize(buildConnectionProps(channelConfig, brokerConfig, resolved.rawMap()));
            log.info("DataPublisher created for output channel '{}' via broker '{}' ({})",
                    id, brokerConfig.getBrokerId(), brokerConfig.getBrokerType());
            return publisher;
        });
    }

    /**
     * Get a DataSubscriber for the given Input Channel ID.
     * The subscriber is initialized with the broker connection properties
     * derived from the channel's broker configuration.
     *
     * @param inputChannelId the input channel ID (e.g. "order-events")
     * @return a ready-to-use DataSubscriber
     * @throws IllegalArgumentException if the channel or its broker is not found
     */
    public DataSubscriber getSubscriber(String inputChannelId) {
        return subscriberCache.computeIfAbsent(inputChannelId, id -> {
            log.info("Creating DataSubscriber for input channel '{}'", id);
            Map<String, Object> channelConfig = inputChannelService.getChannel(id);
            if (channelConfig == null) {
                throw new IllegalArgumentException("Input channel not found: " + id);
            }
            ResolvedBroker resolved = resolveBroker(channelConfig, id);
            BrokerConfig brokerConfig = resolved.config();
            DataSubscriber subscriber = MessagingFactory.createSubscriber(brokerConfig);
            subscriber.initialize(buildConnectionProps(channelConfig, brokerConfig, resolved.rawMap()));
            log.info("DataSubscriber created for input channel '{}' via broker '{}' ({})",
                    id, brokerConfig.getBrokerId(), brokerConfig.getBrokerType());
            return subscriber;
        });
    }

    /**
     * Get the configured destinations for a channel.
     *
     * @param channelId the channel ID
     * @param isOutput  true for output channels, false for input channels
     * @return list of destination names (topics/queues), or empty list if none configured
     */
    @SuppressWarnings("unchecked")
    public List<String> getDestinations(String channelId, boolean isOutput) {
        Map<String, Object> config = isOutput
                ? outputChannelService.getChannel(channelId)
                : inputChannelService.getChannel(channelId);
        if (config == null) return Collections.emptyList();

        Object destinations = config.get("destinations");
        if (destinations instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Object name = map.get("name");
                    if (name != null) result.add(name.toString());
                } else if (item instanceof String s) {
                    result.add(s);
                }
            }
            return result;
        }
        return Collections.emptyList();
    }

    /**
     * Get the raw channel configuration map.
     *
     * @param channelId the channel ID
     * @param isOutput  true for output channels, false for input channels
     * @return the config map, or null if not found
     */
    public Map<String, Object> getChannelConfig(String channelId, boolean isOutput) {
        return isOutput
                ? outputChannelService.getChannel(channelId)
                : inputChannelService.getChannel(channelId);
    }

    /**
     * Close all cached publishers and subscribers. Called during shutdown.
     */
    public void shutdown() {
        log.info("ChannelAccessor shutting down — closing {} publisher(s) and {} subscriber(s)",
                publisherCache.size(), subscriberCache.size());
        publisherCache.forEach((id, pub) -> {
            try { pub.close(); } catch (Exception e) {
                log.warn("Error closing publisher for channel '{}': {}", id, e.getMessage());
            }
        });
        subscriberCache.forEach((id, sub) -> {
            try { sub.close(); } catch (Exception e) {
                log.warn("Error closing subscriber for channel '{}': {}", id, e.getMessage());
            }
        });
        publisherCache.clear();
        subscriberCache.clear();
    }

    // ─── Internal helpers ────────────────────────────────────────────────────

    /** Resolved broker: typed config + raw JSON map for nested block pass-through. */
    private record ResolvedBroker(BrokerConfig config, Map<String, Object> rawMap) {}

    /**
     * Resolve the BrokerConfig from a channel config's "broker" field.
     */
    private ResolvedBroker resolveBroker(Map<String, Object> channelConfig, String channelId) {
        String brokerId = (String) channelConfig.get("broker");
        if (brokerId == null || brokerId.isBlank()) {
            throw new IllegalArgumentException(
                    "Channel '" + channelId + "' does not specify a 'broker' field");
        }
        Map<String, Object> brokerMap = brokerService.getBroker(brokerId);
        if (brokerMap == null) {
            throw new IllegalArgumentException(
                    "Broker '" + brokerId + "' referenced by channel '" + channelId + "' not found");
        }
        // Convert the raw map into a BrokerConfig
        BrokerConfig bc = new BrokerConfig();
        bc.setBrokerId(brokerId);
        String type = String.valueOf(brokerMap.getOrDefault("type",
                brokerMap.getOrDefault("broker_type", "KAFKA")))
                .toUpperCase().replace("-", "_");
        bc.setBrokerType(BrokerConfig.BrokerType.valueOf(type));
        bc.setConnectionUri(String.valueOf(brokerMap.getOrDefault("connection_uri", "")));
        bc.setDisplayName(String.valueOf(brokerMap.getOrDefault("display_name",
                brokerMap.getOrDefault("description", brokerId))));
        bc.setEnabled(Boolean.parseBoolean(
                String.valueOf(brokerMap.getOrDefault("enabled", "true"))));

        // Extract properties from nested "connection" map or top-level "properties"
        Map<String, String> props = new LinkedHashMap<>();
        if (brokerMap.get("connection") instanceof Map<?, ?> conn) {
            conn.forEach((k, v) -> props.put(String.valueOf(k), String.valueOf(v)));
        }
        if (brokerMap.get("properties") instanceof Map<?, ?> p) {
            p.forEach((k, v) -> props.put(String.valueOf(k), String.valueOf(v)));
        }
        bc.setProperties(props);
        return new ResolvedBroker(bc, brokerMap);
    }

    /**
     * Build connection properties map by merging broker and channel-level settings.
     * The resulting map is passed to DataPublisher/DataSubscriber.initialize().
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> buildConnectionProps(Map<String, Object> channelConfig,
                                                      BrokerConfig brokerConfig,
                                                      Map<String, Object> rawBrokerMap) {
        Map<String, Object> props = new LinkedHashMap<>();

        // Broker-level properties
        if (brokerConfig.getProperties() != null) {
            props.putAll(brokerConfig.getProperties());
        }
        if (brokerConfig.getConnectionUri() != null && !brokerConfig.getConnectionUri().isBlank()) {
            props.put("connection_uri", brokerConfig.getConnectionUri());
        }
        props.put("broker_type", brokerConfig.getBrokerType().name());
        props.put("broker_id", brokerConfig.getBrokerId());

        // Pass through nested config blocks (authentication, schema_registry, ssl)
        // Required by Confluent Kafka and other brokers that read structured sub-maps
        for (String block : List.of("authentication", "schema_registry", "ssl")) {
            if (rawBrokerMap.get(block) instanceof Map<?, ?> nested) {
                props.put(block, new LinkedHashMap<>(nested));
            }
        }

        // Nested "properties" map from broker JSON (native client overrides)
        if (rawBrokerMap.get("properties") instanceof Map<?, ?> nativeProps) {
            Map<String, String> propsCopy = new LinkedHashMap<>();
            nativeProps.forEach((k, v) -> propsCopy.put(String.valueOf(k), String.valueOf(v)));
            props.put("properties", propsCopy);
        }

        // Channel-level overrides (retry, queue depth, etc.)
        if (channelConfig.get("retry") instanceof Map<?, ?> retry) {
            retry.forEach((k, v) -> props.put("retry." + k, v));
        }
        if (channelConfig.get("queue") instanceof Map<?, ?> queue) {
            queue.forEach((k, v) -> props.put("queue." + k, v));
        }

        // SSL config from channel (overrides broker-level)
        if (channelConfig.get("ssl") instanceof Map<?, ?> ssl) {
            ssl.forEach((k, v) -> props.put("ssl." + k, v));
        }

        return props;
    }
}
