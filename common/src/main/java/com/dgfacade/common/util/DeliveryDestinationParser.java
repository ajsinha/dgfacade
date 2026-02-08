/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential. Patent Pending.
 */
package com.dgfacade.common.util;

/**
 * Parses delivery_destination URIs like:
 *   kafka://server/topic
 *   activemq://server/topic/topicName
 *   activemq://server/queue/queueName
 *   file:///path/to/file
 *   REST
 *   WebSocket
 */
public final class DeliveryDestinationParser {

    private DeliveryDestinationParser() {}

    public static ParsedDestination parse(String destination) {
        if (destination == null || destination.isBlank()) {
            return new ParsedDestination("REST", null, null, null);
        }
        String trimmed = destination.trim();
        if ("REST".equalsIgnoreCase(trimmed)) {
            return new ParsedDestination("REST", null, null, null);
        }
        if ("WebSocket".equalsIgnoreCase(trimmed)) {
            return new ParsedDestination("WebSocket", null, null, null);
        }
        if (trimmed.startsWith("kafka://")) {
            String rest = trimmed.substring("kafka://".length());
            int slash = rest.indexOf('/');
            if (slash > 0) {
                return new ParsedDestination("KAFKA", rest.substring(0, slash), rest.substring(slash + 1), null);
            }
        }
        if (trimmed.startsWith("activemq://")) {
            String rest = trimmed.substring("activemq://".length());
            int slash = rest.indexOf('/');
            if (slash > 0) {
                String server = rest.substring(0, slash);
                String path = rest.substring(slash + 1);
                if (path.startsWith("topic/")) {
                    return new ParsedDestination("ACTIVEMQ_TOPIC", server, path.substring("topic/".length()), null);
                } else if (path.startsWith("queue/")) {
                    return new ParsedDestination("ACTIVEMQ_QUEUE", server, path.substring("queue/".length()), null);
                }
            }
        }
        if (trimmed.startsWith("file://")) {
            return new ParsedDestination("FILE", null, null, trimmed.substring("file://".length()));
        }
        return new ParsedDestination("UNKNOWN", null, null, null);
    }

    public static record ParsedDestination(
        String type,
        String server,
        String topicOrQueue,
        String filePath
    ) {}
}
