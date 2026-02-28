/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential.
 */
package com.dgfacade.server.ingestion;

import jakarta.jms.*;
import org.apache.activemq.ActiveMQConnectionFactory;

import java.time.Instant;
import java.util.*;

/**
 * ActiveMQ (JMS) based request ingester. Reads connection settings from the
 * resolved broker config and queue/topic destinations from the input channel.
 *
 * <p>Resolution chain:</p>
 * <pre>
 *   ingester config → input_channel (destinations: queues/topics) → broker (broker_url, username, password)
 * </pre>
 */
public class ActiveMQRequestIngester extends AbstractRequestIngester {

    private Connection connection;
    private Session session;
    private final List<MessageConsumer> consumers = new ArrayList<>();
    private String brokerUrl;
    private List<Map<String, String>> destinations;

    @Override
    public IngesterType getType() { return IngesterType.ACTIVEMQ; }

    @SuppressWarnings("unchecked")
    @Override
    public void initialize(String id, Map<String, Object> config) {
        super.initialize(id, config);

        // Connection from broker (flattened into resolved config)
        this.brokerUrl = (String) config.getOrDefault("broker_url", "tcp://localhost:61616");

        // Destinations from input channel
        Object destsObj = config.get("destinations");
        if (destsObj instanceof List<?> list) {
            this.destinations = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, String> dest = new HashMap<>();
                    map.forEach((k, v) -> dest.put(String.valueOf(k), String.valueOf(v)));
                    this.destinations.add(dest);
                }
            }
        }
        if (this.destinations == null || this.destinations.isEmpty()) {
            this.destinations = List.of(Map.of("name", "DGFACADE.REQUESTS", "type", "queue"));
        }
    }

    @Override
    public void start() {
        if (running) return;

        // Credentials from broker (flattened)
        String username = (String) config.getOrDefault("username", "admin");
        String password = (String) config.getOrDefault("password", "admin");

        log.info("[{}] ── ActiveMQ Ingester Starting ──", id);
        log.info("[{}]   broker_url  = {}", id, brokerUrl);
        log.info("[{}]   username    = {}", id, username);
        log.info("[{}]   destinations= {}", id, destinations);

        try {
            ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
            log.info("[{}] Creating JMS connection to {}...", id, brokerUrl);
            connection = factory.createConnection(username, password);
            connection.setExceptionListener(ex ->
                    log.error("[{}] ✗ ActiveMQ connection error: {}", id, ex.getMessage()));
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            log.info("[{}] JMS session created (AUTO_ACKNOWLEDGE)", id);

            for (Map<String, String> dest : destinations) {
                String name = dest.getOrDefault("name", "DGFACADE.REQUESTS");
                String type = dest.getOrDefault("type", "queue");

                Destination jmsDest = "topic".equalsIgnoreCase(type)
                        ? session.createTopic(name) : session.createQueue(name);

                MessageConsumer consumer = session.createConsumer(jmsDest);
                consumer.setMessageListener(this::onJmsMessage);
                consumers.add(consumer);
                log.info("[{}] ✓ Subscribed to ActiveMQ {} : {}", id, type.toUpperCase(), name);
            }

            connection.start();
            running = true;
            startedAt = Instant.now();
            log.info("[{}] ── ActiveMQ Ingester RUNNING — broker={}, {} consumer(s) active ──",
                    id, brokerUrl, consumers.size());

        } catch (JMSException e) {
            log.error("[{}] ✗ FAILED to start ActiveMQ ingester: {}", id, e.getMessage(), e);
        }
    }

    private void onJmsMessage(Message message) {
        try {
            String destName = message.getJMSDestination() != null
                    ? message.getJMSDestination().toString() : "unknown";
            String msgId = message.getJMSMessageID();

            if (message instanceof TextMessage txt) {
                String body = txt.getText();
                log.info("[{}] ◀ JMS MESSAGE received — destination={}, jmsMessageId={}, bodyLength={}",
                        id, destName, msgId, body != null ? body.length() : 0);
                processMessage(body, "jms:" + destName);
            } else {
                requestsRejected.incrementAndGet();
                log.warn("[{}] ✗ Ignored non-TextMessage — type={}, destination={}, jmsMessageId={}",
                        id, message.getClass().getSimpleName(), destName, msgId);
            }
        } catch (JMSException e) {
            requestsFailed.incrementAndGet();
            log.error("[{}] ✗ Error reading JMS message: {}", id, e.getMessage(), e);
        }
    }

    @Override
    public void stop() {
        running = false;
        for (MessageConsumer c : consumers) {
            try { c.close(); } catch (JMSException ignored) {}
        }
        consumers.clear();
        try { if (session != null) session.close(); } catch (JMSException ignored) {}
        try { if (connection != null) connection.close(); } catch (JMSException ignored) {}
        log.info("[{}] ActiveMQ ingester stopped — received={}, submitted={}, failed={}, rejected={}",
                id, requestsReceived.get(), requestsSubmitted.get(),
                requestsFailed.get(), requestsRejected.get());
    }

    @Override
    protected String getSourceDescription() {
        List<String> labels = new ArrayList<>();
        for (Map<String, String> d : destinations) {
            labels.add(d.getOrDefault("type", "queue") + "://" + d.getOrDefault("name", "?"));
        }
        return "activemq://" + brokerUrl.replaceFirst("tcp://", "") + " [" + String.join(", ", labels) + "]";
    }
}
