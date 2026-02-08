/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential. Patent Pending.
 */
package com.dgfacade.messaging.ibmmq;

import com.dgfacade.messaging.core.*;
import com.ibm.mq.jakarta.jms.MQConnectionFactory;
import com.ibm.msg.client.jakarta.wmq.WMQConstants;
import jakarta.jms.*;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * IBM MQ DataPublisher using JMS (com.ibm.mq.jakarta.client).
 * Connects via queue manager, channel, and host/port.
 * Supports SSL/TLS via PEM certificates, JKS keystores, and CipherSpec configuration.
 *
 * <p>Required config fields:</p>
 * <pre>
 *   host, port, queue_manager, channel
 *   Optional: username, password, ssl {...}
 * </pre>
 */
public class IbmMQDataPublisher extends AbstractPublisher {

    private Connection connection;
    private Session session;

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

            if (config.containsKey("ccsid")) {
                factory.setCCSID(((Number) config.get("ccsid")).intValue());
            }

            // SSL/TLS configuration
            @SuppressWarnings("unchecked")
            Map<String, Object> ssl = (Map<String, Object>) config.get("ssl");
            if (ssl != null && Boolean.parseBoolean(String.valueOf(ssl.getOrDefault("enabled", "false")))) {
                configureSsl(factory, ssl);
            }

            String username = (String) config.get("username");
            String password = (String) config.get("password");
            if (username != null && password != null) {
                connection = factory.createConnection(username, password);
            } else {
                connection = factory.createConnection();
            }

            connection.setExceptionListener(ex -> {
                log.error("IBM MQ connection error", ex);
                scheduleReconnect();
            });
            connection.start();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            state = ConnectionState.CONNECTED;

            log.info("IBM MQ publisher connected to {}:{} QM={} CH={}",
                    config.get("host"), config.get("port"),
                    config.get("queue_manager"), config.get("channel"));
        } catch (Exception e) {
            log.error("Failed to connect IBM MQ publisher", e);
            state = ConnectionState.ERROR;
            scheduleReconnect();
        }
    }

    private void configureSsl(MQConnectionFactory factory, Map<String, Object> ssl) throws Exception {
        String cipherSuite = (String) ssl.getOrDefault("cipher_suite", "TLS_AES_256_GCM_SHA384");
        factory.setSSLCipherSuite(cipherSuite);

        javax.net.ssl.SSLContext sslContext = SslHelper.createSslContext(ssl);
        String format = String.valueOf(ssl.getOrDefault("format", "JKS")).toUpperCase();
        if ("PEM".equals(format)) {
            // PEM-based: set the default SSLContext for the JVM
            javax.net.ssl.SSLContext.setDefault(sslContext);
            log.info("IBM MQ SSL configured with PEM certificates, cipher={}", cipherSuite);
        } else {
            // JKS/PKCS12: set system properties for IBM MQ client
            if (ssl.containsKey("keystore_path")) {
                System.setProperty("javax.net.ssl.keyStore", (String) ssl.get("keystore_path"));
                System.setProperty("javax.net.ssl.keyStorePassword",
                        String.valueOf(ssl.getOrDefault("keystore_password", "")));
                System.setProperty("javax.net.ssl.keyStoreType",
                        String.valueOf(ssl.getOrDefault("keystore_type", "JKS")));
            }
            if (ssl.containsKey("truststore_path")) {
                System.setProperty("javax.net.ssl.trustStore", (String) ssl.get("truststore_path"));
                System.setProperty("javax.net.ssl.trustStorePassword",
                        String.valueOf(ssl.getOrDefault("truststore_password", "")));
                System.setProperty("javax.net.ssl.trustStoreType",
                        String.valueOf(ssl.getOrDefault("truststore_type", "JKS")));
            }
            log.info("IBM MQ SSL configured with keystore, cipher={}", cipherSuite);
        }
    }

    @Override
    protected CompletableFuture<Void> doPublish(String topic, MessageEnvelope envelope) {
        return CompletableFuture.runAsync(() -> {
            try {
                Destination dest;
                if (topic.startsWith("topic://")) {
                    dest = session.createTopic(topic.substring("topic://".length()));
                } else {
                    String queueName = topic.startsWith("queue://") ? topic.substring("queue://".length()) : topic;
                    dest = session.createQueue(queueName);
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
                throw new RuntimeException("IBM MQ publish failed", e);
            }
        });
    }

    @Override
    protected void doDisconnect() {
        try {
            if (session != null) session.close();
            if (connection != null) connection.close();
        } catch (JMSException e) {
            log.warn("Error closing IBM MQ publisher", e);
        }
    }

    @Override
    public boolean isConnected() {
        return state == ConnectionState.CONNECTED && connection != null;
    }
}
