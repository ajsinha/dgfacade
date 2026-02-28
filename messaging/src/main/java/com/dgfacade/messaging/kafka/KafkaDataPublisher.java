/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential.
 */
package com.dgfacade.messaging.kafka;

import com.dgfacade.messaging.core.*;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Kafka-based DataPublisher using the standard Kafka Producer API.
 * Event-based by default. Supports dynamic topic addition at runtime.
 */
public class KafkaDataPublisher extends AbstractPublisher {

    private KafkaProducer<String, String> producer;

    @Override
    protected void doConnect() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                config.getOrDefault("bootstrap_servers", "localhost:9092"));
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, config.getOrDefault("acks", "1"));
        props.put(ProducerConfig.RETRIES_CONFIG, config.getOrDefault("retries", "3"));
        props.put(ProducerConfig.LINGER_MS_CONFIG, config.getOrDefault("linger_ms", "5"));
        // Connection recovery: exponential backoff for reconnection attempts
        props.put(ProducerConfig.RECONNECT_BACKOFF_MS_CONFIG,
                config.getOrDefault("reconnect_backoff_ms", "1000"));
        props.put(ProducerConfig.RECONNECT_BACKOFF_MAX_MS_CONFIG,
                config.getOrDefault("reconnect_backoff_max_ms", "30000"));
        props.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG,
                config.getOrDefault("retry_backoff_ms", "500"));
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG,
                config.getOrDefault("request_timeout_ms", "30000"));
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG,
                config.getOrDefault("delivery_timeout_ms", "120000"));
        // Add any custom properties
        if (config.containsKey("properties")) {
            @SuppressWarnings("unchecked")
            Map<String, String> custom = (Map<String, String>) config.get("properties");
            props.putAll(custom);
        }
        producer = new KafkaProducer<>(props);
        state = ConnectionState.CONNECTED;
        log.info("Kafka producer connected to {}", props.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG));
    }

    @Override
    protected CompletableFuture<Void> doPublish(String topic, MessageEnvelope envelope) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        ProducerRecord<String, String> record = new ProducerRecord<>(topic,
                envelope.getMessageId(), envelope.getPayload());
        if (envelope.getHeaders() != null) {
            envelope.getHeaders().forEach((k, v) ->
                record.headers().add(k, v.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }
        producer.send(record, (metadata, exception) -> {
            if (exception != null) {
                future.completeExceptionally(exception);
            } else {
                future.complete(null);
            }
        });
        return future;
    }

    @Override
    protected void doDisconnect() {
        if (producer != null) {
            producer.flush();
            producer.close();
        }
    }

    @Override
    public boolean isConnected() { return state == ConnectionState.CONNECTED && producer != null; }
}
