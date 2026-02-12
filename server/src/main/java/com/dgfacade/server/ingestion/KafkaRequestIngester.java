/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential. Patent Pending.
 */
package com.dgfacade.server.ingestion;

import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Kafka-based request ingester. Reads connection settings from the resolved
 * broker config and topic subscriptions from the input channel destinations.
 *
 * <p>Resolution chain:</p>
 * <pre>
 *   ingester config → input_channel (destinations: topics) → broker (bootstrap_servers, group_id, ...)
 * </pre>
 *
 * <p>Ingester-level {@code overrides} can override any broker field (e.g., group_id).</p>
 */
public class KafkaRequestIngester extends AbstractRequestIngester {

    private KafkaConsumer<String, String> consumer;
    private ExecutorService pollExecutor;
    private List<String> topics;
    private String bootstrapServers;

    @Override
    public IngesterType getType() { return IngesterType.KAFKA; }

    @SuppressWarnings("unchecked")
    @Override
    public void initialize(String id, Map<String, Object> config) {
        super.initialize(id, config);

        // Connection from broker (flattened into resolved config)
        this.bootstrapServers = (String) config.getOrDefault("bootstrap_servers", "localhost:9092");

        // Topics from input channel destinations
        Object destsObj = config.get("destinations");
        if (destsObj instanceof List<?> list) {
            this.topics = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    String name = (String) map.get("name");
                    if (name != null) this.topics.add(name);
                }
            }
        }
        if (this.topics == null || this.topics.isEmpty()) {
            this.topics = List.of("dgfacade.requests");
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void start() {
        if (running) return;

        log.info("[{}] ── Kafka Ingester Starting ──", id);
        log.info("[{}]   bootstrap_servers = {}", id, bootstrapServers);
        log.info("[{}]   topics            = {}", id, topics);

        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG,
                config.getOrDefault("group_id", "dgfacade-ingester-" + id));
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                config.getOrDefault("auto_offset_reset", "latest"));
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG,
                String.valueOf(config.getOrDefault("max_poll_records", 100)));

        // Custom Kafka properties from broker config
        if (config.containsKey("properties")) {
            Map<String, String> custom = (Map<String, String>) config.get("properties");
            props.putAll(custom);
        }

        log.info("[{}]   group_id          = {}", id, props.get(ConsumerConfig.GROUP_ID_CONFIG));
        log.info("[{}]   auto_offset_reset = {}", id, props.get(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG));

        consumer = new KafkaConsumer<>(props);
        consumer.subscribe(topics);

        running = true;
        startedAt = Instant.now();

        pollExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ingester-kafka-" + id);
            t.setDaemon(true);
            return t;
        });
        pollExecutor.submit(this::pollLoop);

        log.info("[{}] ── Kafka Ingester RUNNING — {} topic(s) subscribed ──", id, topics.size());
    }

    private void pollLoop() {
        log.info("[{}] Kafka poll loop started on thread {}", id, Thread.currentThread().getName());
        long pollCount = 0;
        while (running) {
            try {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                pollCount++;
                if (!records.isEmpty()) {
                    log.info("[{}] ◀ Kafka POLL #{} returned {} record(s)", id, pollCount, records.count());
                }
                for (ConsumerRecord<String, String> record : records) {
                    String source = "topic=" + record.topic() + " partition=" + record.partition()
                            + " offset=" + record.offset();
                    log.info("[{}] ◀ KAFKA MESSAGE — {} ({}B)",
                            id, source, record.value() != null ? record.value().length() : 0);
                    processMessage(record.value(), source);
                }
            } catch (Exception e) {
                if (running) {
                    log.error("[{}] ✗ Kafka poll error: {}", id, e.getMessage());
                    try { Thread.sleep(2000); } catch (InterruptedException ie) { break; }
                }
            }
        }
        log.info("[{}] Kafka poll loop exited after {} polls", id, pollCount);
    }

    @Override
    public void stop() {
        running = false;
        if (pollExecutor != null) pollExecutor.shutdownNow();
        if (consumer != null) {
            try { consumer.close(Duration.ofSeconds(5)); } catch (Exception e) {
                log.debug("[{}] Kafka consumer close error: {}", id, e.getMessage());
            }
        }
        log.info("[{}] Kafka ingester stopped — received={}, submitted={}, failed={}, rejected={}",
                id, requestsReceived.get(), requestsSubmitted.get(),
                requestsFailed.get(), requestsRejected.get());
    }

    @Override
    protected String getSourceDescription() {
        return "kafka://" + bootstrapServers + "/" + String.join(",", topics);
    }
}
