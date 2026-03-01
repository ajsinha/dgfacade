/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential.
 */
package com.dgfacade.web.controller;

import com.dgfacade.server.engine.ExecutionEngine;
import com.dgfacade.server.service.BrokerService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

@Controller
public class DashboardController {

    private final ExecutionEngine engine;
    private final BrokerService brokerService;

    @Value("${dgfacade.app-name:DGFacade}")
    private String appName;

    @Value("${dgfacade.version:1.6.2}")
    private String version;

    public DashboardController(ExecutionEngine engine, BrokerService brokerService) {
        this.engine = engine;
        this.brokerService = brokerService;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("appName", appName);
        model.addAttribute("version", version);
        model.addAttribute("handlers", engine.getRegisteredRequestTypes());
        model.addAttribute("handlerCount", engine.getRegisteredRequestTypes().size());
        model.addAttribute("recentStates", engine.getRecentStates());

        // Dynamically detect broker types from config files
        boolean kafkaEnabled = false;
        boolean activemqEnabled = false;
        boolean rabbitmqEnabled = false;
        for (String brokerId : brokerService.listBrokerIds()) {
            Map<String, Object> broker = brokerService.getBroker(brokerId);
            if (broker == null) continue;
            Object enabled = broker.get("enabled");
            boolean isEnabled = enabled == null || Boolean.TRUE.equals(enabled) || "true".equals(String.valueOf(enabled));
            if (!isEnabled) continue;
            String type = String.valueOf(broker.getOrDefault("type", "")).toLowerCase();
            if (type.contains("kafka")) kafkaEnabled = true;
            else if (type.contains("activemq")) activemqEnabled = true;
            else if (type.contains("rabbit")) rabbitmqEnabled = true;
        }
        model.addAttribute("kafkaEnabled", kafkaEnabled);
        model.addAttribute("activemqEnabled", activemqEnabled);
        model.addAttribute("rabbitmqEnabled", rabbitmqEnabled);
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
    public String helpRequestProcessing() {
        return "redirect:/help/handlers#internals";
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

    @GetMapping("/help/handler-chaining")
    public String helpHandlerChaining() {
        return "redirect:/help/handlers#chaining";
    }

    @GetMapping("/help/clustering")
    public String helpClustering(Model model) {
        model.addAttribute("appName", appName);
        model.addAttribute("version", version);
        return "pages/help/clustering";
    }

    @GetMapping("/help/ingestion")
    public String helpIngestion(Model model) {
        model.addAttribute("appName", appName);
        model.addAttribute("version", version);
        return "pages/help/ingestion";
    }

    @GetMapping("/help/operational-tooling")
    public String helpOperationalTooling(Model model) {
        model.addAttribute("appName", appName);
        model.addAttribute("version", version);
        return "pages/help/operational-tooling";
    }

    @GetMapping("/help/python")
    public String helpPython(Model model) {
        model.addAttribute("appName", appName);
        model.addAttribute("version", version);
        return "pages/help/python";
    }
}
