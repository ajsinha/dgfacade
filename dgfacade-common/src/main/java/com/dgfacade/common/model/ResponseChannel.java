/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, modification, or use is strictly prohibited without
 * explicit written permission from the copyright holder.
 * Patent Pending.
 */
package com.dgfacade.common.model;

/**
 * Specifies the channel through which streaming data should be published back.
 * The requester can specify this in the request payload, or the system default is used.
 */
public enum ResponseChannel {
    /** Publish to a Kafka topic */
    KAFKA,
    /** Publish to an ActiveMQ/Artemis queue */
    ACTIVEMQ,
    /** Push to a WebSocket session via STOMP */
    WEBSOCKET,
    /** Return via REST (only for one-shot handlers) */
    REST
}
