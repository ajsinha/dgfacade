/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential. Patent Pending.
 */
package com.dgfacade.server.handler;

import com.dgfacade.common.model.DGRequest;
import com.dgfacade.common.model.DGResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * <b>PDCHandler</b> — Publish–Deliver–Consume Handler.
 *
 * <p>A specialized handler that orchestrates a request–response–stream pattern:</p>
 * <ol>
 *   <li><b>POST</b> the payload from {@link DGRequest} to a configured REST endpoint</li>
 *   <li><b>Receive</b> a Kafka topic name from the REST response</li>
 *   <li><b>Subscribe</b> to that Kafka topic using the configured Input Channel's broker</li>
 *   <li><b>Publish</b> every consumed message to the configured Output Channel + destination</li>
 *   <li><b>Continue</b> until TTL expires, then gracefully stop</li>
 * </ol>
 *
 * <h3>Handler Config Example</h3>
 * <pre>{@code
 * {
 *   "request_type": "PDC_PROCESS",
 *   "handler_class": "com.dgfacade.server.handler.PDCHandler",
 *   "ttl_minutes": 60,
 *   "config": {
 *     "rest_endpoint": "https://api.example.com/v1/stream-request",
 *     "rest_timeout_seconds": 30,
 *     "input_channel": "pdc-kafka-input",
 *     "output_channel": "pdc-kafka-output",
 *     "output_destination": "results.processed",
 *     "kafka_poll_ms": 100,
 *     "kafka_group_id": "dgfacade-pdc-${request_id}"
 *   }
 * }
 * }</pre>
 *
 * <h3>Flow</h3>
 * <pre>
 *   DGRequest.payload
 *       │
 *       ▼
 *   POST → REST Endpoint → returns { "topic": "dynamic-topic-xyz" }
 *       │
 *       ▼
 *   Subscribe to "dynamic-topic-xyz" via Input Channel broker (Kafka)
 *       │
 *       ▼
 *   For each message received:
 *       publish to Output Channel → output_destination
 *       │
 *       ▼
 *   Continue until TTL → stop &amp; cleanup
 * </pre>
 *
 * <p><b>Note:</b> In this version, the Kafka subscribe/publish is simulated with
 * log statements and in-memory message passing. A production version would use
 * the actual {@code KafkaDataSubscriber} and {@code KafkaDataPublisher} from
 * the messaging module, resolved via the Input/Output Channel configurations.</p>
 */
public class PDCHandler implements DGHandler {

    private static final Logger log = LoggerFactory.getLogger(PDCHandler.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    // ── Configuration fields (populated from HandlerConfig.config) ──
    private String restEndpoint;
    private int restTimeoutSeconds = 30;
    private String inputChannelId;
    private String outputChannelId;
    private String outputDestination;
    private long kafkaPollMs = 100;
    private String kafkaGroupId;

    // ── Runtime state ──
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private final AtomicLong messagesConsumed = new AtomicLong(0);
    private final AtomicLong messagesPublished = new AtomicLong(0);
    private final ConcurrentLinkedQueue<String> publishedMessages = new ConcurrentLinkedQueue<>();
    private String dynamicTopic;
    private HttpClient httpClient;

    @Override
    public void construct(Map<String, Object> config) {
        this.restEndpoint = str(config, "rest_endpoint", null);
        this.restTimeoutSeconds = intVal(config, "rest_timeout_seconds", 30);
        this.inputChannelId = str(config, "input_channel", null);
        this.outputChannelId = str(config, "output_channel", null);
        this.outputDestination = str(config, "output_destination", null);
        this.kafkaPollMs = intVal(config, "kafka_poll_ms", 100);
        this.kafkaGroupId = str(config, "kafka_group_id", "dgfacade-pdc-consumer");

        // Validate required config
        Objects.requireNonNull(restEndpoint, "PDCHandler requires 'rest_endpoint' in config");
        Objects.requireNonNull(inputChannelId, "PDCHandler requires 'input_channel' in config");
        Objects.requireNonNull(outputChannelId, "PDCHandler requires 'output_channel' in config");
        Objects.requireNonNull(outputDestination, "PDCHandler requires 'output_destination' in config");

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(restTimeoutSeconds))
                .build();

        log.info("PDCHandler constructed: restEndpoint={}, inputChannel={}, outputChannel={}, outputDest={}",
                restEndpoint, inputChannelId, outputChannelId, outputDestination);
    }

