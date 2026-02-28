/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential.
 */
package com.dgfacade.messaging.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Base publisher with common reconnection logic, batch buffering,
 * and statistics tracking. Subclasses implement doPublish() and doConnect().
 */
public abstract class AbstractPublisher implements DataPublisher {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected volatile ConnectionState state = ConnectionState.DISCONNECTED;
    protected Map<String, Object> config;
    protected final Set<String> topics = ConcurrentHashMap.newKeySet();
    protected int reconnectIntervalSeconds = 60;
    protected boolean batchMode = false;
    protected int batchSize = 100;
    protected int batchFlushIntervalMs = 5000;

    private final AtomicLong sentCount = new AtomicLong();
    private final AtomicLong errorCount = new AtomicLong();
    private final AtomicLong bytesSent = new AtomicLong();
    private ScheduledExecutorService reconnectExecutor;
    private ScheduledExecutorService batchExecutor;
    private final ConcurrentLinkedQueue<PendingMessage> batchBuffer = new ConcurrentLinkedQueue<>();

    private record PendingMessage(String topic, MessageEnvelope envelope, CompletableFuture<Void> future) {}

    @Override
    public void initialize(Map<String, Object> config) {
        this.config = config;
        this.reconnectIntervalSeconds = (int) config.getOrDefault("reconnect_interval_seconds", 60);
        this.batchMode = Boolean.parseBoolean(String.valueOf(config.getOrDefault("batch_mode", "false")));
        this.batchSize = (int) config.getOrDefault("batch_size", 100);
        this.batchFlushIntervalMs = (int) config.getOrDefault("batch_flush_interval_ms", 5000);
        doConnect();
        if (batchMode) {
            batchExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "pub-batch-flush");
                t.setDaemon(true);
                return t;
            });
            batchExecutor.scheduleAtFixedRate(this::flushBatch, batchFlushIntervalMs, batchFlushIntervalMs,
                    TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public CompletableFuture<Void> publish(String topic, MessageEnvelope envelope) {
        if (batchMode) {
            CompletableFuture<Void> future = new CompletableFuture<>();
            batchBuffer.add(new PendingMessage(topic, envelope, future));
            if (batchBuffer.size() >= batchSize) flushBatch();
            return future;
        }
        return doPublish(topic, envelope).thenRun(() -> {
            sentCount.incrementAndGet();
            bytesSent.addAndGet(envelope.getPayload() != null ? envelope.getPayload().length() : 0);
        }).exceptionally(ex -> {
            errorCount.incrementAndGet();
            scheduleReconnect();
            throw new CompletionException(ex);
        });
    }

    @Override
    public CompletableFuture<Void> publishBatch(String topic, List<MessageEnvelope> envelopes) {
        return CompletableFuture.allOf(
            envelopes.stream().map(e -> publish(topic, e)).toArray(CompletableFuture[]::new)
        );
    }

    @Override
    public void addTopic(String topic) { topics.add(topic); }

    @Override
    public void flush() { flushBatch(); }

    @Override
    public PublisherStats getStats() {
        return new PublisherStats(sentCount.get(), errorCount.get(), bytesSent.get(), isConnected());
    }

    @Override
    public void close() {
        flushBatch();
        state = ConnectionState.CLOSING;
        if (reconnectExecutor != null) reconnectExecutor.shutdownNow();
        if (batchExecutor != null) batchExecutor.shutdownNow();
        doDisconnect();
        state = ConnectionState.CLOSED;
    }

    protected void scheduleReconnect() {
        if (state == ConnectionState.CLOSING || state == ConnectionState.CLOSED) return;
        state = ConnectionState.RECONNECTING;
        if (reconnectExecutor == null || reconnectExecutor.isShutdown()) {
            reconnectExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "pub-reconnect");
                t.setDaemon(true);
                return t;
            });
        }
        reconnectExecutor.schedule(() -> {
            try {
                log.info("Attempting reconnection...");
                doConnect();
                state = ConnectionState.CONNECTED;
                log.info("Reconnected successfully");
            } catch (Exception e) {
                log.warn("Reconnection failed, will retry in {}s", reconnectIntervalSeconds, e);
                scheduleReconnect();
            }
        }, reconnectIntervalSeconds, TimeUnit.SECONDS);
    }

    private void flushBatch() {
        List<PendingMessage> batch = new ArrayList<>();
        PendingMessage msg;
        while ((msg = batchBuffer.poll()) != null) batch.add(msg);
        if (batch.isEmpty()) return;
        for (PendingMessage pm : batch) {
            try {
                doPublish(pm.topic(), pm.envelope()).get(10, TimeUnit.SECONDS);
                sentCount.incrementAndGet();
                pm.future().complete(null);
            } catch (Exception e) {
                errorCount.incrementAndGet();
                pm.future().completeExceptionally(e);
            }
        }
    }

    protected abstract CompletableFuture<Void> doPublish(String topic, MessageEnvelope envelope);
    protected abstract void doConnect();
    protected abstract void doDisconnect();
}
