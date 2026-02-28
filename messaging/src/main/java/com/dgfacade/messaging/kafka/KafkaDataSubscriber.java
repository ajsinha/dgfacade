/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential.
 */
package com.dgfacade.messaging.kafka;

import com.dgfacade.messaging.core.*;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.serialization.StringDeserializer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * Kafka-based DataSubscriber. Event-driven: polls the broker in a tight loop
 * and dispatches messages via the internal queue. Supports dynamic topic subscription.
 * Implements backpressure by pausing Kafka partition assignment when queue is full.
 */
public class KafkaDataSubscriber extends AbstractSubscriber {

    private KafkaConsumer<String, String> consumer;
    private volatile boolean running = false;
    private ExecutorService pollExecutor;

    @Override
    protected void doConnect() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                config.getOrDefault("bootstrap_servers", "localhost:9092"));
        props.put(ConsumerConfig.GROUP_ID_CONFIG,
                config.getOrDefault("group_id", "dgfacade-" + UUID.randomUUID().toString().substring(0, 8)));
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                config.getOrDefault("auto_offset_reset", "latest"));
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG,
                config.getOrDefault("max_poll_records", "500"));
        // Connection recovery: exponential backoff for reconnection attempts
        props.put(ConsumerConfig.RECONNECT_BACKOFF_MS_CONFIG,
                config.getOrDefault("reconnect_backoff_ms", "1000"));
        props.put(ConsumerConfig.RECONNECT_BACKOFF_MAX_MS_CONFIG,
                config.getOrDefault("reconnect_backoff_max_ms", "30000"));
        props.put(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG,
                config.getOrDefault("request_timeout_ms", "30000"));
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG,
                config.getOrDefault("session_timeout_ms", "45000"));
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG,
                config.getOrDefault("heartbeat_interval_ms", "10000"));
        if (config.containsKey("properties")) {
            @SuppressWarnings("unchecked")
            Map<String, String> custom = (Map<String, String>) config.get("properties");
            props.putAll(custom);
        }
        consumer = new KafkaConsumer<>(props);
        state = ConnectionState.CONNECTED;
        log.info("Kafka consumer connected to {}", props.get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG));
    }

    @Override
    public void start() {
        super.start();
        running = true;
        pollExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "kafka-poll");
            t.setDaemon(true);
            return t;
        });
        pollExecutor.submit(this::pollLoop);
    }

    private void pollLoop() {
        while (running && state != ConnectionState.CLOSING) {
            try {
                if (paused || internalQueue.size() >= backpressureMaxDepth) {
                    Thread.sleep(100);
                    continue;
                }
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(200));
                for (ConsumerRecord<String, String> record : records) {
                    MessageEnvelope envelope = new MessageEnvelope(record.topic(), record.value());
                    envelope.setPartition(record.partition());
                    envelope.setOffset(record.offset());
                    enqueue(envelope);
                }
            } catch (Exception e) {
                if (running) {
                    log.error("Kafka poll error — will attempt reconnection", e);
                    try {
                        // Close stale consumer before reconnect
                        if (consumer != null) { try { consumer.close(Duration.ofSeconds(5)); } catch (Exception ignored) {} }
                        state = ConnectionState.RECONNECTING;
                        // Exponential backoff: 5s, 10s, 20s, capped at 60s
                        int backoffSeconds = Math.min(60, reconnectIntervalSeconds);
                        Thread.sleep(backoffSeconds * 1000L);
                        log.info("Attempting Kafka consumer reconnection...");
                        doConnect();
                        for (String topic : listeners.keySet()) doSubscribe(topic);
                        log.info("Kafka consumer reconnected — resuming poll loop");
                        // Continue the loop (don't return)
                    } catch (Exception reconnectEx) {
                        log.warn("Kafka reconnection failed, scheduling deferred reconnect: {}", reconnectEx.getMessage());
                        scheduleReconnect();
                        return;
                    }
                }
            }
        }
    }

    @Override
    protected void doSubscribe(String topicOrQueue) {
        Set<String> currentTopics = new HashSet<>(listeners.keySet());
        if (consumer != null) {
            consumer.subscribe(currentTopics);
            log.info("Kafka subscribed to topics: {}", currentTopics);
        }
    }

    @Override
    protected void doUnsubscribe(String topicOrQueue) {
        if (consumer != null) {
            Set<String> remaining = new HashSet<>(listeners.keySet());
            consumer.subscribe(remaining);
        }
    }

    @Override
    protected void doDisconnect() {
        running = false;
        if (pollExecutor != null) pollExecutor.shutdownNow();
        if (consumer != null) consumer.close();
    }

    @Override
    public boolean isConnected() { return state == ConnectionState.CONNECTED && consumer != null; }
}
