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

import com.dgfacade.common.handler.DGHandler;
import com.dgfacade.common.model.*;
import com.dgfacade.messaging.MessagePublisher;
import com.dgfacade.common.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Sample handler that performs basic arithmetic operations
 * and publishes results to Kafka.
 *
 * Expected payload:
 * {
 *   "operation": "ADD|SUBTRACT|MULTIPLY|DIVIDE",
 *   "operandA": <number>,
 *   "operandB": <number>
 * }
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ArithmeticHandler implements DGHandler {

    private static final Logger log = LoggerFactory.getLogger(ArithmeticHandler.class);

    @Autowired(required = false)
    private MessagePublisher messagePublisher;

    private DGRequest request;
    private HandlerStatus status = HandlerStatus.CREATED;
    private String operation;
    private double operandA;
    private double operandB;

    @Override
    public String getRequestType() { return "ARITHMETIC"; }

    @Override
    public String getDescription() { return "Performs basic arithmetic operations (ADD, SUBTRACT, MULTIPLY, DIVIDE)"; }

    @Override
    public void start(DGRequest request) throws Exception {
        this.status = HandlerStatus.STARTING;
        this.request = request;

        Map<String, Object> payload = request.getPayload();
        if (payload == null) throw new DGFacadeException("Payload is required", ResponseStatus.INVALID_REQUEST);

        this.operation = String.valueOf(payload.get("operation")).toUpperCase();
        this.operandA = toDouble(payload.get("operandA"));
        this.operandB = toDouble(payload.get("operandB"));

        if (!operation.matches("ADD|SUBTRACT|MULTIPLY|DIVIDE")) {
            throw new DGFacadeException("Invalid operation: " + operation +
                    ". Must be ADD, SUBTRACT, MULTIPLY, or DIVIDE", ResponseStatus.INVALID_REQUEST);
        }

        if ("DIVIDE".equals(operation) && operandB == 0) {
            throw new DGFacadeException("Division by zero", ResponseStatus.INVALID_REQUEST);
        }

        this.status = HandlerStatus.READY;
        log.info("ArithmeticHandler started: {} {} {}", operandA, operation, operandB);
    }

    @Override
    public DGResponse execute() throws Exception {
        this.status = HandlerStatus.EXECUTING;

        double result = switch (operation) {
            case "ADD" -> operandA + operandB;
            case "SUBTRACT" -> operandA - operandB;
            case "MULTIPLY" -> operandA * operandB;
            case "DIVIDE" -> operandA / operandB;
            default -> throw new DGFacadeException("Unsupported operation: " + operation);
        };

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("operation", operation);
        resultMap.put("operandA", operandA);
        resultMap.put("operandB", operandB);
        resultMap.put("result", result);
        resultMap.put("expression", operandA + " " + operationSymbol() + " " + operandB + " = " + result);

        log.info("ArithmeticHandler result: {} {} {} = {}", operandA, operationSymbol(), operandB, result);
        return DGResponse.success(request.getRequestId(), getRequestType(), resultMap);
    }

    @Override
    public void stop(DGResponse response) {
        this.status = HandlerStatus.STOPPING;
        try {
            if (response != null && messagePublisher != null && messagePublisher.isAvailable()) {
                messagePublisher.publish("dgfacade-arithmetic-results",
                        request.getRequestId(), JsonUtils.toJson(response));
                log.info("Published arithmetic result to messaging");
            }
        } catch (Exception e) {
            log.warn("Failed to publish result to messaging", e);
        }
        this.status = HandlerStatus.STOPPED;
    }

    @Override
    public HandlerStatus getStatus() { return status; }

    private String operationSymbol() {
        return switch (operation) {
            case "ADD" -> "+";
            case "SUBTRACT" -> "-";
            case "MULTIPLY" -> "*";
            case "DIVIDE" -> "/";
            default -> "?";
        };
    }

    private double toDouble(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        if (value instanceof String s) return Double.parseDouble(s);
        throw new DGFacadeException("Invalid numeric value: " + value, ResponseStatus.INVALID_REQUEST);
    }
}
