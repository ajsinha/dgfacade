/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential.
 */
package com.dgfacade.server.handler;

import com.dgfacade.common.model.DGRequest;
import com.dgfacade.common.model.DGResponse;
import com.dgfacade.messaging.core.DataPublisher;
import com.dgfacade.messaging.core.DataSubscriber;
import com.dgfacade.messaging.core.MessageEnvelope;
import com.dgfacade.server.channel.ChannelAccessor;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * <b>PDCHandler</b> — Publish–Deliver–Consume Handler.
 *
 * <p>Orchestrates a complete request–subscribe–forward pattern using real
 * Input and Output Channels via {@link ChannelAccessor}:</p>
 * <ol>
 *   <li><b>POST</b> the request payload to a configured REST endpoint</li>
 *   <li><b>Receive</b> a topic name from the REST response (or use a configured static topic)</li>
 *   <li><b>Subscribe</b> to that topic via the configured Input Channel's real broker</li>
 *   <li><b>Publish</b> every consumed message to the configured Output Channel + destination</li>
 *   <li><b>Continue</b> until TTL expires or stop is called, then gracefully close</li>
 * </ol>
 *
 * <h3>Handler Config</h3>
 * <pre>{@code
 * {
 *   "request_type": "PDC_PROCESS",
 *   "handler_class": "com.dgfacade.server.handler.PDCHandler",
 *   "ttl_minutes": 60,
 *   "config": {
 *     "rest_endpoint": "https://api.example.com/v1/stream-request",
 *     "rest_timeout_seconds": 30,
 *     "input_channel": "pdc-kafka-input",
 *     "input_topic": "orders.raw",
 *     "output_channel": "pdc-kafka-output",
 *     "output_topic": "orders.processed",
 *     "use_dynamic_topic": true,
 *     "kafka_group_id": "dgfacade-pdc-${request_id}"
 *   }
 * }
 * }</pre>
 *
 * <h3>Topic Resolution</h3>
 * <ul>
 *   <li>If {@code use_dynamic_topic} is true (default), the handler POSTs to the REST
 *       endpoint and reads the {@code topic} field from the response JSON.</li>
 *   <li>If {@code use_dynamic_topic} is false, the handler subscribes to the
 *       statically configured {@code input_topic}.</li>
 *   <li>The output always publishes to {@code output_topic}.</li>
 * </ul>
 *
 * <h3>Channel Access</h3>
 * <p>The handler receives a {@link ChannelAccessor} from the execution engine
 * before {@code construct()} is called. The accessor resolves channel IDs to
 * real broker connections (Kafka, ActiveMQ, RabbitMQ, IBM MQ, FileSystem, SQL)
 * and provides {@link DataPublisher} and {@link DataSubscriber} instances.</p>
 */
public class PDCHandler implements DGHandler {

