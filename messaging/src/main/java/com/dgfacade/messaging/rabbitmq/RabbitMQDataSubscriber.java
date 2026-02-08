/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential. Patent Pending.
 */
package com.dgfacade.messaging.rabbitmq;

import com.dgfacade.messaging.core.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeoutException;

/**
 * RabbitMQ DataSubscriber using AMQP 0-9-1.
 * Consumes from queues with automatic acknowledgement and backpressure support.
 * Supports SSL/TLS via PEM certificates and JKS/PKCS12 keystores.
 */
public class RabbitMQDataSubscriber extends AbstractSubscriber {

    private com.rabbitmq.client.Connection connection;
    private com.rabbitmq.client.Channel channel;
    private final Map<String, String> consumerTags = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    protected void doConnect() {
        try {
            com.rabbitmq.client.ConnectionFactory factory = new com.rabbitmq.client.ConnectionFactory();
            factory.setHost((String) config.getOrDefault("host", "localhost"));
            factory.setPort(((Number) config.getOrDefault("port", 5672)).intValue());
            factory.setVirtualHost((String) config.getOrDefault("virtual_host", "/"));
            factory.setUsername((String) config.getOrDefault("username", "guest"));
            factory.setPassword((String) config.getOrDefault("password", "guest"));
            factory.setConnectionTimeout(((Number) config.getOrDefault("connection_timeout", 30000)).intValue());
            factory.setRequestedHeartbeat(((Number) config.getOrDefault("heartbeat", 60)).intValue());
            factory.setAutomaticRecoveryEnabled(true);
            factory.setNetworkRecoveryInterval(
                    ((Number) config.getOrDefault("recovery_interval_ms", 5000)).longValue());

            @SuppressWarnings("unchecked")
            Map<String, Object> ssl = (Map<String, Object>) config.get("ssl");
            if (ssl != null && Boolean.parseBoolean(String.valueOf(ssl.getOrDefault("enabled", "false")))) {
                factory.useSslProtocol(SslHelper.createSslContext(ssl));
                factory.setPort(((Number) config.getOrDefault("ssl_port", 5671)).intValue());
                log.info("RabbitMQ subscriber SSL/TLS enabled");
            }

            connection = factory.newConnection("dgfacade-subscriber");
            channel = connection.createChannel();
            int prefetch = ((Number) config.getOrDefault("prefetch_count", 100)).intValue();
            channel.basicQos(prefetch);
            state = ConnectionState.CONNECTED;
            log.info("RabbitMQ subscriber connected to {}:{}{} (prefetch={})",
                    factory.getHost(), factory.getPort(), factory.getVirtualHost(), prefetch);
        } catch (Exception e) {
            log.error("Failed to connect RabbitMQ subscriber", e);
            state = ConnectionState.ERROR;
            scheduleReconnect();
        }
    }

    @Override
    protected void doSubscribe(String queueName) {
        try {
            // Declare queue idempotently (durable, non-exclusive, non-auto-delete)
            channel.queueDeclare(queueName, true, false, false, null);

            String tag = channel.basicConsume(queueName, false, "dgfacade-" + queueName,
                    new com.rabbitmq.client.DefaultConsumer(channel) {
                        @Override
                        public void handleDelivery(String consumerTag, com.rabbitmq.client.Envelope envelope,
                                                   com.rabbitmq.client.AMQP.BasicProperties properties, byte[] body)
                                throws IOException {
                            MessageEnvelope msg = new MessageEnvelope();
                            msg.setTopic(queueName);
                            msg.setPayload(new String(body, StandardCharsets.UTF_8));
                            msg.setMessageId(properties.getCorrelationId());
                            if (properties.getHeaders() != null) {
                                Map<String, String> headers = new HashMap<>();
                                properties.getHeaders().forEach((k, v) -> headers.put(k, String.valueOf(v)));
                                msg.setHeaders(headers);
                            }
                            enqueue(msg);
                            channel.basicAck(envelope.getDeliveryTag(), false);
                        }
                    });
            consumerTags.put(queueName, tag);
            log.info("RabbitMQ subscribed to queue: {}", queueName);
        } catch (IOException e) {
            log.error("Failed to subscribe to RabbitMQ queue: {}", queueName, e);
        }
    }

    @Override
    protected void doUnsubscribe(String queueName) {
        String tag = consumerTags.remove(queueName);
        if (tag != null) {
            try {
                channel.basicCancel(tag);
            } catch (IOException e) {
                log.warn("Error cancelling RabbitMQ consumer for {}", queueName, e);
            }
        }
    }

    @Override
    protected void doDisconnect() {
        try {
            if (channel != null && channel.isOpen()) channel.close();
            if (connection != null && connection.isOpen()) connection.close();
        } catch (IOException | TimeoutException e) {
            log.warn("Error closing RabbitMQ subscriber", e);
        }
    }

    @Override
    public boolean isConnected() {
        return state == ConnectionState.CONNECTED && connection != null && connection.isOpen();
    }
}
