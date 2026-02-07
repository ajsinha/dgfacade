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

import com.dgfacade.common.model.DGRequest;
import com.dgfacade.common.model.RequestSource;
import com.dgfacade.common.util.JsonUtils;
import com.dgfacade.messaging.RequestDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "dgfacade.activemq.enabled", havingValue = "true")
public class ActiveMQRequestListener {

    private static final Logger log = LoggerFactory.getLogger(ActiveMQRequestListener.class);

    @Autowired
    private RequestDispatcher dispatcher;

    @Autowired(required = false)
    private ActiveMQPublisher publisher;

    @JmsListener(destination = "${dgfacade.activemq.request-queue:dgfacade.requests}")
    public void onMessage(String message) {
        log.info("Received ActiveMQ message");
        try {
            DGRequest request = JsonUtils.fromJson(message, DGRequest.class);
            request.setSource(RequestSource.ACTIVEMQ);

            dispatcher.dispatch(request).thenAccept(response -> {
                if (publisher != null) {
                    publisher.publish("dgfacade.responses", JsonUtils.toJson(response));
                }
            });
        } catch (Exception e) {
            log.error("Failed to process ActiveMQ message", e);
        }
    }
}