    private static final Logger log = LoggerFactory.getLogger(PDCHandler.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    // -- Channel Access --
    private ChannelAccessor channelAccessor;

    // -- Configuration fields (populated from HandlerConfig.config) --
    private String restEndpoint;
    private int restTimeoutSeconds = 30;
    private String inputChannelId;
    private String inputTopic;
    private String outputChannelId;
    private String outputTopic;
    private boolean useDynamicTopic = true;
    private String kafkaGroupId;

    // -- Runtime state --
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private final AtomicLong messagesConsumed = new AtomicLong(0);
    private final AtomicLong messagesPublished = new AtomicLong(0);
    private final AtomicLong messagesErrored = new AtomicLong(0);
    private final ConcurrentLinkedQueue<String> sampleMessages = new ConcurrentLinkedQueue<>();
    private String subscribedTopic;
    private HttpClient httpClient;
    private DataSubscriber subscriber;
    private DataPublisher publisher;

    @Override
    public void setChannelAccessor(ChannelAccessor accessor) {
        this.channelAccessor = accessor;
    }

    @Override
    public void construct(Map<String, Object> config) {
        this.restEndpoint = str(config, "rest_endpoint", null);
        this.restTimeoutSeconds = intVal(config, "rest_timeout_seconds", 30);
        this.inputChannelId = str(config, "input_channel", null);
        this.inputTopic = str(config, "input_topic", null);
        this.outputChannelId = str(config, "output_channel", null);
        this.outputTopic = str(config, "output_topic", null);
        this.useDynamicTopic = Boolean.parseBoolean(str(config, "use_dynamic_topic", "true"));
        this.kafkaGroupId = str(config, "kafka_group_id", "dgfacade-pdc-consumer");

        // Validate required config
        Objects.requireNonNull(inputChannelId, "PDCHandler requires 'input_channel' in config");
        Objects.requireNonNull(outputChannelId, "PDCHandler requires 'output_channel' in config");
        Objects.requireNonNull(outputTopic, "PDCHandler requires 'output_topic' in config");

        if (useDynamicTopic) {
            Objects.requireNonNull(restEndpoint,
                    "PDCHandler requires 'rest_endpoint' when use_dynamic_topic=true");
        } else {
            Objects.requireNonNull(inputTopic,
                    "PDCHandler requires 'input_topic' when use_dynamic_topic=false");
        }

        if (channelAccessor == null) {
            throw new IllegalStateException(
                    "PDCHandler requires ChannelAccessor. Ensure BrokerService and Channel configs are set up.");
        }

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(restTimeoutSeconds))
                .build();

        log.info("PDCHandler constructed: inputChannel={}, outputChannel={}, outputTopic={}, dynamic={}",
                inputChannelId, outputChannelId, outputTopic, useDynamicTopic);
    }

