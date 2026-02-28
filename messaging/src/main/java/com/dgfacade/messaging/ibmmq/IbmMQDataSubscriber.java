/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential.
 */
package com.dgfacade.messaging.ibmmq;

import com.dgfacade.messaging.core.*;
import com.ibm.mq.jakarta.jms.MQConnectionFactory;
import com.ibm.msg.client.jakarta.wmq.WMQConstants;
import jakarta.jms.*;
import java.util.*;

/**
 * IBM MQ DataSubscriber using JMS.
 * Listens on IBM MQ queues/topics with automatic reconnection
 * and backpressure-aware consumption.
 * Supports SSL/TLS via PEM certificates and JKS/PKCS12 keystores.
 */
public class IbmMQDataSubscriber extends AbstractSubscriber {

    private Connection connection;
    private Session session;
    private final Map<String, MessageConsumer> consumers = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    protected void doConnect() {
        try {
            MQConnectionFactory factory = new MQConnectionFactory();
            factory.setHostName((String) config.getOrDefault("host", "localhost"));
            factory.setPort(((Number) config.getOrDefault("port", 1414)).intValue());
            factory.setQueueManager((String) config.getOrDefault("queue_manager", "QM1"));
            factory.setChannel((String) config.getOrDefault("channel", "DEV.APP.SVRCONN"));
            factory.setTransportType(WMQConstants.WMQ_CM_CLIENT);
            factory.setAppName("DGFacade");

            @SuppressWarnings("unchecked")
            Map<String, Object> ssl = (Map<String, Object>) config.get("ssl");
            if (ssl != null && Boolean.parseBoolean(String.valueOf(ssl.getOrDefault("enabled", "false")))) {
                String cipherSuite = (String) ssl.getOrDefault("cipher_suite", "TLS_AES_256_GCM_SHA384");
                factory.setSSLCipherSuite(cipherSuite);
                javax.net.ssl.SSLContext sslContext = SslHelper.createSslContext(ssl);
                String format = String.valueOf(ssl.getOrDefault("format", "JKS")).toUpperCase();
                if ("PEM".equals(format)) {
                    javax.net.ssl.SSLContext.setDefault(sslContext);
                } else {
                    if (ssl.containsKey("keystore_path")) {
                        System.setProperty("javax.net.ssl.keyStore", (String) ssl.get("keystore_path"));
                        System.setProperty("javax.net.ssl.keyStorePassword",
                                String.valueOf(ssl.getOrDefault("keystore_password", "")));
                    }
                    if (ssl.containsKey("truststore_path")) {
                        System.setProperty("javax.net.ssl.trustStore", (String) ssl.get("truststore_path"));
                        System.setProperty("javax.net.ssl.trustStorePassword",
                                String.valueOf(ssl.getOrDefault("truststore_password", "")));
                    }
                }
                log.info("IBM MQ subscriber SSL configured, cipher={}", cipherSuite);
            }

            String username = (String) config.get("username");
            String password = (String) config.get("password");
            if (username != null && password != null) {
                connection = factory.createConnection(username, password);
            } else {
                connection = factory.createConnection();
            }
            connection.setExceptionListener(ex -> {
                log.error("IBM MQ subscriber connection error", ex);
                scheduleReconnect();
            });
            connection.start();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            state = ConnectionState.CONNECTED;

            log.info("IBM MQ subscriber connected to {}:{} QM={} CH={}",
                    config.get("host"), config.get("port"),
                    config.get("queue_manager"), config.get("channel"));
        } catch (Exception e) {
            log.error("Failed to connect IBM MQ subscriber", e);
            state = ConnectionState.ERROR;
            scheduleReconnect();
        }
    }

    @Override
    protected void doSubscribe(String topicOrQueue) {
        try {
            Destination dest;
            if (topicOrQueue.startsWith("topic://")) {
                dest = session.createTopic(topicOrQueue.substring("topic://".length()));
            } else {
                String name = topicOrQueue.startsWith("queue://") ?
                        topicOrQueue.substring("queue://".length()) : topicOrQueue;
                dest = session.createQueue(name);
            }
            MessageConsumer consumer = session.createConsumer(dest);
            consumer.setMessageListener(jmsMessage -> {
                try {
                    MessageEnvelope envelope = new MessageEnvelope();
                    envelope.setTopic(topicOrQueue);
                    if (jmsMessage instanceof TextMessage txt) {
                        envelope.setPayload(txt.getText());
                    } else if (jmsMessage instanceof BytesMessage bytes) {
                        byte[] data = new byte[(int) bytes.getBodyLength()];
                        bytes.readBytes(data);
                        envelope.setPayload(new String(data, java.nio.charset.StandardCharsets.UTF_8));
                    }
                    envelope.setMessageId(jmsMessage.getJMSCorrelationID());
                    enqueue(envelope);
                } catch (JMSException e) {
                    log.error("Error processing IBM MQ message from {}", topicOrQueue, e);
                }
            });
            consumers.put(topicOrQueue, consumer);
            log.info("IBM MQ subscribed to: {}", topicOrQueue);
        } catch (JMSException e) {
            log.error("Failed to subscribe to IBM MQ destination: {}", topicOrQueue, e);
        }
    }

    @Override
    protected void doUnsubscribe(String topicOrQueue) {
        MessageConsumer consumer = consumers.remove(topicOrQueue);
        if (consumer != null) {
            try { consumer.close(); } catch (JMSException e) {
                log.warn("Error closing IBM MQ consumer for {}", topicOrQueue, e);
            }
        }
    }

    @Override
    protected void doDisconnect() {
        consumers.values().forEach(c -> { try { c.close(); } catch (JMSException e) { /*ignore*/ } });
        consumers.clear();
        try {
            if (session != null) session.close();
            if (connection != null) connection.close();
        } catch (JMSException e) {
            log.warn("Error closing IBM MQ subscriber", e);
        }
    }

    @Override
    public boolean isConnected() {
        return state == ConnectionState.CONNECTED && connection != null;
    }
}
