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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * A composite, multi-broker message listener that supports dynamic topic
 * subscriptions with multiple listeners per topic.
 *
 * <h3>Architecture</h3>
 * <pre>
 *   ┌─────────────────────────────────────────────────────────┐
 *   │              CompositeMessageListener                   │
 *   │                                                         │
 *   │  topicListeners: Map&lt;String, Set&lt;TopicMessageListener&gt;&gt;  │
 *   │                                                         │
 *   │  ┌─────────────────────┐  ┌──────────────────────────┐  │
 *   │  │ KafkaDynamicSubscr  │  │ ActiveMQDynamicSubscr    │  │
 *   │  │  (shared consumer   │  │  (shared connection      │  │
 *   │  │   factory)          │  │   factory)               │  │
 *   │  │                     │  │                          │  │
 *   │  │ topic-A → container │  │ queue-X → container      │  │
 *   │  │ topic-B → container │  │ topic-Y → container      │  │
 *   │  └─────────────────────┘  └──────────────────────────┘  │
 *   └─────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h3>Key behaviors</h3>
 * <ul>
 *   <li><strong>Dynamic subscriptions:</strong> Topics can be added and removed at runtime.
 *       No restart required.</li>
 *   <li><strong>Multiple listeners per topic:</strong> Call {@code addListener(topic, listener)}
 *       multiple times with different listener instances.</li>
 *   <li><strong>Automatic unsubscribe:</strong> When the last listener for a topic is removed,
 *       the underlying broker subscription is torn down and the topic is removed from memory.</li>
 *   <li><strong>Multi-broker fan-out:</strong> A single {@code addListener} call subscribes
 *       to the topic on ALL enabled brokers. A message from any broker is delivered to all listeners.</li>
 *   <li><strong>Shared connections:</strong> Each broker uses one shared connection factory
 *       regardless of how many topics are subscribed.</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 *   // 1. Build configuration
 *   CompositeListenerConfig config = CompositeListenerConfig.builder()
 *       .kafkaEnabled(true)
 *       .kafkaBootstrapServers("broker:9092")
 *       .kafkaGroupId("my-group")
 *       .activeMqEnabled(true)
 *       .activeMqBrokerUrl("tcp://localhost:61616")
 *       .build();
 *
 *   // 2. Create the composite listener
 *   CompositeMessageListener composite = new CompositeMessageListener(config);
 *
 *   // 3. Add listeners at runtime
 *   TopicMessageListener priceHandler = msg ->
 *       System.out.println("Price: " + msg.value());
 *   TopicMessageListener auditLogger = msg ->
 *       log.info("Audit: {} from {}", msg.topic(), msg.source());
 *
 *   composite.addListener("market-data", priceHandler);
 *   composite.addListener("market-data", auditLogger);  // same topic, second listener
 *   composite.addListener("trade-events", priceHandler); // same listener, different topic
 *
 *   // 4. Remove a listener — if last one for topic, topic is fully unsubscribed
 *   composite.removeListener("market-data", auditLogger);
 *
 *   // 5. Shutdown when done
 *   composite.shutdown();
 * }</pre>
 *
 * @since 1.1.0
 */