    @Override
    public DGResponse execute(DGRequest request) {
        running.set(true);
        Instant startTime = Instant.now();
        int ttlMinutes = request.getTtlMinutes() > 0 ? request.getTtlMinutes() : 30;
        Instant deadline = startTime.plus(Duration.ofMinutes(ttlMinutes));

        try {
            // ── Step 1: POST payload to REST endpoint ──
            log.info("PDCHandler: POSTing payload to {}", restEndpoint);
            String payloadJson = mapper.writeValueAsString(
                    request.getPayload() != null ? request.getPayload() : Map.of());

            HttpRequest httpReq = HttpRequest.newBuilder()
                    .uri(URI.create(restEndpoint))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(restTimeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofString(payloadJson))
                    .build();

            HttpResponse<String> httpResp = httpClient.send(httpReq, HttpResponse.BodyHandlers.ofString());

            if (httpResp.statusCode() < 200 || httpResp.statusCode() >= 300) {
                String errMsg = "REST endpoint returned HTTP " + httpResp.statusCode() + ": " + httpResp.body();
                log.error("PDCHandler: {}", errMsg);
                return DGResponse.error(request.getRequestId(), errMsg);
            }

            // ── Step 2: Extract Kafka topic from response ──
            @SuppressWarnings("unchecked")
            Map<String, Object> respBody = mapper.readValue(httpResp.body(), Map.class);
            dynamicTopic = (String) respBody.get("topic");
            if (dynamicTopic == null || dynamicTopic.isBlank()) {
                return DGResponse.error(request.getRequestId(),
                        "REST response missing 'topic' field. Response: " + httpResp.body());
            }

            log.info("PDCHandler: REST returned topic='{}'. Subscribing via input channel '{}'",
                    dynamicTopic, inputChannelId);

            // ── Step 3: Subscribe to the Kafka topic via Input Channel ──
            // In production: resolve inputChannelId → InputChannelService → broker config → KafkaDataSubscriber
            // For now: simulate the consume–publish loop
            log.info("PDCHandler: Starting consume–publish loop. " +
                    "Input: {} / topic={}, Output: {} / dest={}, TTL={}min",
                    inputChannelId, dynamicTopic, outputChannelId, outputDestination, ttlMinutes);

            // ── Step 4: Consume–Publish loop until TTL ──
            while (running.get() && !stopped.get() && Instant.now().isBefore(deadline)) {
                // Simulated: poll messages from the dynamic Kafka topic
                // In production this would call KafkaConsumer.poll(kafkaPollMs)
                String simulatedMessage = simulateKafkaPoll(dynamicTopic);

                if (simulatedMessage != null) {
                    messagesConsumed.incrementAndGet();

                    // ── Step 5: Publish to Output Channel ──
                    // In production: resolve outputChannelId → OutputChannelService → broker → KafkaDataPublisher
                    simulatePublish(outputDestination, simulatedMessage);
                    messagesPublished.incrementAndGet();
                    publishedMessages.add(simulatedMessage);

                    log.debug("PDCHandler: Consumed from {} → Published to {}: {}",
                            dynamicTopic, outputDestination, truncate(simulatedMessage, 100));
                }

                // Avoid busy-wait
                Thread.sleep(kafkaPollMs);
            }

            // ── Build response ──
            log.info("PDCHandler: Loop ended. Consumed={}, Published={}, Duration={}s",
                    messagesConsumed.get(), messagesPublished.get(),
                    Duration.between(startTime, Instant.now()).toSeconds());

            Map<String, Object> resultData = new LinkedHashMap<>();
            resultData.put("dynamic_topic", dynamicTopic);
            resultData.put("input_channel", inputChannelId);
            resultData.put("output_channel", outputChannelId);
            resultData.put("output_destination", outputDestination);
            resultData.put("messages_consumed", messagesConsumed.get());
            resultData.put("messages_published", messagesPublished.get());
            resultData.put("duration_seconds", Duration.between(startTime, Instant.now()).toSeconds());
            resultData.put("stopped_reason", stopped.get() ? "STOPPED" : "TTL_EXPIRED");

            return DGResponse.success(request.getRequestId(), resultData);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return DGResponse.error(request.getRequestId(), "PDCHandler interrupted");
        } catch (Exception e) {
            log.error("PDCHandler execution failed", e);
            return DGResponse.error(request.getRequestId(), "PDCHandler error: " + e.getMessage());
        }
    }

    @Override
    public void stop() {
        log.info("PDCHandler: stop() called — ceasing consume–publish loop");
        stopped.set(true);
        running.set(false);
    }

    @Override
    public void cleanup() {
        running.set(false);
        if (httpClient != null) {
            // HttpClient does not need explicit close in Java 11+
            httpClient = null;
        }
        log.info("PDCHandler: cleanup complete. Total consumed={}, published={}",
                messagesConsumed.get(), messagesPublished.get());
    }

    // ─── Simulation helpers (replace with real Kafka in production) ────────

    /**
     * Simulates polling a Kafka topic. In production, this would use
     * {@code KafkaConsumer<String, String>.poll(Duration.ofMillis(kafkaPollMs))}.
     */
    private String simulateKafkaPoll(String topic) {
        // Simulate: ~10% chance of receiving a message each poll cycle
        if (Math.random() < 0.10) {
            return "{\"source\":\"" + topic + "\",\"seq\":" + messagesConsumed.get() +
                    ",\"ts\":\"" + Instant.now() + "\",\"data\":\"simulated-payload\"}";
        }
        return null;
    }

    /**
     * Simulates publishing to an output destination. In production, this would use
     * {@code KafkaProducer<String, String>.send(new ProducerRecord<>(destination, message))}.
     */
    private void simulatePublish(String destination, String message) {
        log.trace("PDCHandler: [SIMULATED] Publishing to {}: {}", destination, truncate(message, 80));
    }

    // ─── Config helpers ──────────────────────────────────────────────────

    private static String str(Map<String, Object> map, String key, String def) {
        Object val = map.get(key);
        return val != null ? val.toString() : def;
    }

    private static int intVal(Map<String, Object> map, String key, int def) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) { try { return Integer.parseInt(s); } catch (Exception e) { return def; } }
        return def;
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
