/**
 * Composite multi-broker message listener with dynamic topic subscription.
 *
 * <p>This package provides {@link com.dgfacade.messaging.composite.CompositeMessageListener},
 * a unified listener facade that can subscribe to topics on both Kafka and ActiveMQ
 * simultaneously, using shared connections per broker.
 *
 * <h3>Key features</h3>
 * <ul>
 *   <li>Dynamic runtime subscription — add/remove topics without restart</li>
 *   <li>Multiple listeners per topic — fan-out delivery to all registered callbacks</li>
 *   <li>Automatic cleanup — when last listener is removed, topic is unsubscribed and purged from memory</li>
 *   <li>Multi-broker — a single topic subscription creates listeners on all enabled brokers</li>
 *   <li>Shared connections — one connection factory per broker, regardless of topic count</li>
 * </ul>
 *
 * <h3>Quick start</h3>
 * <pre>{@code
 *   // Build config
 *   CompositeListenerConfig config = CompositeListenerConfig.builder()
 *       .kafkaEnabled(true)
 *       .kafkaBootstrapServers("broker:9092")
 *       .kafkaGroupId("my-group")
 *       .build();
 *
 *   // Create listener
 *   try (CompositeMessageListener listener = new CompositeMessageListener(config)) {
 *       // Add listener at runtime
 *       listener.addListener("my-topic", msg ->
 *           System.out.println(msg.topic() + ": " + msg.value()));
 *
 *       // ... application runs ...
 *
 *       // Remove when done — if last listener, topic unsubscribed
 *       listener.removeListener("my-topic", myListener);
 *   }
 * }</pre>
 *
 * @since 1.1.0
 */
package com.dgfacade.messaging.composite;
