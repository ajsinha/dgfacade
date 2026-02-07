/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, modification, or use is strictly prohibited without
 * explicit written permission from the copyright holder.
 * Patent Pending.
 */
package com.dgfacade.messaging.kafka;

import com.dgfacade.messaging.MessagePublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "dgfacade.kafka.enabled", havingValue = "true")
public class KafkaPublisher implements MessagePublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaPublisher.class);
    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
        log.info("KafkaPublisher initialized");
    }

    @Override
    public void publish(String topic, String message) {
        publish(topic, null, message);
    }

    @Override
    public void publish(String topic, String key, String message) {
        try {
            if (key != null) {
                kafkaTemplate.send(topic, key, message);
            } else {
                kafkaTemplate.send(topic, message);
            }
            log.debug("Published message to Kafka topic: {}", topic);
        } catch (Exception e) {
            log.error("Failed to publish message to Kafka topic: {}", topic, e);
        }
    }

    @Override
    public boolean isAvailable() { return true; }

    @Override
    public String getPublisherName() { return "Kafka"; }
}
