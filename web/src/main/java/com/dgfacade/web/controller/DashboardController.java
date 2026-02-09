/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential. Patent Pending.
 */
package com.dgfacade.web.controller;

import com.dgfacade.server.engine.ExecutionEngine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

    private final ExecutionEngine engine;

    @Value("${dgfacade.app-name:DGFacade}")
    private String appName;

    @Value("${dgfacade.version:1.2.0}")
    private String version;

    public DashboardController(ExecutionEngine engine) {
        this.engine = engine;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("appName", appName);
        model.addAttribute("version", version);
        model.addAttribute("handlers", engine.getRegisteredRequestTypes());
        model.addAttribute("handlerCount", engine.getRegisteredRequestTypes().size());
        model.addAttribute("recentStates", engine.getRecentStates());
        model.addAttribute("kafkaEnabled", false);
        model.addAttribute("activemqEnabled", false);
        return "pages/monitoring/dashboard";
    }

    @GetMapping("/login")
    public String login(Model model) {
        model.addAttribute("appName", appName);
        return "pages/info/login";
    }

    @GetMapping("/about")
    public String about(Model model) {
        model.addAttribute("appName", appName);
        model.addAttribute("version", version);
        return "pages/info/about";
    }

    @GetMapping("/help")
    public String help(Model model) {
        model.addAttribute("appName", appName);
        model.addAttribute("version", version);
        return "pages/info/help";
    }

    @GetMapping("/help/architecture")
    public String helpArchitecture(Model model) {
        model.addAttribute("appName", appName);
        model.addAttribute("version", version);
        return "pages/help/architecture";
    }

    @GetMapping("/help/brokers")
    public String helpBrokers(Model model) {
        model.addAttribute("appName", appName);
        model.addAttribute("version", version);
        return "pages/help/brokers";
    }

    @GetMapping("/help/channels")
    public String helpChannels(Model model) {
        model.addAttribute("appName", appName);
        model.addAttribute("version", version);
        return "pages/help/channels";
    }

    @GetMapping("/help/configuration")
    public String helpConfiguration(Model model) {
        model.addAttribute("appName", appName);
        model.addAttribute("version", version);
        return "pages/help/configuration";
    }

    @GetMapping("/help/request-processing")
    public String helpRequestProcessing(Model model) {
        model.addAttribute("appName", appName);
        model.addAttribute("version", version);
        return "pages/help/request-processing";
    }

    @GetMapping("/help/prometheus")
    public String helpPrometheus(Model model) {
        model.addAttribute("appName", appName);
        model.addAttribute("version", version);
        return "pages/help/prometheus";
    }

    @GetMapping("/glossary")
    public String glossary(Model model) {
        model.addAttribute("appName", appName);
        model.addAttribute("version", version);
        return "pages/info/glossary";
    }

    @GetMapping("/docs")
    public String docs(Model model) {
        model.addAttribute("appName", appName);
        return "pages/info/docs";
    }

    @GetMapping("/playground")
    public String playground(Model model) {
        model.addAttribute("appName", appName);
        model.addAttribute("version", version);
        model.addAttribute("handlers", engine.getRegisteredRequestTypes());
        return "pages/playground/handler-playground";
    }

    @GetMapping("/help/handlers")
    public String helpHandlers(Model model) {
        model.addAttribute("appName", appName);
        model.addAttribute("version", version);
        return "pages/help/handlers";
    }
}
