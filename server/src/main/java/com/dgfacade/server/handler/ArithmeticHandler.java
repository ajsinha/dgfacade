/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential. Patent Pending.
 */
package com.dgfacade.server.handler;

import com.dgfacade.common.model.DGRequest;
import com.dgfacade.common.model.DGResponse;
import java.util.Map;

/**
 * Built-in handler for basic arithmetic operations: ADD, SUBTRACT, MULTIPLY, DIVIDE.
 * Demonstrates structured request/response processing.
 */
public class ArithmeticHandler implements DGHandler {

    private volatile boolean stopped = false;

    @Override
    public void construct(Map<String, Object> config) { /* no config needed */ }

    @Override
    public DGResponse execute(DGRequest request) {
        if (stopped) return DGResponse.error(request.getRequestId(), "Handler was stopped");
        try {
            Map<String, Object> payload = request.getPayload();
            String operation = String.valueOf(payload.getOrDefault("operation", "ADD")).toUpperCase();
            double a = ((Number) payload.getOrDefault("operandA", 0)).doubleValue();
            double b = ((Number) payload.getOrDefault("operandB", 0)).doubleValue();
            double result = switch (operation) {
                case "ADD" -> a + b;
                case "SUBTRACT", "SUB" -> a - b;
                case "MULTIPLY", "MUL" -> a * b;
                case "DIVIDE", "DIV" -> {
                    if (b == 0) throw new ArithmeticException("Division by zero");
                    yield a / b;
                }
                case "MOD" -> a % b;
                case "POWER", "POW" -> Math.pow(a, b);
                default -> throw new IllegalArgumentException("Unknown operation: " + operation);
            };
            return DGResponse.success(request.getRequestId(),
                    Map.of("operation", operation, "operandA", a, "operandB", b, "result", result));
        } catch (Exception e) {
            return DGResponse.error(request.getRequestId(), e.getMessage());
        }
    }

    @Override
    public void stop() { stopped = true; }

    @Override
    public void cleanup() {}
}
