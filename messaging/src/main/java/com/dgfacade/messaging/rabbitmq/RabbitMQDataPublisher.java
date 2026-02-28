/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential.
 */
package com.dgfacade.messaging.rabbitmq;

import com.dgfacade.messaging.core.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

/**
 * RabbitMQ DataPublisher using AMQP 0-9-1.
 * Supports SSL/TLS via PEM certificates and JKS/PKCS12 keystores.
 */
public class RabbitMQDataPublisher extends AbstractPublisher {

    private com.rabbitmq.client.Connection connection;
    private com.rabbitmq.client.Channel channel;

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
            // Connection recovery: automatic reconnection on connection loss
            factory.setAutomaticRecoveryEnabled(true);
            factory.setNetworkRecoveryInterval(
                    ((Number) config.getOrDefault("network_recovery_interval_ms", 10000)).longValue());
            factory.setTopologyRecoveryEnabled(true);

            @SuppressWarnings("unchecked")
            Map<String, Object> ssl = (Map<String, Object>) config.get("ssl");
            if (ssl != null && Boolean.parseBoolean(String.valueOf(ssl.getOrDefault("enabled", "false")))) {
                factory.useSslProtocol(SslHelper.createSslContext(ssl));
                factory.setPort(((Number) config.getOrDefault("ssl_port", 5671)).intValue());
                log.info("RabbitMQ SSL/TLS enabled");
            }

            connection = factory.newConnection("dgfacade-publisher");
            channel = connection.createChannel();
            state = ConnectionState.CONNECTED;
            log.info("RabbitMQ publisher connected to {}:{}{}", factory.getHost(), factory.getPort(), factory.getVirtualHost());
        } catch (Exception e) {
            log.error("Failed to connect RabbitMQ publisher", e);
            state = ConnectionState.ERROR;
            scheduleReconnect();
        }
    }

    @Override
    protected CompletableFuture<Void> doPublish(String topic, MessageEnvelope envelope) {
        return CompletableFuture.runAsync(() -> {
            try {
                String exchange = (String) config.getOrDefault("exchange", "");
                var propsBuilder = new com.rabbitmq.client.AMQP.BasicProperties.Builder()
                        .correlationId(envelope.getMessageId())
                        .timestamp(Date.from(envelope.getTimestamp()))
                        .contentType("application/json");
                if (envelope.getHeaders() != null) {
                    Map<String, Object> h = new HashMap<>();
                    envelope.getHeaders().forEach(h::put);
                    propsBuilder.headers(h);
                }
                channel.basicPublish(exchange, topic, propsBuilder.build(),
                        envelope.getPayload().getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new RuntimeException("RabbitMQ publish failed", e);
            }
        });
    }

    @Override
    protected void doDisconnect() {
        try {
            if (channel != null && channel.isOpen()) channel.close();
            if (connection != null && connection.isOpen()) connection.close();
        } catch (IOException | TimeoutException e) {
            log.warn("Error closing RabbitMQ publisher", e);
        }
    }

    @Override
    public boolean isConnected() {
        return state == ConnectionState.CONNECTED && connection != null && connection.isOpen();
    }
}
