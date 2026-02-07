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

import java.util.Set;

/**
 * Internal interface for a broker-specific dynamic subscriber.
 *
 * Each implementation manages the lifecycle of listener containers
 * (Kafka consumer containers, JMS listener containers) for its
 * respective broker. The {@link CompositeMessageListener} delegates
 * subscribe/unsubscribe operations to these subscribers.
 *
 * <p>Implementations must be thread-safe.
 *
 * @since 1.1.0
 */
interface DynamicSubscriber {

    /**
     * Subscribe to a topic/destination. If already subscribed, this is a no-op.
     * The provided callback receives every message on that topic.
     *
     * @param topic    the topic or queue name
     * @param callback receives every raw message, which the caller will fan-out to listeners
     */
    void subscribe(String topic, java.util.function.Consumer<ReceivedMessage> callback);

    /**
     * Unsubscribe from a topic/destination. Stops and destroys the underlying
     * listener container for that topic.
     *
     * @param topic the topic or queue name
     */
    void unsubscribe(String topic);

    /**
     * Returns the set of topics/destinations currently subscribed to.
     */
    Set<String> getSubscribedTopics();

    /**
     * Returns true if currently subscribed to the given topic.
     */
    boolean isSubscribed(String topic);

    /**
     * Returns the broker source type (KAFKA or ACTIVEMQ).
     */
    ReceivedMessage.BrokerSource getBrokerSource();

    /**
     * Returns true if this subscriber was initialized and is operational.
     */
    boolean isAvailable();

    /**
     * Shutdown all listener containers and release resources.
     */
    void shutdown();
}
