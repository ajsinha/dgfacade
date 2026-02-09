/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential. Patent Pending.
 */
package com.dgfacade.web.controller;

import com.dgfacade.common.model.HandlerState;
import com.dgfacade.server.engine.ExecutionEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class MonitoringController {

    private static final Logger log = LoggerFactory.getLogger(MonitoringController.class);

    private final ExecutionEngine engine;

    @Value("${dgfacade.app-name:DGFacade}")
    private String appName;

    @Value("${dgfacade.version:1.3.0}")
    private String version;

    public MonitoringController(ExecutionEngine engine) {
        this.engine = engine;
    }

    @GetMapping("/monitoring/handlers")
    public String handlers(Model model) {
        model.addAttribute("appName", appName);
        model.addAttribute("version", version);
        model.addAttribute("states", engine.getRecentStates());
        return "pages/monitoring/handler-states";
    }

    @GetMapping("/monitoring/handlers/{handlerId}")
    public String handlerDetail(@PathVariable("handlerId") String handlerId, Model model,
                                RedirectAttributes redirect) {
        model.addAttribute("appName", appName);
        model.addAttribute("version", version);

        HandlerState state = null;
        try {
            state = engine.getRecentStates().stream()
                    .filter(s -> s != null && handlerId.equals(s.getHandlerId()))
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            log.warn("Error searching handler states for {}: {}", handlerId, e.getMessage());
        }

        if (state == null) {
            log.info("Handler {} not found in recent states buffer (may have expired)", handlerId);
            model.addAttribute("handlerNotFound", true);
            model.addAttribute("handlerId", handlerId);
        }

        model.addAttribute("state", state);
        return "pages/monitoring/handler-detail";
    }
}
