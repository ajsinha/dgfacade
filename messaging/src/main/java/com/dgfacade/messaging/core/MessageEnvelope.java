/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential.
 */
package com.dgfacade.messaging.core;

import java.time.Instant;
import java.util.Map;

/**
 * Universal message envelope wrapping any payload traversing the pub/sub layer.
 */
public class MessageEnvelope {

    private String messageId;
    private String topic;
    private String payload;
    private Map<String, String> headers;
    private Instant timestamp;
    private int partition = -1;
    private long offset = -1;

    public MessageEnvelope() { this.timestamp = Instant.now(); }

    public MessageEnvelope(String topic, String payload) {
        this();
        this.messageId = java.util.UUID.randomUUID().toString();
        this.topic = topic;
        this.payload = payload;
    }

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public Map<String, String> getHeaders() { return headers; }
    public void setHeaders(Map<String, String> headers) { this.headers = headers; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public int getPartition() { return partition; }
    public void setPartition(int partition) { this.partition = partition; }
    public long getOffset() { return offset; }
    public void setOffset(long offset) { this.offset = offset; }
}
