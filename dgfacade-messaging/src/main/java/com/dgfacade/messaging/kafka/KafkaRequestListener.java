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

import com.dgfacade.common.model.DGRequest;
import com.dgfacade.common.model.DGResponse;
import com.dgfacade.common.model.RequestSource;
import com.dgfacade.common.util.JsonUtils;
import com.dgfacade.messaging.MessagePublisher;
import com.dgfacade.messaging.RequestDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "dgfacade.kafka.enabled", havingValue = "true")
public class KafkaRequestListener {

    private static final Logger log = LoggerFactory.getLogger(KafkaRequestListener.class);

    @Autowired
    private RequestDispatcher dispatcher;

    @Autowired(required = false)
    private KafkaPublisher kafkaPublisher;

    @KafkaListener(topics = "${dgfacade.kafka.request-topic:dgfacade-requests}",
                   groupId = "${dgfacade.kafka.group-id:dgfacade-group}")
    public void onMessage(String message) {
        log.info("Received Kafka message");
        try {
            DGRequest request = JsonUtils.fromJson(message, DGRequest.class);
            request.setSource(RequestSource.KAFKA);

            dispatcher.dispatch(request).thenAccept(response -> {
                if (kafkaPublisher != null) {
                    String responseTopic = "dgfacade-responses";
                    kafkaPublisher.publish(responseTopic, request.getRequestId(),
                                          JsonUtils.toJson(response));
                }
            });
        } catch (Exception e) {
            log.error("Failed to process Kafka message", e);
        }
    }
}
