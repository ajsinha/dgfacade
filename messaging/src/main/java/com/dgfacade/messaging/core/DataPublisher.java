/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential.
 */
package com.dgfacade.messaging.core;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Abstract publisher interface for sending messages to any broker.
 * Implementations exist for Kafka, ActiveMQ, FileSystem, and SQL.
 *
 * Publication can be event-based (immediate send) or batched (scheduled flush).
 * Default: event-based for Kafka/ActiveMQ, scheduled for File/SQL.
 */
public interface DataPublisher extends AutoCloseable {

    /** Initialize the publisher with connection details. */
    void initialize(java.util.Map<String, Object> config);

    /** Publish a single message to a topic. Returns a future that completes on ack. */
    CompletableFuture<Void> publish(String topic, MessageEnvelope envelope);

    /** Publish a batch of messages. */
    CompletableFuture<Void> publishBatch(String topic, List<MessageEnvelope> envelopes);

    /** Dynamically change or add a target topic at runtime. */
    void addTopic(String topic);

    /** Check if the publisher is connected and healthy. */
    boolean isConnected();

    /** Flush any buffered messages. */
    void flush();

    /** Get publisher statistics. */
    PublisherStats getStats();

    @Override
    void close();

    record PublisherStats(long messagesSent, long messagesErrored, long bytesSent, boolean connected) {}
}
