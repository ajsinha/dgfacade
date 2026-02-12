/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential. Patent Pending.
 */
package com.dgfacade.web.controller;

import com.dgfacade.common.util.JsonUtil;
import com.dgfacade.server.service.BrokerService;
import com.dgfacade.server.service.InputChannelService;
import com.dgfacade.server.service.OutputChannelService;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Provides configuration validation and bulk export/import endpoints.
 * <p>Enhancement 5: Config validation — validates JSON structure against expected schemas.</p>
 * <p>Enhancement 8: Bulk config import/export — download all configs as ZIP.</p>
 */
@RestController
@RequestMapping("/admin/api")
public class ConfigExportController {

    private static final Logger log = LoggerFactory.getLogger(ConfigExportController.class);

    private final BrokerService brokerService;
    private final InputChannelService inputChannelService;
    private final OutputChannelService outputChannelService;

    @Value("${dgfacade.brokers.config-dir:config/brokers}")
    private String brokersDir;
    @Value("${dgfacade.input-channels.config-dir:config/input-channels}")
    private String inputChannelsDir;
    @Value("${dgfacade.output-channels.config-dir:config/output-channels}")
    private String outputChannelsDir;
    @Value("${dgfacade.ingesters.config-dir:config/ingesters}")
    private String ingestersDir;
    @Value("${dgfacade.config.handlers-dir:config/handlers}")
    private String handlersDir;

