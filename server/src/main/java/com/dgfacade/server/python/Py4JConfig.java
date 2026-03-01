/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential.
 */
package com.dgfacade.server.python;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;

/**
 * Extended configuration model for Py4J Python worker pool.
 * Loaded from config/python/py4j.json.
 * <p>
 * Includes handler definitions that map request types to Python classes,
 * enabling the Admin UI to display registered Python handlers.
 *
 * @since 1.6.2
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Py4JConfig {

    private boolean enabled = false;
    private int workerCount = 2;
    private String pythonBinary = "python3";
    private List<String> pythonPath = new ArrayList<>(List.of("python", "python/handlers"));
    private String gatewayHost = "127.0.0.1";
    private int gatewayPortStart = 25333;
    private int callbackPortStart = 25433;
    private int workerStartupTimeoutSeconds = 30;
    private int workerHealthCheckIntervalSeconds = 15;
    private int workerRestartDelaySeconds = 5;
    private int maxConsecutiveFailures = 3;
    private int requestTimeoutSeconds = 300;
    private boolean logPythonOutput = true;
    private int maxRestartAttempts = 10;
    private List<PythonHandlerDef> handlers = new ArrayList<>();

    // ── Getters & Setters ──────────────────────────────────────────

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getWorkerCount() { return workerCount; }
    public void setWorkerCount(int workerCount) { this.workerCount = workerCount; }

    public String getPythonBinary() { return pythonBinary; }
    public void setPythonBinary(String pythonBinary) { this.pythonBinary = pythonBinary; }

    public List<String> getPythonPath() { return pythonPath; }
    public void setPythonPath(List<String> pythonPath) { this.pythonPath = pythonPath; }

    public String getGatewayHost() { return gatewayHost; }
    public void setGatewayHost(String gatewayHost) { this.gatewayHost = gatewayHost; }

    public int getGatewayPortStart() { return gatewayPortStart; }
    public void setGatewayPortStart(int gatewayPortStart) { this.gatewayPortStart = gatewayPortStart; }

    public int getCallbackPortStart() { return callbackPortStart; }
    public void setCallbackPortStart(int callbackPortStart) { this.callbackPortStart = callbackPortStart; }

    public int getWorkerStartupTimeoutSeconds() { return workerStartupTimeoutSeconds; }
    public void setWorkerStartupTimeoutSeconds(int v) { this.workerStartupTimeoutSeconds = v; }

    public int getWorkerHealthCheckIntervalSeconds() { return workerHealthCheckIntervalSeconds; }
    public void setWorkerHealthCheckIntervalSeconds(int v) { this.workerHealthCheckIntervalSeconds = v; }

    public int getWorkerRestartDelaySeconds() { return workerRestartDelaySeconds; }
    public void setWorkerRestartDelaySeconds(int v) { this.workerRestartDelaySeconds = v; }

    public int getMaxConsecutiveFailures() { return maxConsecutiveFailures; }
    public void setMaxConsecutiveFailures(int v) { this.maxConsecutiveFailures = v; }

    public int getRequestTimeoutSeconds() { return requestTimeoutSeconds; }
    public void setRequestTimeoutSeconds(int v) { this.requestTimeoutSeconds = v; }

    public boolean isLogPythonOutput() { return logPythonOutput; }
    public void setLogPythonOutput(boolean v) { this.logPythonOutput = v; }

    public int getMaxRestartAttempts() { return maxRestartAttempts; }
    public void setMaxRestartAttempts(int v) { this.maxRestartAttempts = v; }

    public List<PythonHandlerDef> getHandlers() { return handlers; }
    public void setHandlers(List<PythonHandlerDef> handlers) { this.handlers = handlers; }

    /**
     * Definition of a single Python handler as declared in py4j.json.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PythonHandlerDef {
        private String requestType;
        private String pythonModule;
        private String pythonClass;
        private String description = "";
        private boolean isPython = true;

        public String getRequestType() { return requestType; }
        public void setRequestType(String requestType) { this.requestType = requestType; }

        public String getPythonModule() { return pythonModule; }
        public void setPythonModule(String pythonModule) { this.pythonModule = pythonModule; }

        public String getPythonClass() { return pythonClass; }
        public void setPythonClass(String pythonClass) { this.pythonClass = pythonClass; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public boolean isPython() { return isPython; }
        public void setIsPython(boolean isPython) { this.isPython = isPython; }
        public void setPython(boolean python) { this.isPython = python; }

        @Override
        public String toString() {
            return "PythonHandlerDef{" + requestType + " -> " + pythonModule + "." + pythonClass + "}";
        }
    }
}
