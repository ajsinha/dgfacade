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

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

/**
 * Immutable record representing a message received from Kafka or ActiveMQ.
 *
 * Carries the raw payload along with metadata about where and when the
 * message was received. The {@code source} field indicates which broker
 * the message originated from.
 *
 * @param topic       the topic or queue name the message was received on
 * @param key         the message key (Kafka only; null for ActiveMQ)
 * @param value       the message payload as a string
 * @param headers     optional headers/properties from the message
 * @param source      the broker source: KAFKA or ACTIVEMQ
 * @param receivedAt  the instant when the message was received by the listener
 *
 * @since 1.1.0
 */
public record ReceivedMessage(
        String topic,
        String key,
        String value,
        Map<String, String> headers,
        BrokerSource source,
        Instant receivedAt
) {
    /** Which messaging broker the message was received from. */
    public enum BrokerSource {
        KAFKA, ACTIVEMQ
    }

    /** Convenience constructor without headers. */
    public ReceivedMessage(String topic, String key, String value, BrokerSource source) {
        this(topic, key, value, Collections.emptyMap(), source, Instant.now());
    }

    /** Convenience constructor with headers. */
    public ReceivedMessage(String topic, String key, String value,
                           Map<String, String> headers, BrokerSource source) {
        this(topic, key, value, headers, source, Instant.now());
    }
}
