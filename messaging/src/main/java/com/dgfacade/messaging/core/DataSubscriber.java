/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential.
 */
package com.dgfacade.messaging.core;

import java.util.Set;

/**
 * Abstract subscriber interface for receiving messages from any broker.
 * Implementations exist for Kafka, ActiveMQ, FileSystem, and SQL.
 *
 * For Kafka and ActiveMQ: event-driven (messages arrive as they are published).
 * For FileSystem and SQL: schedule-driven (polled at configurable intervals).
 *
 * Back-pressure: if the internal queue depth exceeds the configured limit,
 * no more messages are pulled from the broker — they stay on the broker.
 */
public interface DataSubscriber extends AutoCloseable {

    /** Initialize the subscriber with connection details. */
    void initialize(java.util.Map<String, Object> config);

    /** Subscribe to a topic/queue and register a listener. Can be called dynamically at runtime. */
    void subscribe(String topicOrQueue, MessageListener listener);

    /** Unsubscribe from a topic/queue. */
    void unsubscribe(String topicOrQueue);

    /** Get all currently subscribed topics/queues. */
    Set<String> getSubscriptions();

    /** Start consuming messages. */
    void start();

    /** Pause consumption (messages stay on broker). */
    void pause();

    /** Resume consumption after pause. */
    void resume();

    /** Check if subscriber is connected and consuming. */
    boolean isConnected();

    /** Get the current internal queue depth (for backpressure monitoring). */
    int getQueueDepth();

    /** Get subscriber statistics. */
    SubscriberStats getStats();

    @Override
    void close();

    record SubscriberStats(long messagesReceived, long messagesProcessed, long messagesErrored,
                           int currentQueueDepth, boolean connected, boolean paused) {}
}
