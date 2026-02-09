/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential. Patent Pending.
 */
package com.dgfacade.web.controller;

import com.dgfacade.common.model.*;
import com.dgfacade.server.service.BrokerService;
import com.dgfacade.server.service.InputChannelService;
import com.dgfacade.server.service.OutputChannelService;
import com.dgfacade.server.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final UserService userService;
    private final BrokerService brokerService;
    private final InputChannelService inputChannelService;
    private final OutputChannelService outputChannelService;

    @Value("${dgfacade.app-name:DGFacade}") private String appName;
    @Value("${dgfacade.version:1.3.0}") private String version;

    public AdminController(UserService userService, BrokerService brokerService,
                           InputChannelService inputChannelService,
                           OutputChannelService outputChannelService) {
        this.userService = userService;
        this.brokerService = brokerService;
        this.inputChannelService = inputChannelService;
        this.outputChannelService = outputChannelService;
    }

    private void common(Model model) {
        model.addAttribute("appName", appName);
        model.addAttribute("version", version);
    }

    // ══════════════════════════ USERS ══════════════════════════

    @GetMapping("/users") public String users(Model model) { common(model); model.addAttribute("users", userService.getAllUsers()); return "pages/admin/users"; }
    @GetMapping("/users/create") public String userCreateForm(Model model) { common(model); return "pages/admin/user-form"; }

    @PostMapping("/users/create")
    public String createUser(@RequestParam String username, @RequestParam String password,
                             @RequestParam String displayName, @RequestParam String email,
                             @RequestParam(required = false) List<String> roles, RedirectAttributes redirect) {
        try {
            UserInfo user = new UserInfo();
            user.setUserId(UUID.randomUUID().toString().substring(0, 8));
            user.setUsername(username); user.setPassword(password);
            user.setDisplayName(displayName); user.setEmail(email);
            user.setRoles(roles != null ? roles : List.of("USER")); user.setEnabled(true);
            List<UserInfo> all = new ArrayList<>(userService.getAllUsers()); all.add(user);
            userService.saveUsers(all);
            redirect.addFlashAttribute("success", "User created: " + username);
        } catch (Exception e) { redirect.addFlashAttribute("error", "Failed: " + e.getMessage()); }
        return "redirect:/admin/users";
    }

    @PostMapping("/users/delete")
    public String deleteUser(@RequestParam String userId, RedirectAttributes redirect) {
        if ("admin".equals(userId)) { redirect.addFlashAttribute("error", "Protected system account."); return "redirect:/admin/users"; }
        try {
            List<UserInfo> all = new ArrayList<>(userService.getAllUsers());
            all.removeIf(u -> u.getUserId().equals(userId)); userService.saveUsers(all);
            redirect.addFlashAttribute("success", "User deleted");
        } catch (Exception e) { redirect.addFlashAttribute("error", "Failed: " + e.getMessage()); }
        return "redirect:/admin/users";
    }

    // ══════════════════════════ API KEYS ══════════════════════════

    @GetMapping("/apikeys") public String apiKeys(Model model) { common(model); model.addAttribute("apiKeys", userService.getAllApiKeys()); model.addAttribute("users", userService.getAllUsers()); return "pages/admin/apikeys"; }
    @GetMapping("/apikeys/create") public String apiKeyCreateForm(Model model) { common(model); model.addAttribute("users", userService.getAllUsers()); return "pages/admin/apikey-form"; }

    @PostMapping("/apikeys/create")
    public String createApiKey(@RequestParam String userId, @RequestParam String description, RedirectAttributes redirect) {
        try {
            ApiKeyInfo key = new ApiKeyInfo(); key.setKey("dgf-" + UUID.randomUUID().toString().substring(0, 16));
            key.setUserId(userId); key.setDescription(description);
            List<ApiKeyInfo> all = new ArrayList<>(userService.getAllApiKeys()); all.add(key);
            userService.saveApiKeys(all);
            redirect.addFlashAttribute("success", "API key created: " + key.getKey());
        } catch (Exception e) { redirect.addFlashAttribute("error", "Failed: " + e.getMessage()); }
        return "redirect:/admin/apikeys";
    }

    @PostMapping("/apikeys/delete")
    public String deleteApiKey(@RequestParam String key, RedirectAttributes redirect) {
        if ("dgf-admin-key-0001".equals(key)) { redirect.addFlashAttribute("error", "Protected admin master key."); return "redirect:/admin/apikeys"; }
        try {
            List<ApiKeyInfo> all = new ArrayList<>(userService.getAllApiKeys());
            all.removeIf(k -> k.getKey().equals(key)); userService.saveApiKeys(all);
            redirect.addFlashAttribute("success", "API key deleted");
        } catch (Exception e) { redirect.addFlashAttribute("error", "Failed: " + e.getMessage()); }
        return "redirect:/admin/apikeys";
    }

    // ══════════════════════════ BROKERS ══════════════════════════

    @GetMapping("/brokers") public String brokers(Model model) { common(model); model.addAttribute("brokers", brokerService.getAllBrokers()); return "pages/admin/brokers"; }
    @GetMapping("/brokers/create") public String brokerCreateForm(Model model) { common(model); model.addAttribute("editMode", false); model.addAttribute("broker", defaultBroker()); model.addAttribute("brokerId", ""); return "pages/admin/broker-form"; }

    @PostMapping("/brokers/create")
    public String brokerCreate(@RequestParam String brokerId, HttpServletRequest req, RedirectAttributes redirect) {
        try {
            if (brokerId == null || brokerId.isBlank()) { redirect.addFlashAttribute("error", "Broker ID is required"); return "redirect:/admin/brokers/create"; }
            String cleanId = brokerId.trim().toLowerCase().replaceAll("[^a-z0-9\\-_]", "-");
            if (brokerService.getBroker(cleanId) != null) { redirect.addFlashAttribute("error", "Broker '" + cleanId + "' already exists"); return "redirect:/admin/brokers/create"; }
            brokerService.saveBroker(cleanId, buildBrokerFromRequest(req));
            redirect.addFlashAttribute("success", "Broker created: " + cleanId);
        } catch (Exception e) { log.error("Failed to create broker", e); redirect.addFlashAttribute("error", "Failed: " + e.getMessage()); }
        return "redirect:/admin/brokers";
    }

    @GetMapping("/brokers/edit/{brokerId}") public String brokerEditForm(@PathVariable("brokerId") String brokerId, Model model, RedirectAttributes redirect) {
        common(model); Map<String, Object> broker = brokerService.getBroker(brokerId);
        if (broker == null) { redirect.addFlashAttribute("error", "Broker not found: " + brokerId); return "redirect:/admin/brokers"; }
        model.addAttribute("editMode", true); model.addAttribute("broker", broker); model.addAttribute("brokerId", brokerId); return "pages/admin/broker-form";
    }

    @PostMapping("/brokers/edit/{brokerId}") public String brokerUpdate(@PathVariable("brokerId") String brokerId, HttpServletRequest req, RedirectAttributes redirect) {
        try { brokerService.saveBroker(brokerId, buildBrokerFromRequest(req)); redirect.addFlashAttribute("success", "Broker updated: " + brokerId); }
        catch (Exception e) { log.error("Failed to update broker {}", brokerId, e); redirect.addFlashAttribute("error", "Failed: " + e.getMessage()); }
        return "redirect:/admin/brokers";
    }

    @PostMapping("/brokers/delete") public String brokerDelete(@RequestParam String brokerId, RedirectAttributes redirect) {
        if (brokerService.deleteBroker(brokerId)) redirect.addFlashAttribute("success", "Broker deleted: " + brokerId); else redirect.addFlashAttribute("error", "Broker not found: " + brokerId); return "redirect:/admin/brokers";
    }
    @PostMapping("/brokers/start") public String brokerStart(@RequestParam String brokerId, RedirectAttributes redirect) { brokerService.startBroker(brokerId); redirect.addFlashAttribute("success", "Broker started: " + brokerId); return "redirect:/admin/brokers"; }
    @PostMapping("/brokers/stop") public String brokerStop(@RequestParam String brokerId, RedirectAttributes redirect) { brokerService.stopBroker(brokerId); redirect.addFlashAttribute("success", "Broker stopped: " + brokerId); return "redirect:/admin/brokers"; }

    // ══════════════════════════ INPUT CHANNELS ══════════════════════════

    @GetMapping("/input-channels") public String inputChannels(Model model) { common(model); model.addAttribute("channels", inputChannelService.getAllChannels()); model.addAttribute("brokerIds", brokerService.listBrokerIds()); return "pages/admin/input-channels"; }
    @GetMapping("/input-channels/create") public String inputChannelCreateForm(Model model) { common(model); model.addAttribute("editMode", false); model.addAttribute("channel", defaultChannel()); model.addAttribute("channelId", ""); model.addAttribute("brokerIds", brokerService.listBrokerIds()); return "pages/admin/input-channel-form"; }

    @PostMapping("/input-channels/create") public String inputChannelCreate(@RequestParam String channelId, HttpServletRequest req, RedirectAttributes redirect) {
        try {
            if (channelId == null || channelId.isBlank()) { redirect.addFlashAttribute("error", "Input Channel ID is required"); return "redirect:/admin/input-channels/create"; }
            String cleanId = channelId.trim().toLowerCase().replaceAll("[^a-z0-9\\-_]", "-");
            if (inputChannelService.getChannel(cleanId) != null) { redirect.addFlashAttribute("error", "Input Channel '" + cleanId + "' already exists"); return "redirect:/admin/input-channels/create"; }
            inputChannelService.saveChannel(cleanId, buildChannelFromRequest(req));
            redirect.addFlashAttribute("success", "Input Channel created: " + cleanId);
        } catch (Exception e) { log.error("Failed to create input channel", e); redirect.addFlashAttribute("error", "Failed: " + e.getMessage()); }
        return "redirect:/admin/input-channels";
    }

    @GetMapping("/input-channels/edit/{channelId}") public String inputChannelEditForm(@PathVariable("channelId") String channelId, Model model, RedirectAttributes redirect) {
        common(model); Map<String, Object> channel = inputChannelService.getChannel(channelId);
        if (channel == null) { redirect.addFlashAttribute("error", "Input Channel not found: " + channelId); return "redirect:/admin/input-channels"; }
        model.addAttribute("editMode", true); model.addAttribute("channel", channel); model.addAttribute("channelId", channelId); model.addAttribute("brokerIds", brokerService.listBrokerIds()); return "pages/admin/input-channel-form";
    }
    @PostMapping("/input-channels/edit/{channelId}") public String inputChannelUpdate(@PathVariable("channelId") String channelId, HttpServletRequest req, RedirectAttributes redirect) {
        try { inputChannelService.saveChannel(channelId, buildChannelFromRequest(req)); redirect.addFlashAttribute("success", "Input Channel updated: " + channelId); }
        catch (Exception e) { log.error("Failed to update input channel {}", channelId, e); redirect.addFlashAttribute("error", "Failed: " + e.getMessage()); }
        return "redirect:/admin/input-channels";
    }
    @PostMapping("/input-channels/delete") public String inputChannelDelete(@RequestParam String channelId, RedirectAttributes redirect) { if (inputChannelService.deleteChannel(channelId)) redirect.addFlashAttribute("success", "Input Channel deleted: " + channelId); else redirect.addFlashAttribute("error", "Input Channel not found: " + channelId); return "redirect:/admin/input-channels"; }
    @PostMapping("/input-channels/start") public String inputChannelStart(@RequestParam String channelId, RedirectAttributes redirect) { inputChannelService.startChannel(channelId); redirect.addFlashAttribute("success", "Input Channel started: " + channelId); return "redirect:/admin/input-channels"; }
    @PostMapping("/input-channels/stop") public String inputChannelStop(@RequestParam String channelId, RedirectAttributes redirect) { inputChannelService.stopChannel(channelId); redirect.addFlashAttribute("success", "Input Channel stopped: " + channelId); return "redirect:/admin/input-channels"; }

    // ══════════════════════════ OUTPUT CHANNELS ══════════════════════════

    @GetMapping("/output-channels") public String outputChannels(Model model) { common(model); model.addAttribute("channels", outputChannelService.getAllChannels()); model.addAttribute("brokerIds", brokerService.listBrokerIds()); return "pages/admin/output-channels"; }
    @GetMapping("/output-channels/create") public String outputChannelCreateForm(Model model) { common(model); model.addAttribute("editMode", false); model.addAttribute("channel", defaultChannel()); model.addAttribute("channelId", ""); model.addAttribute("brokerIds", brokerService.listBrokerIds()); return "pages/admin/output-channel-form"; }

    @PostMapping("/output-channels/create") public String outputChannelCreate(@RequestParam String channelId, HttpServletRequest req, RedirectAttributes redirect) {
        try {
            if (channelId == null || channelId.isBlank()) { redirect.addFlashAttribute("error", "Output Channel ID is required"); return "redirect:/admin/output-channels/create"; }
            String cleanId = channelId.trim().toLowerCase().replaceAll("[^a-z0-9\\-_]", "-");
            if (outputChannelService.getChannel(cleanId) != null) { redirect.addFlashAttribute("error", "Output Channel '" + cleanId + "' already exists"); return "redirect:/admin/output-channels/create"; }
            outputChannelService.saveChannel(cleanId, buildChannelFromRequest(req));
            redirect.addFlashAttribute("success", "Output Channel created: " + cleanId);
        } catch (Exception e) { log.error("Failed to create output channel", e); redirect.addFlashAttribute("error", "Failed: " + e.getMessage()); }
        return "redirect:/admin/output-channels";
    }

    @GetMapping("/output-channels/edit/{channelId}") public String outputChannelEditForm(@PathVariable("channelId") String channelId, Model model, RedirectAttributes redirect) {
        common(model); Map<String, Object> channel = outputChannelService.getChannel(channelId);
        if (channel == null) { redirect.addFlashAttribute("error", "Output Channel not found: " + channelId); return "redirect:/admin/output-channels"; }
        model.addAttribute("editMode", true); model.addAttribute("channel", channel); model.addAttribute("channelId", channelId); model.addAttribute("brokerIds", brokerService.listBrokerIds()); return "pages/admin/output-channel-form";
    }
    @PostMapping("/output-channels/edit/{channelId}") public String outputChannelUpdate(@PathVariable("channelId") String channelId, HttpServletRequest req, RedirectAttributes redirect) {
        try { outputChannelService.saveChannel(channelId, buildChannelFromRequest(req)); redirect.addFlashAttribute("success", "Output Channel updated: " + channelId); }
        catch (Exception e) { log.error("Failed to update output channel {}", channelId, e); redirect.addFlashAttribute("error", "Failed: " + e.getMessage()); }
        return "redirect:/admin/output-channels";
    }
    @PostMapping("/output-channels/delete") public String outputChannelDelete(@RequestParam String channelId, RedirectAttributes redirect) { if (outputChannelService.deleteChannel(channelId)) redirect.addFlashAttribute("success", "Output Channel deleted: " + channelId); else redirect.addFlashAttribute("error", "Output Channel not found: " + channelId); return "redirect:/admin/output-channels"; }
    @PostMapping("/output-channels/start") public String outputChannelStart(@RequestParam String channelId, RedirectAttributes redirect) { outputChannelService.startChannel(channelId); redirect.addFlashAttribute("success", "Output Channel started: " + channelId); return "redirect:/admin/output-channels"; }
    @PostMapping("/output-channels/stop") public String outputChannelStop(@RequestParam String channelId, RedirectAttributes redirect) { outputChannelService.stopChannel(channelId); redirect.addFlashAttribute("success", "Output Channel stopped: " + channelId); return "redirect:/admin/output-channels"; }

    // ══════════════════════════ HELPERS ══════════════════════════

    private Map<String, Object> buildBrokerFromRequest(HttpServletRequest req) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("type", param(req, "type", "kafka")); config.put("description", param(req, "description", "")); config.put("enabled", "true".equals(param(req, "enabled", "true")));
        Map<String, Object> conn = new LinkedHashMap<>();
        String type = param(req, "type", "kafka");
        switch (type) {
            case "kafka": conn.put("bootstrap_servers", param(req, "conn_bootstrap_servers", "localhost:9092")); conn.put("client_id", param(req, "conn_client_id", "dgfacade-client")); conn.put("group_id", param(req, "conn_group_id", "dgfacade-consumer-group")); conn.put("auto_offset_reset", param(req, "conn_auto_offset_reset", "latest")); tryPutInt(conn, "max_poll_records", param(req, "conn_max_poll_records", "500")); break;
            case "activemq": conn.put("broker_url", param(req, "conn_broker_url", "tcp://localhost:61616")); conn.put("username", param(req, "conn_username", "")); conn.put("password", param(req, "conn_password", "")); conn.put("client_id", param(req, "conn_client_id", "dgfacade-amq")); break;
            case "rabbitmq": conn.put("host", param(req, "conn_host", "localhost")); tryPutInt(conn, "port", param(req, "conn_port", "5672")); conn.put("virtual_host", param(req, "conn_virtual_host", "/")); conn.put("username", param(req, "conn_username", "")); conn.put("password", param(req, "conn_password", "")); conn.put("exchange", param(req, "conn_exchange", "")); break;
            case "ibmmq": conn.put("host", param(req, "conn_host", "localhost")); tryPutInt(conn, "port", param(req, "conn_port", "1414")); conn.put("queue_manager", param(req, "conn_queue_manager", "QM1")); conn.put("channel", param(req, "conn_channel", "DEV.APP.SVRCONN")); conn.put("username", param(req, "conn_username", "")); conn.put("password", param(req, "conn_password", "")); break;
            case "filesystem": conn.put("directory", param(req, "conn_directory", "/tmp/dgfacade/inbox")); conn.put("poll_interval_seconds", param(req, "conn_poll_interval", "10")); conn.put("file_pattern", param(req, "conn_file_pattern", "*.json")); break;
            case "sql": conn.put("jdbc_url", param(req, "conn_jdbc_url", "jdbc:postgresql://localhost:5432/dgfacade")); conn.put("username", param(req, "conn_username", "")); conn.put("password", param(req, "conn_password", "")); conn.put("table_name", param(req, "conn_table_name", "dg_messages")); break;
        }
        config.put("connection", conn);
        String sslEnabled = param(req, "ssl_enabled", "false");
        if ("true".equals(sslEnabled)) {
            Map<String, Object> ssl = new LinkedHashMap<>(); ssl.put("enabled", true); ssl.put("format", param(req, "ssl_format", "PEM")); ssl.put("protocol", param(req, "ssl_protocol", "TLSv1.3"));
            if ("PEM".equals(param(req, "ssl_format", "PEM"))) { ssl.put("ca_cert_path", param(req, "ssl_ca_cert_path", "")); ssl.put("client_cert_path", param(req, "ssl_client_cert_path", "")); ssl.put("client_key_path", param(req, "ssl_client_key_path", "")); }
            else { ssl.put("keystore_path", param(req, "ssl_keystore_path", "")); ssl.put("keystore_password", param(req, "ssl_keystore_password", "")); ssl.put("truststore_path", param(req, "ssl_truststore_path", "")); ssl.put("truststore_password", param(req, "ssl_truststore_password", "")); }
            config.put("ssl", ssl);
        }
        String propsText = param(req, "extra_properties", "");
        if (!propsText.isBlank()) { Map<String, String> props = new LinkedHashMap<>(); for (String line : propsText.split("\\n")) { String t = line.trim(); if (t.isEmpty() || !t.contains("=")) continue; String[] kv = t.split("=", 2); props.put(kv[0].trim(), kv[1].trim()); } if (!props.isEmpty()) config.put("properties", props); }
        return config;
    }

    private Map<String, Object> buildChannelFromRequest(HttpServletRequest req) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("type", param(req, "type", "kafka")); config.put("description", param(req, "description", "")); config.put("enabled", "true".equals(param(req, "enabled", "true"))); config.put("broker", param(req, "broker", ""));
        String destName = param(req, "dest_name", ""); String destType = param(req, "dest_type", "topic");
        if (!destName.isBlank()) { List<Map<String, String>> dests = new ArrayList<>(); for (String n : destName.split(",")) { String t = n.trim(); if (!t.isEmpty()) dests.add(Map.of("name", t, "type", destType)); } config.put("destinations", dests); }
        Map<String, Object> queue = new LinkedHashMap<>(); tryPutInt(queue, "depth", param(req, "queue_depth", "10000")); tryPutInt(queue, "warning_threshold_pct", param(req, "queue_warning_pct", "70")); tryPutInt(queue, "critical_threshold_pct", param(req, "queue_critical_pct", "90")); tryPutInt(queue, "drain_resume_pct", param(req, "queue_drain_pct", "60")); config.put("queue", queue);
        Map<String, Object> retry = new LinkedHashMap<>(); tryPutInt(retry, "max_attempts", param(req, "retry_max_attempts", "3")); tryPutInt(retry, "backoff_ms", param(req, "retry_backoff_ms", "1000")); try { retry.put("backoff_multiplier", Double.parseDouble(param(req, "retry_backoff_multiplier", "2.0"))); } catch (Exception ignored) {} config.put("retry", retry);
        return config;
    }

    private Map<String, Object> defaultBroker() { Map<String, Object> m = new LinkedHashMap<>(); m.put("type", "kafka"); m.put("description", ""); m.put("enabled", true); Map<String, Object> c = new LinkedHashMap<>(); c.put("bootstrap_servers", "localhost:9092"); c.put("client_id", "dgfacade-client"); c.put("group_id", "dgfacade-consumer-group"); m.put("connection", c); return m; }
    private Map<String, Object> defaultChannel() { Map<String, Object> m = new LinkedHashMap<>(); m.put("type", "kafka"); m.put("description", ""); m.put("enabled", true); m.put("broker", ""); Map<String, Object> q = new LinkedHashMap<>(); q.put("depth", 10000); q.put("warning_threshold_pct", 70); q.put("critical_threshold_pct", 90); q.put("drain_resume_pct", 60); m.put("queue", q); Map<String, Object> r = new LinkedHashMap<>(); r.put("max_attempts", 3); r.put("backoff_ms", 1000); r.put("backoff_multiplier", 2.0); m.put("retry", r); return m; }
    private String param(HttpServletRequest req, String name, String def) { String val = req.getParameter(name); return (val != null && !val.isBlank()) ? val.trim() : def; }
    private void tryPutInt(Map<String, Object> map, String key, String value) { try { map.put(key, Integer.parseInt(value.trim())); } catch (Exception e) { /* skip */ } }
}
