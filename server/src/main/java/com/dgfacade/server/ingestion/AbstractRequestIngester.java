/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential.
 */
package com.dgfacade.server.ingestion;

import com.dgfacade.common.model.DGRequest;
import com.dgfacade.common.model.DGResponse;
import com.dgfacade.common.util.JsonUtil;
import com.dgfacade.server.engine.ExecutionEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Base class for all RequestIngester implementations.
 * Provides shared request deserialization, validation, submission, and statistics tracking.
 */
public abstract class AbstractRequestIngester implements RequestIngester {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected String id;
    protected String description;
    protected Map<String, Object> config;
    protected ExecutionEngine executionEngine;
    protected volatile boolean running = false;

    // Statistics
    protected final AtomicLong requestsReceived = new AtomicLong();
    protected final AtomicLong requestsSubmitted = new AtomicLong();
    protected final AtomicLong requestsFailed = new AtomicLong();
    protected final AtomicLong requestsRejected = new AtomicLong();
    protected Instant startedAt;
    protected volatile Instant lastActivityAt;

    public void setExecutionEngine(ExecutionEngine engine) {
        this.executionEngine = engine;
    }

    @Override
    public String getId() { return id; }

    @Override
    public String getDescription() { return description; }

    @Override
    public boolean isRunning() { return running; }

    @Override
    public void initialize(String id, Map<String, Object> config) {
        this.id = id;
        this.config = config;
        this.description = (String) config.getOrDefault("description", id);
    }

    @Override
    public IngesterStats getStats() {
        return new IngesterStats(
                id, getType(), running,
                requestsReceived.get(), requestsSubmitted.get(),
                requestsFailed.get(), requestsRejected.get(),
                startedAt, lastActivityAt,
                getSourceDescription()
        );
    }

    /**
     * Subclasses call this to process a raw JSON message.
     * Handles deserialization, field enrichment, submission, and error tracking.
     */
    protected void processMessage(String jsonPayload, String sourceDetail) {
        long count = requestsReceived.incrementAndGet();
        lastActivityAt = Instant.now();

        log.info("[{}] ◀ MESSAGE RECEIVED #{} from {} ({}B payload)",
                id, count, sourceDetail, jsonPayload != null ? jsonPayload.length() : 0);

        DGRequest request;
        try {
            request = JsonUtil.fromJson(jsonPayload, DGRequest.class);
        } catch (Exception e) {
            requestsRejected.incrementAndGet();
            log.warn("[{}] ✗ REJECTED #{} — malformed JSON from {}: {}", id, count, sourceDetail, e.getMessage());
            return;
        }

        // Validate required fields
        if (request.getRequestType() == null || request.getRequestType().isBlank()) {
            requestsRejected.incrementAndGet();
            log.warn("[{}] ✗ REJECTED #{} from {} — missing request_type", id, count, sourceDetail);
            return;
        }
        if (request.getApiKey() == null || request.getApiKey().isBlank()) {
            requestsRejected.incrementAndGet();
            log.warn("[{}] ✗ REJECTED #{} from {} — missing api_key", id, count, sourceDetail);
            return;
        }

        // Enrich request
        if (request.getRequestId() == null || request.getRequestId().isBlank()) {
            request.setRequestId(UUID.randomUUID().toString());
        }
        request.setSourceChannel(getType().name());
        request.setReceivedAt(Instant.now());

        log.info("[{}] ✓ VALIDATED #{} — requestId={}, type={}, apiKey={}",
                id, count, request.getRequestId(), request.getRequestType(),
                request.getApiKey().length() > 8
                        ? request.getApiKey().substring(0, 8) + "..."
                        : request.getApiKey());

        // Submit to execution engine
        try {
            log.info("[{}] → SUBMITTING #{} to ExecutionEngine — requestId={}",
                    id, count, request.getRequestId());

            executionEngine.submit(request)
                    .orTimeout(request.getTtlMinutes(), TimeUnit.MINUTES)
                    .thenAccept(response -> {
                        requestsSubmitted.incrementAndGet();
                        log.info("[{}] ✓ COMPLETED #{} — requestId={}, status={}, handler={}",
                                id, count, request.getRequestId(),
                                response.getStatus(),
                                response.getHandlerId() != null ? response.getHandlerId() : "N/A");
                        if (response.getStatus() == DGResponse.Status.ERROR) {
                            log.warn("[{}]   ERROR detail: {}", id, response.getErrorMessage());
                        }
                        handleResponse(request, response);
                    })
                    .exceptionally(ex -> {
                        requestsFailed.incrementAndGet();
                        log.error("[{}] ✗ FAILED #{} — requestId={}, error: {}",
                                id, count, request.getRequestId(), ex.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            requestsFailed.incrementAndGet();
            log.error("[{}] ✗ SUBMIT ERROR #{} — requestId={}, from {}: {}",
                    id, count, request.getRequestId(), sourceDetail, e.getMessage());
        }
    }

    /**
     * Hook for subclasses to handle the response (e.g., publish to delivery_destination).
     * Default: no-op (fire-and-forget ingestion).
     */
    protected void handleResponse(DGRequest request, DGResponse response) {
        // Override if response delivery is needed
    }

    /** Human-readable source description for stats (e.g., "kafka://orders.ingest"). */
    protected abstract String getSourceDescription();
}
