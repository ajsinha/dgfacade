/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential.
 */
package com.dgfacade.server.ingestion;

import java.time.Instant;
import java.util.Map;

/**
 * Contract for all request ingesters. Each ingester listens on a source
 * (Kafka topic, ActiveMQ queue, filesystem directory) and submits
 * deserialized DGRequest objects to the ExecutionEngine.
 *
 * <p>Multiple ingesters can run concurrently — each on its own thread.</p>
 */
public interface RequestIngester {

    /** Unique identifier for this ingester instance. */
    String getId();

    /** Human-readable description. */
    String getDescription();

    /** Ingester type: KAFKA, ACTIVEMQ, FILESYSTEM. */
    IngesterType getType();

    /** Initialize from JSON configuration. */
    void initialize(String id, Map<String, Object> config);

    /** Start consuming messages. Non-blocking — launches its own thread(s). */
    void start();

    /** Stop consuming and release resources. */
    void stop();

    /** Whether this ingester is currently running and consuming. */
    boolean isRunning();

    /** Get runtime statistics. */
    IngesterStats getStats();

    // ─── Types ──────────────────────────────────────────────────────────

    enum IngesterType {
        KAFKA, CONFLUENT_KAFKA, ACTIVEMQ, FILESYSTEM
    }

    record IngesterStats(
            String id,
            IngesterType type,
            boolean running,
            long requestsReceived,
            long requestsSubmitted,
            long requestsFailed,
            long requestsRejected,
            Instant startedAt,
            Instant lastActivityAt,
            String source
    ) {}
}
