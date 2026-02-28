/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential.
 */
package com.dgfacade.server.handler;

import com.dgfacade.common.model.DGRequest;
import com.dgfacade.common.model.DGResponse;
import java.util.List;
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

            // Support both operands[] array and operandA/operandB pair
            List<Double> operands = new java.util.ArrayList<>();
            if (payload.containsKey("operands") && payload.get("operands") instanceof java.util.List<?> list) {
                for (Object item : list) {
                    if (item instanceof Number n) operands.add(n.doubleValue());
                    else operands.add(Double.parseDouble(String.valueOf(item)));
                }
            } else {
                double a = ((Number) payload.getOrDefault("operandA", 0)).doubleValue();
                double b = ((Number) payload.getOrDefault("operandB", 0)).doubleValue();
                operands.add(a);
                operands.add(b);
            }

            if (operands.isEmpty()) throw new IllegalArgumentException("At least one operand required");

            double result = switch (operation) {
                case "ADD" -> operands.stream().mapToDouble(Double::doubleValue).sum();
                case "SUBTRACT", "SUB" -> {
                    double r = operands.get(0);
                    for (int i = 1; i < operands.size(); i++) r -= operands.get(i);
                    yield r;
                }
                case "MULTIPLY", "MUL" -> operands.stream().mapToDouble(Double::doubleValue).reduce(1, (a, b) -> a * b);
                case "DIVIDE", "DIV" -> {
                    double r = operands.get(0);
                    for (int i = 1; i < operands.size(); i++) {
                        if (operands.get(i) == 0) throw new ArithmeticException("Division by zero");
                        r /= operands.get(i);
                    }
                    yield r;
                }
                case "MOD" -> {
                    if (operands.size() < 2) throw new IllegalArgumentException("MOD requires at least 2 operands");
                    yield operands.get(0) % operands.get(1);
                }
                case "POWER", "POW" -> {
                    if (operands.size() < 2) throw new IllegalArgumentException("POW requires at least 2 operands");
                    yield Math.pow(operands.get(0), operands.get(1));
                }
                case "MIN" -> operands.stream().mapToDouble(Double::doubleValue).min().orElse(0);
                case "MAX" -> operands.stream().mapToDouble(Double::doubleValue).max().orElse(0);
                case "AVG", "AVERAGE" -> operands.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                default -> throw new IllegalArgumentException("Unknown operation: " + operation);
            };
            return DGResponse.success(request.getRequestId(),
                    Map.of("operation", operation, "operands", operands, "result", result));
        } catch (Exception e) {
            return DGResponse.error(request.getRequestId(), e.getMessage());
        }
    }

    @Override
    public void stop() { stopped = true; }

    @Override
    public void cleanup() {}
}
