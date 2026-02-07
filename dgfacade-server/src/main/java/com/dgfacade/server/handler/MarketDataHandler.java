/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, modification, or use is strictly prohibited without
 * explicit written permission from the copyright holder.
 * Patent Pending.
 */
package com.dgfacade.server.handler;

import com.dgfacade.common.handler.StreamingHandler;
import com.dgfacade.common.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * Example streaming handler that simulates a live market data feed.
 *
 * When started, it monitors a list of stock symbols and publishes
 * random price ticks at irregular intervals (simulating real market data
 * that arrives unpredictably). Each tick is published to ALL configured
 * response channels (any combination of Kafka, ActiveMQ, and WebSocket).
 *
 * Request payload:
 * {
 *   "symbols": ["AAPL", "GOOGL", "MSFT"],   // stock symbols to monitor
 *   "intervalMinMs": 500,                     // minimum interval between ticks (ms)
 *   "intervalMaxMs": 3000                     // maximum interval between ticks (ms)
 * }
 *
 * If symbols are not provided, defaults to: AAPL, GOOGL, MSFT, AMZN, TSLA
 *
 * Each published message contains:
 * {
 *   "symbol": "AAPL",
 *   "price": 187.42,
 *   "change": 1.23,
 *   "changePercent": 0.66,
 *   "volume": 12345,
 *   "bid": 187.40,
 *   "ask": 187.44,
 *   "tickTimestamp": "2025-06-15T10:30:00Z"
 * }
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class MarketDataHandler implements StreamingHandler {

    private static final Logger log = LoggerFactory.getLogger(MarketDataHandler.class);

    private static final Map<String, Double> DEFAULT_PRICES = Map.of(
            "AAPL", 187.50, "GOOGL", 174.80, "MSFT", 420.30,
            "AMZN", 185.60, "TSLA", 248.90, "META", 495.20,
            "NVDA", 130.50, "JPM", 198.40, "V", 278.60, "NFLX", 628.70
    );

    private DGRequest request;
    private HandlerStatus status = HandlerStatus.CREATED;
    private Consumer<DGResponse> dataPublisher;
    private volatile boolean running = false;
    private volatile boolean shutdownRequested = false;

    private List<String> symbols;
    private Map<String, Double> currentPrices;
    private int intervalMinMs = 500;
    private int intervalMaxMs = 3000;

    @Override
    public String getRequestType() { return "MARKET_DATA"; }

    @Override
    public String getDescription() {
        return "Simulated live market data feed — publishes random stock price ticks at irregular intervals";
    }

    @Override
    public long getDefaultTtlMinutes() { return 30; }

    @Override
    public Set<ResponseChannel> getDefaultResponseChannels() { return Set.of(ResponseChannel.WEBSOCKET); }

    @Override
    public void setDataPublisher(Consumer<DGResponse> publisher) {
        this.dataPublisher = publisher;
    }

    @Override
    public void requestShutdown() {
        this.shutdownRequested = true;
    }

    @Override
    public boolean isRunning() { return running; }

    @Override
    @SuppressWarnings("unchecked")
    public void start(DGRequest request) throws Exception {
        this.status = HandlerStatus.STARTING;
        this.request = request;

        Map<String, Object> payload = request.getPayload();
        if (payload == null) payload = Collections.emptyMap();

        // Parse symbols
        Object symbolsObj = payload.get("symbols");
        if (symbolsObj instanceof List) {
            this.symbols = ((List<Object>) symbolsObj).stream()
                    .map(Object::toString)
                    .map(String::toUpperCase)
                    .toList();
        } else {
            this.symbols = List.of("AAPL", "GOOGL", "MSFT", "AMZN", "TSLA");
        }

        // Parse intervals
        if (payload.containsKey("intervalMinMs")) {
            this.intervalMinMs = ((Number) payload.get("intervalMinMs")).intValue();
        }
        if (payload.containsKey("intervalMaxMs")) {
            this.intervalMaxMs = ((Number) payload.get("intervalMaxMs")).intValue();
        }

        // Initialize current prices
        this.currentPrices = new HashMap<>();
        for (String symbol : symbols) {
            currentPrices.put(symbol, DEFAULT_PRICES.getOrDefault(symbol,
                    100.0 + ThreadLocalRandom.current().nextDouble(400)));
        }

        this.status = HandlerStatus.READY;
        log.info("MarketDataHandler started: symbols={}, interval=({}-{}ms)",
                 symbols, intervalMinMs, intervalMaxMs);
    }

    @Override
    public DGResponse execute() throws Exception {
        this.status = HandlerStatus.EXECUTING;
        this.running = true;

        log.info("MarketDataHandler streaming loop started for request {}",
                 request.getRequestId());

        try {
            while (!shutdownRequested && !Thread.currentThread().isInterrupted()) {
                // Pick a random symbol
                String symbol = symbols.get(ThreadLocalRandom.current().nextInt(symbols.size()));
                double currentPrice = currentPrices.get(symbol);

                // Simulate price movement (-2% to +2%)
                double changePercent = (ThreadLocalRandom.current().nextDouble() - 0.5) * 4.0;
                double change = currentPrice * changePercent / 100.0;
                double newPrice = Math.round((currentPrice + change) * 100.0) / 100.0;
                currentPrices.put(symbol, newPrice);

                double spread = Math.round(ThreadLocalRandom.current().nextDouble(0.02, 0.10) * 100.0) / 100.0;

                // Build tick data
                Map<String, Object> tick = new LinkedHashMap<>();
                tick.put("symbol", symbol);
                tick.put("price", newPrice);
                tick.put("change", Math.round(change * 100.0) / 100.0);
                tick.put("changePercent", Math.round(changePercent * 100.0) / 100.0);
                tick.put("volume", ThreadLocalRandom.current().nextInt(100, 50000));
                tick.put("bid", Math.round((newPrice - spread / 2) * 100.0) / 100.0);
                tick.put("ask", Math.round((newPrice + spread / 2) * 100.0) / 100.0);
                tick.put("tickTimestamp", Instant.now().toString());

                // Publish via the data publisher callback
                DGResponse tickResponse = DGResponse.success(
                        request.getRequestId(), getRequestType(), tick);

                if (dataPublisher != null) {
                    dataPublisher.accept(tickResponse);
                }

                // Sleep for a random interval (simulates unpredictable data arrival)
                int sleepMs = ThreadLocalRandom.current().nextInt(intervalMinMs, intervalMaxMs + 1);
                Thread.sleep(sleepMs);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("MarketDataHandler interrupted for request {}", request.getRequestId());
        }

        this.running = false;
        log.info("MarketDataHandler streaming loop ended for request {}", request.getRequestId());

        // Return a final summary response
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("finalPrices", new HashMap<>(currentPrices));
        summary.put("symbols", symbols);
        return DGResponse.success(request.getRequestId(), getRequestType(), summary);
    }

    @Override
    public void stop(DGResponse response) {
        this.status = HandlerStatus.STOPPING;
        this.shutdownRequested = true;
        this.running = false;
        this.status = HandlerStatus.STOPPED;
        log.info("MarketDataHandler stopped for request {}", request.getRequestId());
    }

    @Override
    public HandlerStatus getStatus() { return status; }
}
