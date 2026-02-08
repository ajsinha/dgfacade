/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential. Patent Pending.
 */
package com.dgfacade.messaging.activemq;

import com.dgfacade.messaging.core.*;
import jakarta.jms.*;
import org.apache.activemq.ActiveMQConnectionFactory;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ActiveMQ-based DataSubscriber using JMS MessageListener.
 * Event-driven. Supports dynamic topic/queue subscription at runtime.
 * Implements backpressure by pausing the JMS connection when queue is full.
 */
public class ActiveMQDataSubscriber extends AbstractSubscriber {

    private Connection connection;
    private Session session;
    private final Map<String, MessageConsumer> consumers = new ConcurrentHashMap<>();

    @Override
    protected void doConnect() {
        try {
            String brokerUrl = (String) config.getOrDefault("broker_url", "tcp://localhost:61616");
            String username = (String) config.getOrDefault("username", "admin");
            String password = (String) config.getOrDefault("password", "admin");

            ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
            connection = factory.createConnection(username, password);
            connection.setExceptionListener(ex -> {
                log.error("ActiveMQ subscriber connection error", ex);
                scheduleReconnect();
            });
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            connection.start();
            state = ConnectionState.CONNECTED;
            log.info("ActiveMQ subscriber connected to {}", brokerUrl);
        } catch (JMSException e) {
            log.error("Failed to connect ActiveMQ subscriber", e);
            scheduleReconnect();
        }
    }

    @Override
    protected void doSubscribe(String topicOrQueue) {
        try {
            Destination dest;
            if (topicOrQueue.startsWith("queue://")) {
                dest = session.createQueue(topicOrQueue.substring("queue://".length()));
            } else {
                dest = session.createTopic(topicOrQueue.startsWith("topic://") ?
                        topicOrQueue.substring("topic://".length()) : topicOrQueue);
            }
            MessageConsumer consumer = session.createConsumer(dest);
            consumer.setMessageListener(jmsMessage -> {
                try {
                    if (jmsMessage instanceof TextMessage txt) {
                        MessageEnvelope envelope = new MessageEnvelope(topicOrQueue, txt.getText());
                        envelope.setMessageId(txt.getJMSMessageID());
                        enqueue(envelope);
                    }
                } catch (JMSException e) {
                    log.error("Error processing ActiveMQ message", e);
                }
            });
            consumers.put(topicOrQueue, consumer);
        } catch (JMSException e) {
            log.error("Failed to subscribe to {}", topicOrQueue, e);
        }
    }

    @Override
    protected void doUnsubscribe(String topicOrQueue) {
        MessageConsumer consumer = consumers.remove(topicOrQueue);
        if (consumer != null) {
            try { consumer.close(); } catch (JMSException e) { log.warn("Error closing consumer", e); }
        }
    }

    @Override
    protected void doDisconnect() {
        consumers.values().forEach(c -> { try { c.close(); } catch (JMSException e) {} });
        consumers.clear();
        try {
            if (session != null) session.close();
            if (connection != null) connection.close();
        } catch (JMSException e) {
            log.warn("Error closing ActiveMQ subscriber connection", e);
        }
    }

    @Override
    public boolean isConnected() { return state == ConnectionState.CONNECTED && connection != null; }
}
