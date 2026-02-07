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

import com.dgfacade.common.model.DGResponse;
import com.dgfacade.common.model.ResponseChannel;
import com.dgfacade.common.model.StreamingSession;

/**
 * Unified publisher that routes streaming data to the appropriate channel
 * (Kafka, ActiveMQ, or WebSocket) based on session configuration.
 */
public interface StreamingPublisher {

    /**
     * Publish a streaming data response to the channel configured in the session.
     */
    void publish(StreamingSession session, DGResponse response);

    /**
     * Check if the specified channel is available.
     */
    boolean isChannelAvailable(ResponseChannel channel);
}
