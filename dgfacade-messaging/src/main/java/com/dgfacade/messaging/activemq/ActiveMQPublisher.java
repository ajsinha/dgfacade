/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, modification, or use is strictly prohibited without
 * explicit written permission from the copyright holder.
 * Patent Pending.
 */
package com.dgfacade.messaging.activemq;

import com.dgfacade.messaging.MessagePublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "dgfacade.activemq.enabled", havingValue = "true")
public class ActiveMQPublisher implements MessagePublisher {

    private static final Logger log = LoggerFactory.getLogger(ActiveMQPublisher.class);
    private final JmsTemplate jmsTemplate;

    public ActiveMQPublisher(JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
        log.info("ActiveMQPublisher initialized");
    }

    @Override
    public void publish(String destination, String message) {
        try {
            jmsTemplate.convertAndSend(destination, message);
            log.debug("Published message to ActiveMQ queue: {}", destination);
        } catch (Exception e) {
            log.error("Failed to publish message to ActiveMQ queue: {}", destination, e);
        }
    }

    @Override
    public void publish(String destination, String key, String message) {
        publish(destination, message);
    }

    @Override
    public boolean isAvailable() { return true; }

    @Override
    public String getPublisherName() { return "ActiveMQ"; }
}