    public ConfigExportController(BrokerService brokerService,
                                  InputChannelService inputChannelService,
                                  OutputChannelService outputChannelService) {
        this.brokerService = brokerService;
        this.inputChannelService = inputChannelService;
        this.outputChannelService = outputChannelService;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Enhancement 5: Config Validation
    // ═══════════════════════════════════════════════════════════════════

    /** POST /admin/api/validate/broker — Validate a broker config JSON. */
    @PostMapping("/validate/broker")
    public ResponseEntity<Map<String, Object>> validateBroker(@RequestBody String jsonBody) {
        return ResponseEntity.ok(validateConfig(jsonBody, "broker",
                List.of("type"), List.of("connection"), List.of("kafka", "activemq", "rabbitmq", "ibmmq", "filesystem", "sql")));
    }

    /** POST /admin/api/validate/input-channel — Validate an input channel config JSON. */
    @PostMapping("/validate/input-channel")
    public ResponseEntity<Map<String, Object>> validateInputChannel(@RequestBody String jsonBody) {
        return ResponseEntity.ok(validateConfig(jsonBody, "input-channel",
                List.of("type", "broker"), List.of("destinations"), List.of("kafka", "jms", "activemq", "rabbitmq", "ibmmq", "filesystem", "sql")));
    }

    /** POST /admin/api/validate/output-channel — Validate an output channel config JSON. */
    @PostMapping("/validate/output-channel")
    public ResponseEntity<Map<String, Object>> validateOutputChannel(@RequestBody String jsonBody) {
        return ResponseEntity.ok(validateConfig(jsonBody, "output-channel",
                List.of("type", "broker"), List.of("destinations"), List.of("kafka", "jms", "activemq", "rabbitmq", "ibmmq", "filesystem", "sql")));
    }

    /** POST /admin/api/validate/ingester — Validate an ingester config JSON. */
    @PostMapping("/validate/ingester")
    public ResponseEntity<Map<String, Object>> validateIngester(@RequestBody String jsonBody) {
        return ResponseEntity.ok(validateIngesterConfig(jsonBody));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> validateConfig(String jsonBody, String configType,
                                                List<String> requiredFields, List<String> recommendedFields,
                                                List<String> validTypes) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        Map<String, Object> config;
        try {
            config = JsonUtil.fromJson(jsonBody, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of("valid", false, "errors", List.of("Invalid JSON: " + e.getMessage()), "warnings", List.of());
        }

        // Check required fields
        for (String field : requiredFields) {
            Object val = config.get(field);
            if (val == null || (val instanceof String s && s.isBlank())) {
                errors.add("Missing required field: '" + field + "'");
            }
        }

        // Check recommended fields
        for (String field : recommendedFields) {
            if (!config.containsKey(field)) {
                warnings.add("Recommended field missing: '" + field + "'");
            }
        }

        // Validate type value
        String type = (String) config.get("type");
        if (type != null && !validTypes.contains(type.toLowerCase())) {
            errors.add("Unknown type '" + type + "'. Valid types: " + validTypes);
        }

        // Check broker reference resolves (for channels)
        if (config.containsKey("broker")) {
            String brokerId = (String) config.get("broker");
            if (brokerId != null && !brokerId.isBlank() && brokerService.getBroker(brokerId) == null) {
                warnings.add("Broker reference '" + brokerId + "' not found — ensure it exists before starting");
            }
        }

        // Check destinations structure
        if (config.containsKey("destinations")) {
            Object dests = config.get("destinations");
            if (!(dests instanceof List)) {
                errors.add("'destinations' must be an array");
            } else {
                List<?> destList = (List<?>) dests;
                if (destList.isEmpty()) {
                    warnings.add("'destinations' array is empty");
                }
                for (int i = 0; i < destList.size(); i++) {
                    if (!(destList.get(i) instanceof Map)) {
                        errors.add("destinations[" + i + "] must be an object with 'name' and 'type'");
                    } else {
                        Map<?, ?> dest = (Map<?, ?>) destList.get(i);
                        if (!dest.containsKey("name")) {
                            errors.add("destinations[" + i + "].name is required");
                        }
                    }
                }
            }
        }

        // SSL validation
        if (config.containsKey("ssl")) {
            Object ssl = config.get("ssl");
            if (ssl instanceof Map<?, ?> sslMap) {
                if (Boolean.TRUE.equals(sslMap.get("enabled"))) {
                    if (!sslMap.containsKey("ca_cert_path")) {
                        warnings.add("SSL enabled but 'ca_cert_path' not specified");
                    }
                }
            }
        }

        boolean valid = errors.isEmpty();
        return Map.of("valid", valid, "errors", errors, "warnings", warnings);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> validateIngesterConfig(String jsonBody) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        Map<String, Object> config;
        try {
            config = JsonUtil.fromJson(jsonBody, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of("valid", false, "errors", List.of("Invalid JSON: " + e.getMessage()), "warnings", List.of());
        }

        // Check input_channel reference
        String channelId = (String) config.get("input_channel");
        if (channelId == null || channelId.isBlank()) {
            errors.add("Missing required field: 'input_channel'");
        } else {
            Map<String, Object> ch = inputChannelService.getChannel(channelId);
            if (ch == null) {
                warnings.add("Input channel '" + channelId + "' not found — ensure it exists before starting");
            } else {
                // Verify channel's broker also exists
                String brokerId = (String) ch.get("broker");
                if (brokerId != null && brokerService.getBroker(brokerId) == null) {
                    warnings.add("Channel '" + channelId + "' references broker '" + brokerId + "' which is not found");
                }
            }
        }

        if (!config.containsKey("description")) {
            warnings.add("Recommended field missing: 'description'");
        }

        boolean valid = errors.isEmpty();
        return Map.of("valid", valid, "errors", errors, "warnings", warnings);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Enhancement 8: Bulk Config Export
    // ═══════════════════════════════════════════════════════════════════

    /** GET /admin/api/config/export — Download all configs as ZIP. */
    @GetMapping("/config/export")
    public ResponseEntity<byte[]> exportConfigs() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            addDirectoryToZip(zos, brokersDir, "brokers/");
            addDirectoryToZip(zos, inputChannelsDir, "input-channels/");
            addDirectoryToZip(zos, outputChannelsDir, "output-channels/");
            addDirectoryToZip(zos, ingestersDir, "ingesters/");
            addDirectoryToZip(zos, handlersDir, "handlers/");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("dgfacade-config-export.zip").build());

        log.info("Config export: ZIP generated ({} bytes)", baos.size());
        return new ResponseEntity<>(baos.toByteArray(), headers, HttpStatus.OK);
    }

    private void addDirectoryToZip(ZipOutputStream zos, String dir, String prefix) throws IOException {
        Path dirPath = Path.of(dir);
        if (!Files.exists(dirPath) || !Files.isDirectory(dirPath)) return;

        try (var files = Files.list(dirPath)) {
            files.filter(p -> p.toString().endsWith(".json")).sorted().forEach(file -> {
                try {
                    zos.putNextEntry(new ZipEntry(prefix + file.getFileName().toString()));
                    zos.write(Files.readAllBytes(file));
                    zos.closeEntry();
                } catch (IOException e) {
                    log.warn("Failed to add {} to ZIP: {}", file, e.getMessage());
                }
            });
        }
    }
}
