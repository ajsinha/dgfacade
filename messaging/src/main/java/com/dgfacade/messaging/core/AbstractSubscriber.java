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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Base subscriber with backpressure, reconnection logic, and internal queue management.
 * Subclasses implement doSubscribe(), doUnsubscribe(), doConnect(), doPoll().
 */
public abstract class AbstractSubscriber implements DataSubscriber {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected volatile ConnectionState state = ConnectionState.DISCONNECTED;
    protected volatile boolean paused = false;
    protected Map<String, Object> config;
    protected int reconnectIntervalSeconds = 60;
    protected int backpressureMaxDepth = 10000;

    protected final Map<String, MessageListener> listeners = new ConcurrentHashMap<>();
    protected final LinkedBlockingQueue<MessageEnvelope> internalQueue = new LinkedBlockingQueue<>();
    private final AtomicLong receivedCount = new AtomicLong();
    private final AtomicLong processedCount = new AtomicLong();
    private final AtomicLong erroredCount = new AtomicLong();
    private ScheduledExecutorService reconnectExecutor;
    private ExecutorService dispatchExecutor;

    @Override
    public void initialize(Map<String, Object> config) {
        this.config = config;
        this.reconnectIntervalSeconds = (int) config.getOrDefault("reconnect_interval_seconds", 60);
        this.backpressureMaxDepth = (int) config.getOrDefault("backpressure_max_depth", 10000);
        doConnect();
    }

    @Override
    public void subscribe(String topicOrQueue, MessageListener listener) {
        listeners.put(topicOrQueue, listener);
        if (state == ConnectionState.CONNECTED) {
            doSubscribe(topicOrQueue);
        }
        log.info("Subscribed to: {}", topicOrQueue);
    }

    @Override
    public void unsubscribe(String topicOrQueue) {
        listeners.remove(topicOrQueue);
        doUnsubscribe(topicOrQueue);
        log.info("Unsubscribed from: {}", topicOrQueue);
    }

    @Override
    public Set<String> getSubscriptions() { return Set.copyOf(listeners.keySet()); }

    @Override
    public void start() {
        dispatchExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "sub-dispatch");
            t.setDaemon(true);
            return t;
        });
        dispatchExecutor.submit(this::dispatchLoop);
        for (String topic : listeners.keySet()) {
            doSubscribe(topic);
        }
        log.info("Subscriber started with {} subscriptions", listeners.size());
    }

    @Override
    public void pause() {
        paused = true;
        log.info("Subscriber paused");
    }

    @Override
    public void resume() {
        paused = false;
        log.info("Subscriber resumed");
    }

    @Override
    public int getQueueDepth() { return internalQueue.size(); }

    @Override
    public SubscriberStats getStats() {
        return new SubscriberStats(receivedCount.get(), processedCount.get(), erroredCount.get(),
                internalQueue.size(), isConnected(), paused);
    }

    @Override
    public void close() {
        state = ConnectionState.CLOSING;
        if (reconnectExecutor != null) reconnectExecutor.shutdownNow();
        if (dispatchExecutor != null) dispatchExecutor.shutdownNow();
        doDisconnect();
        state = ConnectionState.CLOSED;
    }

    /** Called by subclasses when a raw message arrives from the broker. */
    protected void enqueue(MessageEnvelope envelope) {
        // Backpressure check: if queue is too deep, reject (leave on broker)
        if (internalQueue.size() >= backpressureMaxDepth) {
            log.warn("Backpressure engaged: queue depth {} >= limit {}", internalQueue.size(), backpressureMaxDepth);
            return;
        }
        receivedCount.incrementAndGet();
        internalQueue.offer(envelope);
    }

    private void dispatchLoop() {
        while (state != ConnectionState.CLOSING && state != ConnectionState.CLOSED) {
            try {
                MessageEnvelope envelope = internalQueue.poll(500, TimeUnit.MILLISECONDS);
                if (envelope == null || paused) continue;

                MessageListener listener = listeners.get(envelope.getTopic());
                if (listener != null) {
                    try {
                        listener.onMessage(envelope);
                        processedCount.incrementAndGet();
                    } catch (Exception e) {
                        erroredCount.incrementAndGet();
                        log.error("Listener error on topic '{}'", envelope.getTopic(), e);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    protected void scheduleReconnect() {
        if (state == ConnectionState.CLOSING || state == ConnectionState.CLOSED) return;
        state = ConnectionState.RECONNECTING;
        if (reconnectExecutor == null || reconnectExecutor.isShutdown()) {
            reconnectExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "sub-reconnect");
                t.setDaemon(true);
                return t;
            });
        }
        reconnectExecutor.schedule(() -> {
            try {
                log.info("Attempting subscriber reconnection...");
                doConnect();
                state = ConnectionState.CONNECTED;
                for (String topic : listeners.keySet()) doSubscribe(topic);
                log.info("Subscriber reconnected successfully");
            } catch (Exception e) {
                log.warn("Subscriber reconnection failed, retrying in {}s", reconnectIntervalSeconds);
                scheduleReconnect();
            }
        }, reconnectIntervalSeconds, TimeUnit.SECONDS);
    }

    protected abstract void doSubscribe(String topicOrQueue);
    protected abstract void doUnsubscribe(String topicOrQueue);
    protected abstract void doConnect();
    protected abstract void doDisconnect();
}
