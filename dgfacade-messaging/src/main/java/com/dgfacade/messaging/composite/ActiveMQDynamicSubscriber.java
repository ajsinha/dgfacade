/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, modification, or use is strictly prohibited without
 * explicit written permission from the copyright holder.
 * Patent Pending.
 */
package com.dgfacade.messaging.composite;

import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.TextMessage;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.listener.DefaultMessageListenerContainer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Manages dynamic ActiveMQ/Artemis topic and queue subscriptions using
 * Spring JMS {@link DefaultMessageListenerContainer}s.
 *
 * <p>For each subscribed destination, a dedicated listener container is
 * created from a shared {@link ConnectionFactory}. When a destination is
 * unsubscribed, its container is stopped and destroyed.
 *
 * <p>The {@code pubSubDomain} flag in configuration controls whether
 * destinations are treated as JMS topics (true) or queues (false).
 * This can also be overridden per-destination using
 * {@link #subscribe(String, boolean, Consumer)}.
 *
 * <p>This class is thread-safe; all container lifecycle operations are synchronized.
 *
 * @since 1.1.0
 */
class ActiveMQDynamicSubscriber implements DynamicSubscriber {

    private static final Logger log = LoggerFactory.getLogger(ActiveMQDynamicSubscriber.class);

    private final ConnectionFactory connectionFactory;
    private final boolean defaultPubSubDomain;
    private final int concurrency;

    /**
     * Active listener containers keyed by destination name.
     */
    private final ConcurrentHashMap<String, DefaultMessageListenerContainer>
            containers = new ConcurrentHashMap<>();

    /**
     * Tracks whether each destination is a topic or queue.
     */
    private final ConcurrentHashMap<String, Boolean> destinationTypes = new ConcurrentHashMap<>();

    private volatile boolean available = false;
    private volatile boolean shutdown = false;

    /**
     * Creates an ActiveMQ dynamic subscriber from the composite config.
     *
     * @param config composite listener configuration with ActiveMQ details
     */
    ActiveMQDynamicSubscriber(CompositeListenerConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        if (!config.isActiveMqEnabled()) {
            throw new IllegalArgumentException("ActiveMQ is not enabled in config");
        }

        this.defaultPubSubDomain = config.isActiveMqPubSubDomain();
        this.concurrency = config.getActiveMqConcurrency();

        // Create the shared Artemis connection factory
        ActiveMQConnectionFactory factory =
                new ActiveMQConnectionFactory(config.getActiveMqBrokerUrl());
        if (config.getActiveMqUsername() != null && !config.getActiveMqUsername().isBlank()) {
            factory.setUser(config.getActiveMqUsername());
            factory.setPassword(config.getActiveMqPassword());
        }

        this.connectionFactory = factory;
        this.available = true;

        log.info("ActiveMQDynamicSubscriber initialized — broker={}, pubSub={}, concurrency={}",
                config.getActiveMqBrokerUrl(), defaultPubSubDomain, concurrency);
    }

    /**
     * Creates an ActiveMQ dynamic subscriber from an existing ConnectionFactory.
     * Useful when the factory is already managed by Spring.
     *
     * @param connectionFactory existing JMS connection factory
     * @param pubSubDomain      true for topics, false for queues
     * @param concurrency       number of concurrent consumers per destination
     */
    ActiveMQDynamicSubscriber(ConnectionFactory connectionFactory,
                               boolean pubSubDomain, int concurrency) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory);
        this.defaultPubSubDomain = pubSubDomain;
        this.concurrency = concurrency;
        this.available = true;
    }

    @Override
    public void subscribe(String destination, Consumer<ReceivedMessage> callback) {
        subscribe(destination, defaultPubSubDomain, callback);
    }

    /**
     * Subscribe to a destination with an explicit topic/queue flag.
     *
     * @param destination the JMS destination name
     * @param isTopic     true if this is a JMS topic, false for a queue
     * @param callback    receives every message from this destination
     */
    public void subscribe(String destination, boolean isTopic, Consumer<ReceivedMessage> callback) {
        Objects.requireNonNull(destination, "destination must not be null");
        Objects.requireNonNull(callback, "callback must not be null");

        if (shutdown) {
            throw new IllegalStateException("ActiveMQDynamicSubscriber is shut down");
        }

        if (containers.containsKey(destination)) {
            log.debug("ActiveMQ already subscribed to '{}', skipping container creation", destination);
            return;
        }

        synchronized (containers) {
            if (containers.containsKey(destination)) return;

            log.info("Subscribing to ActiveMQ {} '{}'", isTopic ? "topic" : "queue", destination);

            DefaultMessageListenerContainer container = new DefaultMessageListenerContainer();
            container.setConnectionFactory(connectionFactory);
            container.setDestinationName(destination);
            container.setPubSubDomain(isTopic);
            container.setConcurrency(String.valueOf(concurrency));
            container.setAutoStartup(false);

            container.setMessageListener((jakarta.jms.MessageListener) jmsMessage -> {
                try {
                    ReceivedMessage msg = toReceivedMessage(destination, jmsMessage);
                    callback.accept(msg);
                } catch (Exception e) {
                    log.error("Error processing ActiveMQ message from '{}': {}",
                            destination, e.getMessage(), e);
                }
            });

            container.afterPropertiesSet();
            container.start();

            containers.put(destination, container);
            destinationTypes.put(destination, isTopic);

            log.info("ActiveMQ container started for {} '{}' (total active: {})",
                    isTopic ? "topic" : "queue", destination, containers.size());
        }
    }

    @Override
    public void unsubscribe(String destination) {
        Objects.requireNonNull(destination, "destination must not be null");

        DefaultMessageListenerContainer container = containers.remove(destination);
        destinationTypes.remove(destination);

        if (container != null) {
            log.info("Unsubscribing from ActiveMQ destination: {}", destination);
            try {
                container.stop();
                container.destroy();
            } catch (Exception e) {
                log.warn("Error stopping ActiveMQ container for '{}': {}", destination, e.getMessage());
            }
            log.info("ActiveMQ container stopped and removed for '{}' (total active: {})",
                    destination, containers.size());
        }
    }

    @Override
    public Set<String> getSubscribedTopics() {
        return Collections.unmodifiableSet(new HashSet<>(containers.keySet()));
    }

    @Override
    public boolean isSubscribed(String destination) {
        return containers.containsKey(destination);
    }

    @Override
    public ReceivedMessage.BrokerSource getBrokerSource() {
        return ReceivedMessage.BrokerSource.ACTIVEMQ;
    }

    @Override
    public boolean isAvailable() { return available && !shutdown; }

    @Override
    public void shutdown() {
        if (shutdown) return;
        shutdown = true;
        available = false;

        log.info("Shutting down ActiveMQDynamicSubscriber — stopping {} containers",
                containers.size());

        for (Map.Entry<String, DefaultMessageListenerContainer> entry : containers.entrySet()) {
            try {
                entry.getValue().stop();
                entry.getValue().destroy();
                log.debug("Stopped ActiveMQ container for '{}'", entry.getKey());
            } catch (Exception e) {
                log.warn("Error stopping ActiveMQ container for '{}': {}",
                        entry.getKey(), e.getMessage());
            }
        }
        containers.clear();
        destinationTypes.clear();

        // Close the connection factory if we own it
        if (connectionFactory instanceof ActiveMQConnectionFactory amqFactory) {
            try {
                amqFactory.close();
            } catch (Exception e) {
                log.warn("Error closing ActiveMQ connection factory: {}", e.getMessage());
            }
        }

        log.info("ActiveMQDynamicSubscriber shut down complete");
    }

    /**
     * Returns whether the given destination is a topic (true) or queue (false).
     */
    public Optional<Boolean> isTopicDestination(String destination) {
        return Optional.ofNullable(destinationTypes.get(destination));
    }

    /**
     * Convert a JMS Message to a ReceivedMessage.
     */
    private ReceivedMessage toReceivedMessage(String destination, Message jmsMessage)
            throws JMSException {
        String value = null;
        if (jmsMessage instanceof TextMessage textMessage) {
            value = textMessage.getText();
        } else {
            value = jmsMessage.toString();
        }

        Map<String, String> headers = new LinkedHashMap<>();
        try {
            @SuppressWarnings("unchecked")
            Enumeration<String> propNames = jmsMessage.getPropertyNames();
            while (propNames.hasMoreElements()) {
                String name = propNames.nextElement();
                headers.put(name, String.valueOf(jmsMessage.getObjectProperty(name)));
            }
        } catch (JMSException e) {
            log.debug("Could not extract JMS properties from message on '{}'", destination);
        }

        String correlationId = null;
        try { correlationId = jmsMessage.getJMSCorrelationID(); } catch (JMSException ignored) {}

        return new ReceivedMessage(
                destination,
                correlationId,
                value,
                headers,
                ReceivedMessage.BrokerSource.ACTIVEMQ
        );
    }
}
