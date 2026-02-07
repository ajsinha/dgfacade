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
import com.dgfacade.common.handler.HandlerRegistry;
import com.dgfacade.server.service.StreamingSessionManager;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

    private final DGFacadeProperties properties;
    private final HandlerRegistry handlerRegistry;
    private final StreamingSessionManager sessionManager;

    public DashboardController(DGFacadeProperties properties, HandlerRegistry handlerRegistry,
                               StreamingSessionManager sessionManager) {
        this.properties = properties;
        this.handlerRegistry = handlerRegistry;
        this.sessionManager = sessionManager;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("appName", properties.getAppName());
        model.addAttribute("version", properties.getVersion());
        model.addAttribute("currentPage", "dashboard");
        model.addAttribute("handlerCount", handlerRegistry.getRegisteredTypes().size());
        model.addAttribute("handlers", handlerRegistry.getRegisteredTypes());
        model.addAttribute("kafkaEnabled", properties.getKafka().isEnabled());
        model.addAttribute("activemqEnabled", properties.getActivemq().isEnabled());
        model.addAttribute("streamingEnabled", properties.getStreaming().isEnabled());
        model.addAttribute("streamingSessions", sessionManager.getActiveCount());
        return "index";
    }

    @GetMapping("/about")
    public String about(Model model) {
        model.addAttribute("appName", properties.getAppName());
        model.addAttribute("version", properties.getVersion());
        model.addAttribute("currentPage", "about");
        return "about";
    }

    @GetMapping("/help")
    public String help(Model model) {
        model.addAttribute("appName", properties.getAppName());
        model.addAttribute("version", properties.getVersion());
        model.addAttribute("currentPage", "help");
        return "help";
    }
}