    @Override
    public DGResponse execute(DGRequest request) {
        running.set(true);
        Instant startTime = Instant.now();
        int ttlMinutes = request.getTtlMinutes() > 0 ? request.getTtlMinutes() : 30;
        Instant deadline = startTime.plus(Duration.ofMinutes(ttlMinutes));

        try {
            // -- Step 1: Determine the input topic --
            if (useDynamicTopic) {
                subscribedTopic = resolveDynamicTopic(request);
                if (subscribedTopic == null) {
                    return DGResponse.error(request.getRequestId(),
                            "Failed to resolve dynamic topic from REST endpoint");
                }
            } else {
                subscribedTopic = inputTopic;
            }

            log.info("PDCHandler: subscribing to topic '{}' via input channel '{}'",
                    subscribedTopic, inputChannelId);

            // -- Step 2: Get real DataSubscriber from Input Channel --
            subscriber = channelAccessor.getSubscriber(inputChannelId);

            // -- Step 3: Get real DataPublisher from Output Channel --
            publisher = channelAccessor.getPublisher(outputChannelId);

            // -- Step 4: Subscribe and forward messages --
            log.info("PDCHandler: starting consume-publish loop. " +
                    "Input: {}/topic={}, Output: {}/topic={}, TTL={}min",
                    inputChannelId, subscribedTopic, outputChannelId, outputTopic, ttlMinutes);

            CountDownLatch completionLatch = new CountDownLatch(1);

            // Subscribe with a real MessageListener that forwards to the output
            subscriber.subscribe(subscribedTopic, envelope -> {
                if (!running.get() || stopped.get()) return;

                messagesConsumed.incrementAndGet();
                String payload = envelope.getPayload();

                try {
                    // Create output envelope
                    MessageEnvelope outEnvelope = new MessageEnvelope(outputTopic, payload);
                    outEnvelope.setHeaders(Map.of(
                            "pdc_source_topic", subscribedTopic,
                            "pdc_input_channel", inputChannelId,
                            "pdc_request_id", request.getRequestId(),
                            "pdc_forwarded_at", Instant.now().toString()
                    ));

                    // Publish to output channel
                    publisher.publish(outputTopic, outEnvelope)
                            .thenRun(() -> {
                                messagesPublished.incrementAndGet();
                                if (sampleMessages.size() < 10) {
                                    sampleMessages.add(truncate(payload, 200));
                                }
                                log.debug("PDCHandler: forwarded message from {} -> {}",
                                        subscribedTopic, outputTopic);
                            })
                            .exceptionally(ex -> {
                                messagesErrored.incrementAndGet();
                                log.error("PDCHandler: failed to publish to {}: {}",
                                        outputTopic, ex.getMessage());
                                return null;
                            });

                } catch (Exception e) {
                    messagesErrored.incrementAndGet();
                    log.error("PDCHandler: error processing message: {}", e.getMessage());
                }
            });

            // Start the subscriber
            subscriber.start();

            // -- Step 5: Wait until TTL expires or stop() is called --
            while (running.get() && !stopped.get() && Instant.now().isBefore(deadline)) {
                try {
                    if (completionLatch.await(1, TimeUnit.SECONDS)) break;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            // -- Build response --
            Duration elapsed = Duration.between(startTime, Instant.now());
            log.info("PDCHandler: loop ended. Consumed={}, Published={}, Errored={}, Duration={}s",
                    messagesConsumed.get(), messagesPublished.get(),
                    messagesErrored.get(), elapsed.toSeconds());

            Map<String, Object> resultData = new LinkedHashMap<>();
            resultData.put("subscribed_topic", subscribedTopic);
            resultData.put("input_channel", inputChannelId);
            resultData.put("output_channel", outputChannelId);
            resultData.put("output_topic", outputTopic);
            resultData.put("messages_consumed", messagesConsumed.get());
            resultData.put("messages_published", messagesPublished.get());
            resultData.put("messages_errored", messagesErrored.get());
            resultData.put("duration_seconds", elapsed.toSeconds());
            resultData.put("stopped_reason", stopped.get() ? "STOPPED" : "TTL_EXPIRED");
            resultData.put("dynamic_topic_used", useDynamicTopic);
            if (!sampleMessages.isEmpty()) {
                resultData.put("sample_messages", new ArrayList<>(sampleMessages));
            }

            return DGResponse.success(request.getRequestId(), resultData);

        } catch (Exception e) {
            log.error("PDCHandler execution failed", e);
            return DGResponse.error(request.getRequestId(), "PDCHandler error: " + e.getMessage());
        }
    }

    @Override
    public void stop() {
        log.info("PDCHandler: stop() called");
        stopped.set(true);
        running.set(false);
    }

    @Override
    public void cleanup() {
        running.set(false);

        if (subscriber != null && subscribedTopic != null) {
            try {
                subscriber.unsubscribe(subscribedTopic);
                log.info("PDCHandler: unsubscribed from topic '{}'", subscribedTopic);
            } catch (Exception e) {
                log.warn("PDCHandler: error unsubscribing: {}", e.getMessage());
            }
        }

        if (publisher != null) {
            try {
                publisher.flush();
                log.info("PDCHandler: publisher flushed");
            } catch (Exception e) {
                log.warn("PDCHandler: error flushing publisher: {}", e.getMessage());
            }
        }

        // Do NOT close publisher/subscriber — they are cached in ChannelAccessor

        if (httpClient != null) httpClient = null;

        log.info("PDCHandler: cleanup complete. consumed={}, published={}, errored={}",
                messagesConsumed.get(), messagesPublished.get(), messagesErrored.get());
    }

    // --- Dynamic topic resolution via REST ---

    private String resolveDynamicTopic(DGRequest request) {
        try {
            log.info("PDCHandler: POSTing to {} to resolve dynamic topic", restEndpoint);
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
                log.error("PDCHandler: REST returned HTTP {}: {}",
                        httpResp.statusCode(), httpResp.body());
                return null;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> respBody = mapper.readValue(httpResp.body(), Map.class);
            String topic = (String) respBody.get("topic");
            if (topic == null || topic.isBlank()) {
                log.error("PDCHandler: REST response missing 'topic'. Response: {}",
                        httpResp.body());
                return null;
            }

            log.info("PDCHandler: REST returned dynamic topic='{}'", topic);
            return topic;

        } catch (Exception e) {
            log.error("PDCHandler: failed to resolve dynamic topic: {}", e.getMessage());
            return null;
        }
    }

    // --- Config helpers ---

    private static String str(Map<String, Object> map, String key, String def) {
        Object val = map.get(key);
        return val != null ? val.toString() : def;
    }

    private static int intVal(Map<String, Object> map, String key, int def) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) {
            try { return Integer.parseInt(s); } catch (Exception e) { return def; }
        }
        return def;
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
