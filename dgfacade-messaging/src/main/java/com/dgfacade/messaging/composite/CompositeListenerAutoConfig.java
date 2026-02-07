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

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring auto-configuration that creates a {@link CompositeMessageListener} bean
 * from application properties when {@code dgfacade.composite.enabled=true}.
 *
 * <p>Configuration properties (all under {@code dgfacade.composite.*}):
 * <pre>
 *   dgfacade.composite.enabled=true
 *   dgfacade.composite.name=my-listener
 *
 *   # Kafka
 *   dgfacade.composite.kafka.enabled=true
 *   dgfacade.composite.kafka.bootstrap-servers=broker1:9092,broker2:9092
 *   dgfacade.composite.kafka.group-id=my-group
 *   dgfacade.composite.kafka.auto-offset-reset=latest
 *   dgfacade.composite.kafka.concurrency=1
 *
 *   # ActiveMQ
 *   dgfacade.composite.activemq.enabled=true
 *   dgfacade.composite.activemq.broker-url=tcp://localhost:61616
 *   dgfacade.composite.activemq.username=admin
 *   dgfacade.composite.activemq.password=admin
 *   dgfacade.composite.activemq.concurrency=1
 *   dgfacade.composite.activemq.pub-sub-domain=true
 * </pre>
 *
 * <p>The bean is a singleton and is automatically shut down via {@code @PreDestroy}.
 * Applications can also create their own {@link CompositeMessageListener} instances
 * programmatically without this auto-configuration.
 *
 * @since 1.1.0
 */
@Configuration
@ConditionalOnProperty(name = "dgfacade.composite.enabled", havingValue = "true")
public class CompositeListenerAutoConfig {

    private static final Logger log = LoggerFactory.getLogger(CompositeListenerAutoConfig.class);

    @Value("${dgfacade.composite.name:dgfacade-composite-listener}")
    private String name;

    @Value("${dgfacade.composite.kafka.enabled:false}")
    private boolean kafkaEnabled;
    @Value("${dgfacade.composite.kafka.bootstrap-servers:localhost:9092}")
    private String kafkaBootstrapServers;
    @Value("${dgfacade.composite.kafka.group-id:dgfacade-composite}")
    private String kafkaGroupId;
    @Value("${dgfacade.composite.kafka.auto-offset-reset:latest}")
    private String kafkaAutoOffsetReset;
    @Value("${dgfacade.composite.kafka.concurrency:1}")
    private int kafkaConcurrency;

    @Value("${dgfacade.composite.activemq.enabled:false}")
    private boolean activeMqEnabled;
    @Value("${dgfacade.composite.activemq.broker-url:tcp://localhost:61616}")
    private String activeMqBrokerUrl;
    @Value("${dgfacade.composite.activemq.username:#{null}}")
    private String activeMqUsername;
    @Value("${dgfacade.composite.activemq.password:#{null}}")
    private String activeMqPassword;
    @Value("${dgfacade.composite.activemq.concurrency:1}")
    private int activeMqConcurrency;
    @Value("${dgfacade.composite.activemq.pub-sub-domain:true}")
    private boolean activeMqPubSubDomain;

    private CompositeMessageListener compositeListener;

    @Bean
    public CompositeMessageListener compositeMessageListener() {
        CompositeListenerConfig.Builder builder = CompositeListenerConfig.builder()
                .listenerName(name);

        if (kafkaEnabled) {
            builder.kafkaEnabled(true)
                    .kafkaBootstrapServers(kafkaBootstrapServers)
                    .kafkaGroupId(kafkaGroupId)
                    .kafkaAutoOffsetReset(kafkaAutoOffsetReset)
                    .kafkaConcurrency(kafkaConcurrency);
        }

        if (activeMqEnabled) {
            builder.activeMqEnabled(true)
                    .activeMqBrokerUrl(activeMqBrokerUrl)
                    .activeMqUsername(activeMqUsername)
                    .activeMqPassword(activeMqPassword)
                    .activeMqConcurrency(activeMqConcurrency)
                    .activeMqPubSubDomain(activeMqPubSubDomain);
        }

        CompositeListenerConfig config = builder.build();
        compositeListener = new CompositeMessageListener(config);

        log.info("CompositeMessageListener bean created: {}", config);
        return compositeListener;
    }

    @PreDestroy
    public void destroy() {
        if (compositeListener != null && !compositeListener.isShutdown()) {
            compositeListener.shutdown();
        }
    }
}
