/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential.
 */
package com.dgfacade.server.python;

import com.dgfacade.common.util.JsonUtil;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Configuration loaded from config/python/py4j.json.
 * Controls the Python worker pool lifecycle.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PythonConfig {

    private static final Logger log = LoggerFactory.getLogger(PythonConfig.class);

    @JsonProperty("enabled")
    private boolean enabled = false;

    @JsonProperty("worker_count")
    private int workerCount = 2;

    @JsonProperty("python_binary")
    private String pythonBinary = "python3";

    @JsonProperty("python_path")
    private List<String> pythonPath = List.of("python", "python/handlers");

    @JsonProperty("worker_startup_timeout_seconds")
    private int workerStartupTimeoutSeconds = 30;

    @JsonProperty("worker_health_check_interval_seconds")
    private int workerHealthCheckIntervalSeconds = 10;

    @JsonProperty("worker_restart_delay_seconds")
    private int workerRestartDelaySeconds = 5;

    @JsonProperty("max_restart_attempts")
    private int maxRestartAttempts = 10;

    @JsonProperty("request_timeout_seconds")
    private int requestTimeoutSeconds = 300;

    @JsonProperty("gateway_port_range_start")
    private int gatewayPortRangeStart = 25333;

    @JsonProperty("callback_port_range_start")
    private int callbackPortRangeStart = 25400;

    @JsonProperty("log_python_output")
    private boolean logPythonOutput = true;

    public static PythonConfig load(String configDir) {
        File file = new File(configDir, "py4j.json");
        if (!file.exists()) {
            log.info("Python config not found at {} — Python handlers disabled", file.getAbsolutePath());
            return new PythonConfig();
        }
        try {
            PythonConfig config = JsonUtil.fromFile(file, new TypeReference<PythonConfig>() {});
            log.info("Python config loaded: enabled={}, workers={}, binary={}",
                    config.enabled, config.workerCount, config.pythonBinary);
            return config;
        } catch (IOException e) {
            log.error("Failed to load Python config from {}: {}", file.getAbsolutePath(), e.getMessage());
            return new PythonConfig();
        }
    }

    // --- Getters ---
    public boolean isEnabled() { return enabled; }
    public int getWorkerCount() { return workerCount; }
    public String getPythonBinary() { return pythonBinary; }
    public List<String> getPythonPath() { return pythonPath; }
    public int getWorkerStartupTimeoutSeconds() { return workerStartupTimeoutSeconds; }
    public int getWorkerHealthCheckIntervalSeconds() { return workerHealthCheckIntervalSeconds; }
    public int getWorkerRestartDelaySeconds() { return workerRestartDelaySeconds; }
    public int getMaxRestartAttempts() { return maxRestartAttempts; }
    public int getRequestTimeoutSeconds() { return requestTimeoutSeconds; }
    public int getGatewayPortRangeStart() { return gatewayPortRangeStart; }
    public int getCallbackPortRangeStart() { return callbackPortRangeStart; }
    public boolean isLogPythonOutput() { return logPythonOutput; }
}
