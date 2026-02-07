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

/**
 * Callback interface for receiving messages from a topic or queue.
 *
 * Implementations are registered with {@link CompositeMessageListener}
 * against one or more topics. When a message arrives on a subscribed
 * topic, every registered listener for that topic is invoked.
 *
 * <p>Listeners must be thread-safe — the same listener instance may be
 * called concurrently from different consumer threads.
 *
 * <p>Example:
 * <pre>{@code
 *   TopicMessageListener myListener = message -> {
 *       System.out.println("Received from " + message.topic() + ": " + message.value());
 *   };
 *   compositeListener.addListener("market-data", myListener);
 * }</pre>
 *
 * @since 1.1.0
 */
@FunctionalInterface
public interface TopicMessageListener {

    /**
     * Called when a message is received on a subscribed topic.
     *
     * @param message the received message, never null
     */
    void onMessage(ReceivedMessage message);
}
