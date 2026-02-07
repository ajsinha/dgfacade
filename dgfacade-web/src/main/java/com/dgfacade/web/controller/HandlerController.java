/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, modification, or use is strictly prohibited without
 * explicit written permission from the copyright holder.
 * Patent Pending.
 */
package com.dgfacade.web.controller;

import com.dgfacade.common.config.DGFacadeProperties;
import com.dgfacade.common.handler.DGHandler;
import com.dgfacade.common.handler.HandlerRegistry;
import com.dgfacade.common.handler.StreamingHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.*;

@Controller
@RequestMapping("/handlers")
public class HandlerController {

    private final DGFacadeProperties properties;
    private final HandlerRegistry handlerRegistry;

    public HandlerController(DGFacadeProperties properties, HandlerRegistry handlerRegistry) {
        this.properties = properties;
        this.handlerRegistry = handlerRegistry;
    }

    @GetMapping("")
    public String listHandlers(Model model) {
        model.addAttribute("appName", properties.getAppName());
        model.addAttribute("version", properties.getVersion());
        model.addAttribute("currentPage", "handlers");

        // Build a list of handler info maps (type, description, isStreaming)
        List<Map<String, Object>> handlerInfos = new ArrayList<>();
        for (String type : handlerRegistry.getRegisteredTypes()) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("type", type);
            Optional<DGHandler> handler = handlerRegistry.createHandler(type);
            info.put("description", handler.map(DGHandler::getDescription).orElse("—"));
            info.put("streaming", handler.map(DGHandler::isStreaming).orElse(false));
            if (handler.isPresent() && handler.get() instanceof StreamingHandler sh) {
                info.put("defaultTtl", sh.getDefaultTtlMinutes());
                info.put("defaultChannels", sh.getDefaultResponseChannels().stream()
                        .map(Enum::name).toList());
            }
            handlerInfos.add(info);
        }
        model.addAttribute("handlers", handlerInfos);
        return "handlers/list";
    }

    @GetMapping("/test")
    public String testHandler(@RequestParam("type") String type, Model model) {
        model.addAttribute("appName", properties.getAppName());
        model.addAttribute("version", properties.getVersion());
        model.addAttribute("currentPage", "handlers");
        model.addAttribute("handlerType", type);

        // Get handler metadata
        Optional<DGHandler> handler = handlerRegistry.createHandler(type);
        String description = handler.map(DGHandler::getDescription).orElse("No description available");
        boolean isStreaming = handler.map(DGHandler::isStreaming).orElse(false);
        model.addAttribute("handlerDescription", description);
        model.addAttribute("isStreaming", isStreaming);

        if (isStreaming && handler.isPresent() && handler.get() instanceof StreamingHandler sh) {
            model.addAttribute("defaultTtl", sh.getDefaultTtlMinutes());
            model.addAttribute("defaultChannels", sh.getDefaultResponseChannels().stream()
                    .map(Enum::name).toList());
        }

        model.addAttribute("sampleRequest", buildSampleRequest(type));
        return "handlers/test";
    }

    private String buildSampleRequest(String type) {
        String payload = switch (type.toUpperCase()) {
            case "ARITHMETIC" -> """
                    {
                      "apiKey": "dgf-dev-key-001",
                      "requestType": "ARITHMETIC",
                      "payload": {
                        "operation": "ADD",
                        "operandA": 10,
                        "operandB": 5
                      }
                    }""";
            case "ECHO" -> """
                    {
                      "apiKey": "dgf-dev-key-001",
                      "requestType": "ECHO",
                      "payload": {
                        "message": "Hello DGFacade!"
                      }
                    }""";
            case "MARKET_DATA" -> """
                    {
                      "apiKey": "dgf-dev-key-001",
                      "requestType": "MARKET_DATA",
                      "streaming": true,
                      "responseChannels": ["WEBSOCKET"],
                      "ttlMinutes": 5,
                      "payload": {
                        "symbols": ["AAPL", "GOOGL", "MSFT"],
                        "intervalMinMs": 1000,
                        "intervalMaxMs": 3000
                      }
                    }""";
            default -> """
                    {
                      "apiKey": "dgf-dev-key-001",
                      "requestType": "%s",
                      "payload": {}
                    }""".formatted(type);
        };
        return payload.stripIndent().strip();
    }
}
