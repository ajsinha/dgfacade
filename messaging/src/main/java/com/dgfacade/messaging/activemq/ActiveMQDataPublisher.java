/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential. Patent Pending.
 */
package com.dgfacade.messaging.activemq;

import com.dgfacade.messaging.core.*;
import jakarta.jms.*;
import org.apache.activemq.ActiveMQConnectionFactory;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * ActiveMQ-based DataPublisher using JMS. Supports both topics and queues.
 * Auto-recovers from connection failures with configurable reconnection interval.
 */
public class ActiveMQDataPublisher extends AbstractPublisher {

    private Connection connection;
    private Session session;

    @Override
    protected void doConnect() {
        try {
            String brokerUrl = (String) config.getOrDefault("broker_url", "tcp://localhost:61616");
            String username = (String) config.getOrDefault("username", "admin");
            String password = (String) config.getOrDefault("password", "admin");

            ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
            connection = factory.createConnection(username, password);
            connection.setExceptionListener(ex -> {
                log.error("ActiveMQ connection error", ex);
                scheduleReconnect();
            });
            connection.start();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            state = ConnectionState.CONNECTED;
            log.info("ActiveMQ producer connected to {}", brokerUrl);
        } catch (JMSException e) {
            log.error("Failed to connect to ActiveMQ", e);
            scheduleReconnect();
        }
    }

    @Override
    protected CompletableFuture<Void> doPublish(String topic, MessageEnvelope envelope) {
        return CompletableFuture.runAsync(() -> {
            try {
                Destination dest;
                if (topic.startsWith("queue://")) {
                    dest = session.createQueue(topic.substring("queue://".length()));
                } else {
                    dest = session.createTopic(topic.startsWith("topic://") ? topic.substring("topic://".length()) : topic);
                }
                MessageProducer producer = session.createProducer(dest);
                producer.setDeliveryMode(DeliveryMode.PERSISTENT);
                TextMessage message = session.createTextMessage(envelope.getPayload());
                message.setJMSCorrelationID(envelope.getMessageId());
                if (envelope.getHeaders() != null) {
                    for (Map.Entry<String, String> h : envelope.getHeaders().entrySet()) {
                        message.setStringProperty(h.getKey(), h.getValue());
                    }
                }
                producer.send(message);
                producer.close();
            } catch (JMSException e) {
                throw new RuntimeException("ActiveMQ publish failed", e);
            }
        });
    }

    @Override
    protected void doDisconnect() {
        try {
            if (session != null) session.close();
            if (connection != null) connection.close();
        } catch (JMSException e) {
            log.warn("Error closing ActiveMQ connection", e);
        }
    }

    @Override
    public boolean isConnected() { return state == ConnectionState.CONNECTED && connection != null; }
}
