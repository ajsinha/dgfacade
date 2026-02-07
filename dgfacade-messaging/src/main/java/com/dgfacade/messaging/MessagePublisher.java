/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, modification, or use is strictly prohibited without
 * explicit written permission from the copyright holder.
 * Patent Pending.
 */
package com.dgfacade.messaging;

/**
 * Generic interface for publishing messages to messaging systems.
 */
public interface MessagePublisher {

    /** Publish a message to the specified destination. */
    void publish(String destination, String message);

    /** Publish a message with a key to the specified destination. */
    void publish(String destination, String key, String message);

    /** Check if this publisher is available. */
    boolean isAvailable();

    /** Get the name of this publisher. */
    String getPublisherName();
}
