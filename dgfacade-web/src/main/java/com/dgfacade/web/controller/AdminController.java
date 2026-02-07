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
import com.dgfacade.common.security.ApiKeyService;
import com.dgfacade.web.security.JsonUserDetailsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final DGFacadeProperties properties;
    private final HandlerRegistry handlerRegistry;
    private final JsonUserDetailsService userDetailsService;
    private final ApiKeyService apiKeyService;

    public AdminController(DGFacadeProperties properties, HandlerRegistry handlerRegistry,
                           JsonUserDetailsService userDetailsService, ApiKeyService apiKeyService) {
        this.properties = properties;
        this.handlerRegistry = handlerRegistry;
        this.userDetailsService = userDetailsService;
        this.apiKeyService = apiKeyService;
    }

    @GetMapping("")
    public String adminDashboard(Model model) {
        populateModel(model, "admin");
        model.addAttribute("handlerCount", handlerRegistry.getRegisteredTypes().size());
        model.addAttribute("userCount", userDetailsService.getAllUsers().size());
        model.addAttribute("apiKeyCount", apiKeyService.getAllApiKeys().size());
        return "admin/dashboard";
    }

    @GetMapping("/users")
    public String manageUsers(Model model) {
        populateModel(model, "admin");
        model.addAttribute("users", userDetailsService.getAllUsers());
        return "admin/users";
    }

    @GetMapping("/apikeys")
    public String manageApiKeys(Model model) {
        populateModel(model, "admin");
        model.addAttribute("apiKeys", apiKeyService.getAllApiKeys());
        return "admin/apikeys";
    }

    @GetMapping("/handlers")
    public String manageHandlers(Model model) {
        populateModel(model, "admin");
        model.addAttribute("handlers", handlerRegistry.getRegisteredTypes());
        return "admin/handlers";
    }

    @GetMapping("/messaging")
    public String messaging(Model model) {
        populateModel(model, "admin");
        model.addAttribute("kafkaEnabled", properties.getKafka().isEnabled());
        model.addAttribute("activemqEnabled", properties.getActivemq().isEnabled());
        model.addAttribute("kafkaConfig", properties.getKafka());
        model.addAttribute("activemqConfig", properties.getActivemq());
        return "admin/messaging";
    }

    private void populateModel(Model model, String page) {
        model.addAttribute("appName", properties.getAppName());
        model.addAttribute("version", properties.getVersion());
        model.addAttribute("currentPage", page);
        model.addAttribute("javaVersion", System.getProperty("java.version"));
        model.addAttribute("osName", System.getProperty("os.name"));
    }
}
