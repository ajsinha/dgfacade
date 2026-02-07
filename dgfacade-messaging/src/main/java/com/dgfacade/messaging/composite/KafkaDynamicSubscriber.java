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

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.MessageListener;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Manages dynamic Kafka topic subscriptions using Spring Kafka listener containers.
 *
 * <p>For each subscribed topic, a dedicated {@link ConcurrentMessageListenerContainer}
 * is created, backed by a shared {@link DefaultKafkaConsumerFactory} (shared connection
 * configuration). When a topic is unsubscribed, its container is stopped and destroyed.
 *
 * <p>This class is thread-safe; all container lifecycle operations are synchronized.
 *
 * @since 1.1.0
 */
class KafkaDynamicSubscriber implements DynamicSubscriber {

    private static final Logger log = LoggerFactory.getLogger(KafkaDynamicSubscriber.class);

    private final DefaultKafkaConsumerFactory<String, String> consumerFactory;
    private final int concurrency;
    private final String groupId;

    /**
     * Active listener containers keyed by topic name.
     * Each container manages its own consumer thread(s) for one topic.
     */
    private final ConcurrentHashMap<String, ConcurrentMessageListenerContainer<String, String>>
            containers = new ConcurrentHashMap<>();

    private volatile boolean available = false;
    private volatile boolean shutdown = false;

    /**
     * Creates a Kafka dynamic subscriber from the composite config.
     *
     * @param config composite listener configuration with Kafka details
     */
    KafkaDynamicSubscriber(CompositeListenerConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        if (!config.isKafkaEnabled()) {
            throw new IllegalArgumentException("Kafka is not enabled in config");
        }

        this.groupId = config.getKafkaGroupId();
        this.concurrency = config.getKafkaConcurrency();

        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getKafkaBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, config.getKafkaAutoOffsetReset());
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);

        // Merge extra properties
        if (config.getKafkaExtraProperties() != null) {
            props.putAll(config.getKafkaExtraProperties());
        }

        this.consumerFactory = new DefaultKafkaConsumerFactory<>(props);
        this.available = true;

        log.info("KafkaDynamicSubscriber initialized — servers={}, group={}, concurrency={}",
                config.getKafkaBootstrapServers(), groupId, concurrency);
    }

    @Override
    public void subscribe(String topic, Consumer<ReceivedMessage> callback) {
        Objects.requireNonNull(topic, "topic must not be null");
        Objects.requireNonNull(callback, "callback must not be null");

        if (shutdown) {
            throw new IllegalStateException("KafkaDynamicSubscriber is shut down");
        }

        // If already subscribed, this is a no-op at the container level
        if (containers.containsKey(topic)) {
            log.debug("Kafka already subscribed to topic '{}', skipping container creation", topic);
            return;
        }

        synchronized (containers) {
            // Double-check under lock
            if (containers.containsKey(topic)) return;

            log.info("Subscribing to Kafka topic: {}", topic);

            ContainerProperties containerProps = new ContainerProperties(topic);
            containerProps.setGroupId(groupId);

            // The internal message listener converts Kafka ConsumerRecord to ReceivedMessage
            containerProps.setMessageListener((MessageListener<String, String>) record -> {
                try {
                    ReceivedMessage msg = toReceivedMessage(record);
                    callback.accept(msg);
                } catch (Exception e) {
                    log.error("Error processing Kafka message from topic '{}': {}",
                            topic, e.getMessage(), e);
                }
            });

            ConcurrentMessageListenerContainer<String, String> container =
                    new ConcurrentMessageListenerContainer<>(consumerFactory, containerProps);
            container.setConcurrency(concurrency);
            container.setBeanName("composite-kafka-" + topic);

            container.start();
            containers.put(topic, container);

            log.info("Kafka container started for topic '{}' (total active: {})",
                    topic, containers.size());
        }
    }

    @Override
    public void unsubscribe(String topic) {
        Objects.requireNonNull(topic, "topic must not be null");

        ConcurrentMessageListenerContainer<String, String> container = containers.remove(topic);
        if (container != null) {
            log.info("Unsubscribing from Kafka topic: {}", topic);
            try {
                container.stop();
                container.destroy();
            } catch (Exception e) {
                log.warn("Error stopping Kafka container for topic '{}': {}", topic, e.getMessage());
            }
            log.info("Kafka container stopped and removed for topic '{}' (total active: {})",
                    topic, containers.size());
        }
    }

    @Override
    public Set<String> getSubscribedTopics() {
        return Collections.unmodifiableSet(new HashSet<>(containers.keySet()));
    }

    @Override
    public boolean isSubscribed(String topic) {
        return containers.containsKey(topic);
    }

    @Override
    public ReceivedMessage.BrokerSource getBrokerSource() {
        return ReceivedMessage.BrokerSource.KAFKA;
    }

    @Override
    public boolean isAvailable() { return available && !shutdown; }

    @Override
    public void shutdown() {
        if (shutdown) return;
        shutdown = true;
        available = false;

        log.info("Shutting down KafkaDynamicSubscriber — stopping {} containers", containers.size());

        for (Map.Entry<String, ConcurrentMessageListenerContainer<String, String>> entry :
                containers.entrySet()) {
            try {
                entry.getValue().stop();
                entry.getValue().destroy();
                log.debug("Stopped Kafka container for topic '{}'", entry.getKey());
            } catch (Exception e) {
                log.warn("Error stopping Kafka container for topic '{}': {}",
                        entry.getKey(), e.getMessage());
            }
        }
        containers.clear();
        log.info("KafkaDynamicSubscriber shut down complete");
    }

    /**
     * Convert a Kafka ConsumerRecord to a ReceivedMessage.
     */
    private ReceivedMessage toReceivedMessage(ConsumerRecord<String, String> record) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (record.headers() != null) {
            for (Header h : record.headers()) {
                headers.put(h.key(), new String(h.value(), StandardCharsets.UTF_8));
            }
        }
        return new ReceivedMessage(
                record.topic(),
                record.key(),
                record.value(),
                headers,
                ReceivedMessage.BrokerSource.KAFKA
        );
    }
}
