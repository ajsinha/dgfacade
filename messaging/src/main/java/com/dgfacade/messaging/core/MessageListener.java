/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential. Patent Pending.
 */
package com.dgfacade.messaging.core;

/**
 * Callback interface for receiving messages from a subscriber.
 * Implementations must be thread-safe.
 */
@FunctionalInterface
public interface MessageListener {
    /**
     * Called when a message arrives on a subscribed topic/queue.
     * @param envelope the message envelope
     */
    void onMessage(MessageEnvelope envelope);
}