public class CompositeMessageListener implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(CompositeMessageListener.class);

    private final CompositeListenerConfig config;
    private final String name;

    /** Kafka dynamic subscriber — null if Kafka not enabled. */
    private final KafkaDynamicSubscriber kafkaSubscriber;

    /** ActiveMQ dynamic subscriber — null if ActiveMQ not enabled. */
    private final ActiveMQDynamicSubscriber activeMqSubscriber;

    /**
     * Master map: topic/destination → set of registered listeners.
     * CopyOnWriteArraySet ensures safe concurrent iteration during fan-out
     * while still allowing add/remove from other threads.
     */
    private final ConcurrentHashMap<String, CopyOnWriteArraySet<TopicMessageListener>>
            topicListeners = new ConcurrentHashMap<>();

    /** Total messages received across all topics. */
    private final AtomicLong totalMessagesReceived = new AtomicLong(0);

    /** Total messages delivered (listener invocations). */
    private final AtomicLong totalMessagesDelivered = new AtomicLong(0);

    private volatile boolean shutdownFlag = false;

    /**
     * Creates a composite listener with the given configuration.
     * Initializes internal subscribers for each enabled broker.
     *
     * @param config configuration with Kafka and/or ActiveMQ connection details
     */
    public CompositeMessageListener(CompositeListenerConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.name = config.getListenerName();

        log.info("Initializing CompositeMessageListener '{}' — {}", name, config);

        // Initialize broker-specific subscribers
        if (config.isKafkaEnabled()) {
            this.kafkaSubscriber = new KafkaDynamicSubscriber(config);
        } else {
            this.kafkaSubscriber = null;
        }

        if (config.isActiveMqEnabled()) {
            this.activeMqSubscriber = new ActiveMQDynamicSubscriber(config);
        } else {
            this.activeMqSubscriber = null;
        }

        log.info("CompositeMessageListener '{}' initialized — kafka={}, activemq={}",
                name,
                kafkaSubscriber != null ? "enabled" : "disabled",
                activeMqSubscriber != null ? "enabled" : "disabled");
    }

    // ========== Core API: Add / Remove Listeners ==========

    /**
     * Register a listener for a topic.
     *
     * <p>If this is the first listener for the given topic, the composite listener
     * subscribes to that topic on all enabled brokers. If already subscribed,
     * the listener is simply added to the set.
     *
     * <p>The same listener instance can be registered for multiple topics.
     * The same topic can have multiple different listeners.
     *
     * @param topic    the topic or destination name
     * @param listener the callback to invoke when messages arrive
     * @throws IllegalStateException if the listener has been shut down
     */
    public void addListener(String topic, TopicMessageListener listener) {
        Objects.requireNonNull(topic, "topic must not be null");
        Objects.requireNonNull(listener, "listener must not be null");

        if (shutdownFlag) {
            throw new IllegalStateException("CompositeMessageListener '" + name + "' is shut down");
        }

        CopyOnWriteArraySet<TopicMessageListener> listeners =
                topicListeners.computeIfAbsent(topic, k -> new CopyOnWriteArraySet<>());

        boolean added = listeners.add(listener);

        if (added) {
            log.info("Listener added for topic '{}' (now {} listeners)", topic, listeners.size());
        } else {
            log.debug("Listener already registered for topic '{}', skipping", topic);
            return;
        }

        // Subscribe at the broker level if this is the first listener for the topic
        if (listeners.size() == 1) {
            subscribeAtBrokerLevel(topic);
        }
    }

    /**
     * Remove a specific listener from a topic.
     *
     * <p>If this was the last listener for the topic, the composite listener
     * unsubscribes from that topic on all brokers and removes the topic
     * from its internal map entirely.
     *
     * @param topic    the topic or destination name
     * @param listener the listener instance to remove
     * @return true if the listener was found and removed, false otherwise
     */
    public boolean removeListener(String topic, TopicMessageListener listener) {
        Objects.requireNonNull(topic, "topic must not be null");
        Objects.requireNonNull(listener, "listener must not be null");

        CopyOnWriteArraySet<TopicMessageListener> listeners = topicListeners.get(topic);
        if (listeners == null) {
            log.debug("No listeners registered for topic '{}', nothing to remove", topic);
            return false;
        }

        boolean removed = listeners.remove(listener);
        if (!removed) {
            log.debug("Listener not found for topic '{}', nothing to remove", topic);
            return false;
        }

        log.info("Listener removed from topic '{}' ({} remaining)", topic, listeners.size());

        // If no listeners remain, unsubscribe from the topic entirely
        if (listeners.isEmpty()) {
            topicListeners.remove(topic);
            unsubscribeAtBrokerLevel(topic);
            log.info("Topic '{}' has no listeners — unsubscribed and removed from memory", topic);
        }

        return true;
    }

    /**
     * Remove ALL listeners for a specific topic and unsubscribe.
     *
     * @param topic the topic or destination name
     * @return the number of listeners that were removed
     */
    public int removeAllListeners(String topic) {
        Objects.requireNonNull(topic, "topic must not be null");

        CopyOnWriteArraySet<TopicMessageListener> listeners = topicListeners.remove(topic);
        if (listeners == null || listeners.isEmpty()) {
            return 0;
        }

        int count = listeners.size();
        listeners.clear();
        unsubscribeAtBrokerLevel(topic);

        log.info("All {} listeners removed from topic '{}' — unsubscribed", count, topic);
        return count;
    }

    /**
     * Remove a specific listener from ALL topics it is registered on.
     * Useful for cleanup when a listener object is being decommissioned.
     *
     * @param listener the listener instance to remove from everywhere
     * @return the set of topics the listener was removed from
     */
    public Set<String> removeListenerFromAllTopics(TopicMessageListener listener) {
        Objects.requireNonNull(listener, "listener must not be null");

        Set<String> removedFrom = new HashSet<>();
        for (String topic : new ArrayList<>(topicListeners.keySet())) {
            if (removeListener(topic, listener)) {
                removedFrom.add(topic);
            }
        }

        if (!removedFrom.isEmpty()) {
            log.info("Listener removed from {} topics: {}", removedFrom.size(), removedFrom);
        }
        return removedFrom;
    }

    // ========== Query API ==========

    /**
     * Returns the set of topics that currently have at least one listener.
     */
    public Set<String> getActiveTopics() {
        return Collections.unmodifiableSet(new HashSet<>(topicListeners.keySet()));
    }

    /**
     * Returns the number of listeners registered for a specific topic.
     */
    public int getListenerCount(String topic) {
        CopyOnWriteArraySet<TopicMessageListener> listeners = topicListeners.get(topic);
        return listeners != null ? listeners.size() : 0;
    }

    /**
     * Returns all topics and their listener counts.
     */
    public Map<String, Integer> getTopicListenerCounts() {
        return topicListeners.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().size(),
                        (a, b) -> a,
                        LinkedHashMap::new));
    }

    /**
     * Returns the total number of active topics (with at least one listener).
     */
    public int getActiveTopicCount() {
        return topicListeners.size();
    }

    /**
     * Returns the total number of messages received from all brokers.
     */
    public long getTotalMessagesReceived() { return totalMessagesReceived.get(); }

    /**
     * Returns the total number of listener invocations (messages × listeners).
     */
    public long getTotalMessagesDelivered() { return totalMessagesDelivered.get(); }

    /**
     * Returns true if Kafka is enabled and its subscriber is available.
     */
    public boolean isKafkaAvailable() {
        return kafkaSubscriber != null && kafkaSubscriber.isAvailable();
    }

    /**
     * Returns true if ActiveMQ is enabled and its subscriber is available.
     */
    public boolean isActiveMqAvailable() {
        return activeMqSubscriber != null && activeMqSubscriber.isAvailable();
    }

    /**
     * Returns the name of this composite listener.
     */
    public String getName() { return name; }

    /**
     * Returns true if this composite listener has been shut down.
     */
    public boolean isShutdown() { return shutdownFlag; }

    /**
     * Returns a snapshot of the current state for diagnostics.
     */
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("name", name);
        status.put("shutdown", shutdownFlag);
        status.put("kafkaEnabled", kafkaSubscriber != null);
        status.put("kafkaAvailable", isKafkaAvailable());
        status.put("activeMqEnabled", activeMqSubscriber != null);
        status.put("activeMqAvailable", isActiveMqAvailable());
        status.put("activeTopics", getActiveTopicCount());
        status.put("topicListenerCounts", getTopicListenerCounts());
        status.put("totalMessagesReceived", totalMessagesReceived.get());
        status.put("totalMessagesDelivered", totalMessagesDelivered.get());
        if (kafkaSubscriber != null) {
            status.put("kafkaSubscribedTopics", kafkaSubscriber.getSubscribedTopics());
        }
        if (activeMqSubscriber != null) {
            status.put("activeMqSubscribedTopics", activeMqSubscriber.getSubscribedTopics());
        }
        return status;
    }

    // ========== Lifecycle ==========

    /**
     * Shutdown the composite listener. Unsubscribes from all topics,
     * stops all broker containers, and releases resources.
     *
     * <p>After shutdown, no new listeners can be added and no messages
     * will be received.
     */
    public void shutdown() {
        if (shutdownFlag) return;
        shutdownFlag = true;

        log.info("Shutting down CompositeMessageListener '{}' — {} active topics",
                name, topicListeners.size());

        topicListeners.clear();

        if (kafkaSubscriber != null) {
            kafkaSubscriber.shutdown();
        }
        if (activeMqSubscriber != null) {
            activeMqSubscriber.shutdown();
        }

        log.info("CompositeMessageListener '{}' shut down — total received={}, delivered={}",
                name, totalMessagesReceived.get(), totalMessagesDelivered.get());
    }

    /** Alias for {@link #shutdown()} to support try-with-resources. */
    @Override
    public void close() { shutdown(); }

    // ========== Internal: Broker-Level Subscribe / Unsubscribe ==========

    /**
     * Subscribe to a topic on all enabled brokers.
     * Each broker's callback routes messages to {@link #fanOutToListeners}.
     */
    private void subscribeAtBrokerLevel(String topic) {
        if (kafkaSubscriber != null && kafkaSubscriber.isAvailable()) {
            try {
                kafkaSubscriber.subscribe(topic, msg -> fanOutToListeners(topic, msg));
            } catch (Exception e) {
                log.error("Failed to subscribe to Kafka topic '{}': {}", topic, e.getMessage(), e);
            }
        }

        if (activeMqSubscriber != null && activeMqSubscriber.isAvailable()) {
            try {
                activeMqSubscriber.subscribe(topic, msg -> fanOutToListeners(topic, msg));
            } catch (Exception e) {
                log.error("Failed to subscribe to ActiveMQ destination '{}': {}",
                        topic, e.getMessage(), e);
            }
        }
    }

    /**
     * Unsubscribe from a topic on all enabled brokers.
     */
    private void unsubscribeAtBrokerLevel(String topic) {
        if (kafkaSubscriber != null) {
            try {
                kafkaSubscriber.unsubscribe(topic);
            } catch (Exception e) {
                log.warn("Error unsubscribing Kafka topic '{}': {}", topic, e.getMessage());
            }
        }

        if (activeMqSubscriber != null) {
            try {
                activeMqSubscriber.unsubscribe(topic);
            } catch (Exception e) {
                log.warn("Error unsubscribing ActiveMQ destination '{}': {}", topic, e.getMessage());
            }
        }
    }

    /**
     * Fan-out a received message to all listeners registered for the topic.
     * Called on the broker's consumer thread.
     */
    private void fanOutToListeners(String topic, ReceivedMessage message) {
        totalMessagesReceived.incrementAndGet();

        CopyOnWriteArraySet<TopicMessageListener> listeners = topicListeners.get(topic);
        if (listeners == null || listeners.isEmpty()) {
            log.debug("Message received on '{}' but no listeners registered (race condition?)", topic);
            return;
        }

        for (TopicMessageListener listener : listeners) {
            try {
                listener.onMessage(message);
                totalMessagesDelivered.incrementAndGet();
            } catch (Exception e) {
                log.error("Listener threw exception processing message from '{}' (source={}): {}",
                        topic, message.source(), e.getMessage(), e);
            }
        }
    }
}
