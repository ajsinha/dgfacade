/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential. Patent Pending.
 */
package com.dgfacade.web.controller;

import com.dgfacade.common.model.*;
import com.dgfacade.server.service.BrokerService;
import com.dgfacade.server.service.ChannelService;
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
    private final ChannelService channelService;

    @Value("${dgfacade.app-name:DGFacade}")
    private String appName;

    @Value("${dgfacade.version:1.2.0}")
    private String version;

    public AdminController(UserService userService, BrokerService brokerService,
                           ChannelService channelService) {
        this.userService = userService;
        this.brokerService = brokerService;
        this.channelService = channelService;
    }

    private void common(Model model) {
        model.addAttribute("appName", appName);
        model.addAttribute("version", version);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  USERS
    // ═══════════════════════════════════════════════════════════════════════

    @GetMapping("/users")
    public String users(Model model) {
        common(model);
        model.addAttribute("users", userService.getAllUsers());
        return "pages/admin/users";
    }

    @GetMapping("/users/create")
    public String userCreateForm(Model model) {
        common(model);
        return "pages/admin/user-form";
    }

    @PostMapping("/users/create")
    public String createUser(@RequestParam String username, @RequestParam String password,
                             @RequestParam String displayName, @RequestParam String email,
                             @RequestParam(required = false) List<String> roles,
                             RedirectAttributes redirect) {
        try {
            UserInfo user = new UserInfo();
            user.setUserId(UUID.randomUUID().toString().substring(0, 8));
            user.setUsername(username);
            user.setPassword(password);
            user.setDisplayName(displayName);
            user.setEmail(email);
            user.setRoles(roles != null ? roles : List.of("USER"));
            user.setEnabled(true);
            List<UserInfo> all = new ArrayList<>(userService.getAllUsers());
            all.add(user);
            userService.saveUsers(all);
            redirect.addFlashAttribute("success", "User created: " + username);
        } catch (Exception e) {
            redirect.addFlashAttribute("error", "Failed: " + e.getMessage());
        }
        return "redirect:/admin/users";
    }

    private static final String PROTECTED_ADMIN_USER_ID = "admin";

    @PostMapping("/users/delete")
    public String deleteUser(@RequestParam String userId, RedirectAttributes redirect) {
        if (PROTECTED_ADMIN_USER_ID.equals(userId)) {
            redirect.addFlashAttribute("error",
                    "The built-in admin account cannot be deleted. It is a protected system account.");
            return "redirect:/admin/users";
        }
        try {
            List<UserInfo> all = new ArrayList<>(userService.getAllUsers());
            all.removeIf(u -> u.getUserId().equals(userId));
            userService.saveUsers(all);
            redirect.addFlashAttribute("success", "User deleted");
        } catch (Exception e) {
            redirect.addFlashAttribute("error", "Failed: " + e.getMessage());
        }
        return "redirect:/admin/users";
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  API KEYS
    // ═══════════════════════════════════════════════════════════════════════

    @GetMapping("/apikeys")
    public String apiKeys(Model model) {
        common(model);
        model.addAttribute("apiKeys", userService.getAllApiKeys());
        model.addAttribute("users", userService.getAllUsers());
        return "pages/admin/apikeys";
    }

    @GetMapping("/apikeys/create")
    public String apiKeyCreateForm(Model model) {
        common(model);
        model.addAttribute("users", userService.getAllUsers());
        return "pages/admin/apikey-form";
    }

    @PostMapping("/apikeys/create")
    public String createApiKey(@RequestParam String userId, @RequestParam String description,
                               RedirectAttributes redirect) {
        try {
            ApiKeyInfo key = new ApiKeyInfo();
            key.setKey("dgf-" + UUID.randomUUID().toString().substring(0, 16));
            key.setUserId(userId);
            key.setDescription(description);
            List<ApiKeyInfo> all = new ArrayList<>(userService.getAllApiKeys());
            all.add(key);
            userService.saveApiKeys(all);
            redirect.addFlashAttribute("success", "API key created: " + key.getKey());
        } catch (Exception e) {
            redirect.addFlashAttribute("error", "Failed: " + e.getMessage());
        }
        return "redirect:/admin/apikeys";
    }

    private static final String PROTECTED_ADMIN_API_KEY = "dgf-admin-key-0001";

    @PostMapping("/apikeys/delete")
    public String deleteApiKey(@RequestParam String key, RedirectAttributes redirect) {
        if (PROTECTED_ADMIN_API_KEY.equals(key)) {
            redirect.addFlashAttribute("error",
                    "The admin master API key cannot be deleted. It is a protected system key.");
            return "redirect:/admin/apikeys";
        }
        try {
            List<ApiKeyInfo> all = new ArrayList<>(userService.getAllApiKeys());
            all.removeIf(k -> k.getKey().equals(key));
            userService.saveApiKeys(all);
            redirect.addFlashAttribute("success", "API key deleted");
        } catch (Exception e) {
            redirect.addFlashAttribute("error", "Failed: " + e.getMessage());
        }
        return "redirect:/admin/apikeys";
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  BROKERS
    // ═══════════════════════════════════════════════════════════════════════

    @GetMapping("/brokers")
    public String brokers(Model model) {
        common(model);
        model.addAttribute("brokers", brokerService.getAllBrokers());
        return "pages/admin/brokers";
    }

    @GetMapping("/brokers/create")
    public String brokerCreateForm(Model model) {
        common(model);
        model.addAttribute("editMode", false);
        model.addAttribute("broker", defaultBroker());
        model.addAttribute("brokerId", "");
        return "pages/admin/broker-form";
    }

    @PostMapping("/brokers/create")
    public String brokerCreate(@RequestParam String brokerId, HttpServletRequest req,
                               RedirectAttributes redirect) {
        try {
            if (brokerId == null || brokerId.isBlank()) {
                redirect.addFlashAttribute("error", "Broker ID is required");
                return "redirect:/admin/brokers/create";
            }
            String cleanId = brokerId.trim().toLowerCase().replaceAll("[^a-z0-9\\-_]", "-");
            if (brokerService.getBroker(cleanId) != null) {
                redirect.addFlashAttribute("error", "Broker '" + cleanId + "' already exists");
                return "redirect:/admin/brokers/create";
            }
            Map<String, Object> config = buildBrokerFromRequest(req);
            brokerService.saveBroker(cleanId, config);
            redirect.addFlashAttribute("success", "Broker created: " + cleanId);
        } catch (Exception e) {
            log.error("Failed to create broker", e);
            redirect.addFlashAttribute("error", "Failed: " + e.getMessage());
        }
        return "redirect:/admin/brokers";
    }

    @GetMapping("/brokers/edit/{brokerId}")
    public String brokerEditForm(@PathVariable("brokerId") String brokerId, Model model,
                                 RedirectAttributes redirect) {
        common(model);
        Map<String, Object> broker = brokerService.getBroker(brokerId);
        if (broker == null) {
            redirect.addFlashAttribute("error", "Broker not found: " + brokerId);
            return "redirect:/admin/brokers";
        }
        model.addAttribute("editMode", true);
        model.addAttribute("broker", broker);
        model.addAttribute("brokerId", brokerId);
        return "pages/admin/broker-form";
    }

    @PostMapping("/brokers/edit/{brokerId}")
    public String brokerUpdate(@PathVariable("brokerId") String brokerId, HttpServletRequest req,
                               RedirectAttributes redirect) {
        try {
            Map<String, Object> config = buildBrokerFromRequest(req);
            brokerService.saveBroker(brokerId, config);
            redirect.addFlashAttribute("success", "Broker updated: " + brokerId);
        } catch (Exception e) {
            log.error("Failed to update broker {}", brokerId, e);
            redirect.addFlashAttribute("error", "Failed: " + e.getMessage());
        }
        return "redirect:/admin/brokers";
    }

    @PostMapping("/brokers/delete")
    public String brokerDelete(@RequestParam String brokerId, RedirectAttributes redirect) {
        if (brokerService.deleteBroker(brokerId)) {
            redirect.addFlashAttribute("success", "Broker deleted: " + brokerId);
        } else {
            redirect.addFlashAttribute("error", "Broker not found: " + brokerId);
        }
        return "redirect:/admin/brokers";
    }

    @PostMapping("/brokers/start")
    public String brokerStart(@RequestParam String brokerId, RedirectAttributes redirect) {
        brokerService.startBroker(brokerId);
        redirect.addFlashAttribute("success", "Broker started: " + brokerId);
        return "redirect:/admin/brokers";
    }

    @PostMapping("/brokers/stop")
    public String brokerStop(@RequestParam String brokerId, RedirectAttributes redirect) {
        brokerService.stopBroker(brokerId);
        redirect.addFlashAttribute("success", "Broker stopped: " + brokerId);
        return "redirect:/admin/brokers";
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  CHANNELS
    // ═══════════════════════════════════════════════════════════════════════

    @GetMapping("/channels")
    public String channels(Model model) {
        common(model);
        model.addAttribute("channels", channelService.getAllChannels());
        model.addAttribute("brokerIds", brokerService.listBrokerIds());
        return "pages/admin/channels";
    }

    @GetMapping("/channels/create")
    public String channelCreateForm(Model model) {
        common(model);
        model.addAttribute("editMode", false);
        model.addAttribute("channel", defaultChannel());
        model.addAttribute("channelId", "");
        model.addAttribute("brokerIds", brokerService.listBrokerIds());
        return "pages/admin/channel-form";
    }

    @PostMapping("/channels/create")
    public String channelCreate(@RequestParam String channelId, HttpServletRequest req,
                                RedirectAttributes redirect) {
        try {
            if (channelId == null || channelId.isBlank()) {
                redirect.addFlashAttribute("error", "Channel ID is required");
                return "redirect:/admin/channels/create";
            }
            String cleanId = channelId.trim().toLowerCase().replaceAll("[^a-z0-9\\-_]", "-");
            if (channelService.getChannel(cleanId) != null) {
                redirect.addFlashAttribute("error", "Channel '" + cleanId + "' already exists");
                return "redirect:/admin/channels/create";
            }
            Map<String, Object> config = buildChannelFromRequest(req);
            channelService.saveChannel(cleanId, config);
            redirect.addFlashAttribute("success", "Channel created: " + cleanId);
        } catch (Exception e) {
            log.error("Failed to create channel", e);
            redirect.addFlashAttribute("error", "Failed: " + e.getMessage());
        }
        return "redirect:/admin/channels";
    }

    @GetMapping("/channels/edit/{channelId}")
    public String channelEditForm(@PathVariable("channelId") String channelId, Model model,
                                  RedirectAttributes redirect) {
        common(model);
        Map<String, Object> channel = channelService.getChannel(channelId);
        if (channel == null) {
            redirect.addFlashAttribute("error", "Channel not found: " + channelId);
            return "redirect:/admin/channels";
        }
        model.addAttribute("editMode", true);
        model.addAttribute("channel", channel);
        model.addAttribute("channelId", channelId);
        model.addAttribute("brokerIds", brokerService.listBrokerIds());
        return "pages/admin/channel-form";
    }

    @PostMapping("/channels/edit/{channelId}")
    public String channelUpdate(@PathVariable("channelId") String channelId, HttpServletRequest req,
                                RedirectAttributes redirect) {
        try {
            Map<String, Object> config = buildChannelFromRequest(req);
            channelService.saveChannel(channelId, config);
            redirect.addFlashAttribute("success", "Channel updated: " + channelId);
        } catch (Exception e) {
            log.error("Failed to update channel {}", channelId, e);
            redirect.addFlashAttribute("error", "Failed: " + e.getMessage());
        }
        return "redirect:/admin/channels";
    }

    @PostMapping("/channels/delete")
    public String channelDelete(@RequestParam String channelId, RedirectAttributes redirect) {
        if (channelService.deleteChannel(channelId)) {
            redirect.addFlashAttribute("success", "Channel deleted: " + channelId);
        } else {
            redirect.addFlashAttribute("error", "Channel not found: " + channelId);
        }
        return "redirect:/admin/channels";
    }

    @PostMapping("/channels/start")
    public String channelStart(@RequestParam String channelId, RedirectAttributes redirect) {
        channelService.startChannel(channelId);
        redirect.addFlashAttribute("success", "Channel started: " + channelId);
        return "redirect:/admin/channels";
    }

    @PostMapping("/channels/stop")
    public String channelStop(@RequestParam String channelId, RedirectAttributes redirect) {
        channelService.stopChannel(channelId);
        redirect.addFlashAttribute("success", "Channel stopped: " + channelId);
        return "redirect:/admin/channels";
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════════════════

    private Map<String, Object> buildBrokerFromRequest(HttpServletRequest req) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("type", param(req, "type", "kafka"));
        config.put("description", param(req, "description", ""));
        config.put("enabled", "true".equals(param(req, "enabled", "true")));

        Map<String, Object> conn = new LinkedHashMap<>();
        String type = param(req, "type", "kafka");
        switch (type) {
            case "kafka":
                conn.put("bootstrap_servers", param(req, "conn_bootstrap_servers", "localhost:9092"));
                conn.put("client_id", param(req, "conn_client_id", "dgfacade-client"));
                conn.put("group_id", param(req, "conn_group_id", "dgfacade-consumer-group"));
                conn.put("auto_offset_reset", param(req, "conn_auto_offset_reset", "latest"));
                tryPutInt(conn, "max_poll_records", param(req, "conn_max_poll_records", "500"));
                break;
            case "activemq":
                conn.put("broker_url", param(req, "conn_broker_url", "tcp://localhost:61616"));
                conn.put("username", param(req, "conn_username", ""));
                conn.put("password", param(req, "conn_password", ""));
                conn.put("client_id", param(req, "conn_client_id", "dgfacade-amq"));
                break;
            case "rabbitmq":
                conn.put("host", param(req, "conn_host", "localhost"));
                tryPutInt(conn, "port", param(req, "conn_port", "5672"));
                tryPutInt(conn, "ssl_port", param(req, "conn_ssl_port", "5671"));
                conn.put("virtual_host", param(req, "conn_virtual_host", "/"));
                conn.put("username", param(req, "conn_username", ""));
                conn.put("password", param(req, "conn_password", ""));
                conn.put("exchange", param(req, "conn_exchange", ""));
                tryPutInt(conn, "heartbeat", param(req, "conn_heartbeat", "30"));
                tryPutInt(conn, "prefetch_count", param(req, "conn_prefetch_count", "200"));
                break;
            case "ibmmq":
                conn.put("host", param(req, "conn_host", "localhost"));
                tryPutInt(conn, "port", param(req, "conn_port", "1414"));
                conn.put("queue_manager", param(req, "conn_queue_manager", "QM1"));
                conn.put("channel", param(req, "conn_channel", "DEV.APP.SVRCONN"));
                conn.put("username", param(req, "conn_username", ""));
                conn.put("password", param(req, "conn_password", ""));
                break;
            case "filesystem":
                conn.put("directory", param(req, "conn_directory", "/tmp/dgfacade/inbox"));
                conn.put("poll_interval_seconds", param(req, "conn_poll_interval", "10"));
                conn.put("file_pattern", param(req, "conn_file_pattern", "*.json"));
                break;
            case "sql":
                conn.put("jdbc_url", param(req, "conn_jdbc_url", "jdbc:postgresql://localhost:5432/dgfacade"));
                conn.put("username", param(req, "conn_username", ""));
                conn.put("password", param(req, "conn_password", ""));
                conn.put("table_name", param(req, "conn_table_name", "dg_messages"));
                conn.put("poll_interval_seconds", param(req, "conn_poll_interval", "10"));
                break;
        }
        config.put("connection", conn);

        // SSL
        String sslEnabled = param(req, "ssl_enabled", "false");
        if ("true".equals(sslEnabled)) {
            Map<String, Object> ssl = new LinkedHashMap<>();
            ssl.put("enabled", true);
            ssl.put("format", param(req, "ssl_format", "PEM"));
            ssl.put("protocol", param(req, "ssl_protocol", "TLSv1.3"));
            String fmt = param(req, "ssl_format", "PEM");
            if ("PEM".equals(fmt)) {
                ssl.put("ca_cert_path", param(req, "ssl_ca_cert_path", ""));
                ssl.put("client_cert_path", param(req, "ssl_client_cert_path", ""));
                ssl.put("client_key_path", param(req, "ssl_client_key_path", ""));
            } else {
                ssl.put("keystore_path", param(req, "ssl_keystore_path", ""));
                ssl.put("keystore_password", param(req, "ssl_keystore_password", ""));
                ssl.put("truststore_path", param(req, "ssl_truststore_path", ""));
                ssl.put("truststore_password", param(req, "ssl_truststore_password", ""));
            }
            String cipher = param(req, "ssl_cipher_suite", "");
            if (!cipher.isBlank()) ssl.put("cipher_suite", cipher);
            config.put("ssl", ssl);
        }

        // Extra properties
        String propsText = param(req, "extra_properties", "");
        if (!propsText.isBlank()) {
            Map<String, String> props = new LinkedHashMap<>();
            for (String line : propsText.split("\\n")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || !trimmed.contains("=")) continue;
                String[] kv = trimmed.split("=", 2);
                props.put(kv[0].trim(), kv[1].trim());
            }
            if (!props.isEmpty()) config.put("properties", props);
        }

        return config;
    }

    private Map<String, Object> buildChannelFromRequest(HttpServletRequest req) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("type", param(req, "type", "kafka"));
        config.put("description", param(req, "description", ""));
        config.put("enabled", "true".equals(param(req, "enabled", "true")));
        config.put("broker", param(req, "broker", ""));

        // Subscriptions
        String subName = param(req, "sub_name", "");
        String subType = param(req, "sub_type", "topic");
        if (!subName.isBlank()) {
            List<Map<String, String>> subs = new ArrayList<>();
            String[] names = subName.split(",");
            for (String n : names) {
                String trimmed = n.trim();
                if (!trimmed.isEmpty()) {
                    subs.add(Map.of("name", trimmed, "type", subType));
                }
            }
            config.put("subscriptions", subs);
        }

        // Queue settings
        Map<String, Object> queue = new LinkedHashMap<>();
        tryPutInt(queue, "depth", param(req, "queue_depth", "10000"));
        tryPutInt(queue, "warning_threshold_pct", param(req, "queue_warning_pct", "70"));
        tryPutInt(queue, "critical_threshold_pct", param(req, "queue_critical_pct", "90"));
        tryPutInt(queue, "drain_resume_pct", param(req, "queue_drain_pct", "60"));
        config.put("queue", queue);

        // Retry settings
        Map<String, Object> retry = new LinkedHashMap<>();
        tryPutInt(retry, "max_attempts", param(req, "retry_max_attempts", "3"));
        tryPutInt(retry, "backoff_ms", param(req, "retry_backoff_ms", "1000"));
        String mult = param(req, "retry_backoff_multiplier", "2.0");
        try { retry.put("backoff_multiplier", Double.parseDouble(mult)); } catch (Exception ignored) {}
        config.put("retry", retry);

        return config;
    }

    private Map<String, Object> defaultBroker() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "kafka");
        m.put("description", "");
        m.put("enabled", true);
        Map<String, Object> conn = new LinkedHashMap<>();
        conn.put("bootstrap_servers", "localhost:9092");
        conn.put("client_id", "dgfacade-client");
        conn.put("group_id", "dgfacade-consumer-group");
        m.put("connection", conn);
        return m;
    }

    private Map<String, Object> defaultChannel() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "kafka");
        m.put("description", "");
        m.put("enabled", true);
        m.put("broker", "");
        Map<String, Object> queue = new LinkedHashMap<>();
        queue.put("depth", 10000);
        queue.put("warning_threshold_pct", 70);
        queue.put("critical_threshold_pct", 90);
        queue.put("drain_resume_pct", 60);
        m.put("queue", queue);
        Map<String, Object> retry = new LinkedHashMap<>();
        retry.put("max_attempts", 3);
        retry.put("backoff_ms", 1000);
        retry.put("backoff_multiplier", 2.0);
        m.put("retry", retry);
        return m;
    }

    private String param(HttpServletRequest req, String name, String def) {
        String val = req.getParameter(name);
        return (val != null && !val.isBlank()) ? val.trim() : def;
    }

    private void tryPutInt(Map<String, Object> map, String key, String value) {
        try { map.put(key, Integer.parseInt(value.trim())); } catch (Exception e) { /* skip */ }
    }
}
