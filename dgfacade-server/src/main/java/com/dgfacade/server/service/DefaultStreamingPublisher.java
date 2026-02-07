/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, modification, or use is strictly prohibited without
 * explicit written permission from the copyright holder.
 * Patent Pending.
 */
package com.dgfacade.server.service;

import com.dgfacade.common.model.DGResponse;
import com.dgfacade.common.model.ResponseChannel;
import com.dgfacade.common.model.StreamingSession;
import com.dgfacade.common.util.JsonUtils;
import com.dgfacade.messaging.MessagePublisher;
import com.dgfacade.messaging.StreamingPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * Multi-channel streaming publisher (v1.1.0).
 *
 * Routes each data message to ALL channels configured in the session.
 * A single session can fan-out to any combination of:
 *   - WEBSOCKET (/topic/stream/{sessionId})
 *   - KAFKA (session's responseTopic)
 *   - ACTIVEMQ (session's responseTopic)
 *
 * If a channel is configured but unavailable (e.g. Kafka disabled),
 * that channel is silently skipped and logged as a warning.
 */
@Service
public class DefaultStreamingPublisher implements StreamingPublisher {

    private static final Logger log = LoggerFactory.getLogger(DefaultStreamingPublisher.class);

    @Autowired(required = false)
    private List<MessagePublisher> messagePublishers;

    @Autowired(required = false)
    private SimpMessagingTemplate messagingTemplate;

    @Override
    public void publish(StreamingSession session, DGResponse response) {
        Set<ResponseChannel> channels = session.getResponseChannels();
        if (channels == null || channels.isEmpty()) {
            log.warn("No response channels configured for session {}", session.getSessionId());
            return;
        }

        // Fan-out: publish to EVERY channel in the set
        for (ResponseChannel channel : channels) {
            try {
                switch (channel) {
                    case KAFKA -> publishToMessaging(session, response, "Kafka");
                    case ACTIVEMQ -> publishToMessaging(session, response, "ActiveMQ");
                    case WEBSOCKET -> publishToWebSocket(session, response);
                    case REST -> log.debug("REST channel — streaming data buffered for session {}",
                            session.getSessionId());
                }
            } catch (Exception e) {
                log.error("Failed to publish to channel {} for session {}",
                        channel, session.getSessionId(), e);
            }
        }
    }

    @Override
    public boolean isChannelAvailable(ResponseChannel channel) {
        return switch (channel) {
            case KAFKA -> hasPublisher("Kafka");
            case ACTIVEMQ -> hasPublisher("ActiveMQ");
            case WEBSOCKET -> messagingTemplate != null;
            case REST -> true;
        };
    }

    private void publishToMessaging(StreamingSession session, DGResponse response, String publisherName) {
        if (messagePublishers == null) {
            log.warn("{} not available — no message publishers registered", publisherName);
            return;
        }
        messagePublishers.stream()
                .filter(p -> p.getPublisherName().equalsIgnoreCase(publisherName) && p.isAvailable())
                .findFirst()
                .ifPresentOrElse(
                        p -> p.publish(session.getResponseTopic(), session.getSessionId(),
                                JsonUtils.toJson(response)),
                        () -> log.warn("{} publisher not available for session {}",
                                publisherName, session.getSessionId())
                );
    }

    private void publishToWebSocket(StreamingSession session, DGResponse response) {
        if (messagingTemplate == null) {
            log.warn("WebSocket not available — cannot publish streaming data for session {}",
                    session.getSessionId());
            return;
        }
        String destination = "/topic/stream/" + session.getSessionId();
        messagingTemplate.convertAndSend(destination, response);
    }

    private boolean hasPublisher(String name) {
        return messagePublishers != null &&
               messagePublishers.stream().anyMatch(p ->
                       p.getPublisherName().equalsIgnoreCase(name) && p.isAvailable());
    }
}
