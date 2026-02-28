/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential.
 */
package com.dgfacade.server.python;

import com.dgfacade.common.model.DGRequest;
import com.dgfacade.common.model.DGResponse;
import com.dgfacade.common.util.JsonUtil;
import com.dgfacade.server.handler.DGHandler;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Bridge handler that delegates execution to a Python worker process.
 *
 * <h3>How It Works</h3>
 * <ol>
 *   <li>{@code construct()} — receives handler config including the Python module and class</li>
 *   <li>{@code execute()} — serializes the {@link DGRequest} to JSON, sends it to a Python
 *       worker via the {@link PythonWorkerManager}, and deserializes the JSON response
 *       back into a {@link DGResponse}</li>
 *   <li>The Python handler receives the request as a dict and returns a dict following
 *       the same contract as Java handlers</li>
 * </ol>
 *
 * <h3>Handler Config Fields</h3>
 * <pre>
 * {
 *   "handler_class": "com.dgfacade.server.python.DGHandlerPython",
 *   "is_python": true,
 *   "config": {
 *     "python_module": "handlers.echo_handler",
 *     "python_class": "EchoHandler"
 *   }
 * }
 * </pre>
 */
public class DGHandlerPython implements DGHandler {

    private static final Logger log = LoggerFactory.getLogger(DGHandlerPython.class);

    /** Static reference to the worker manager — set by AppConfig at startup. */
    private static volatile PythonWorkerManager workerManager;

    private Map<String, Object> config;
    private String pythonModule;
    private String pythonClass;
    private volatile boolean stopped = false;

    /**
     * Called by AppConfig to inject the worker manager singleton.
     */
    public static void setWorkerManager(PythonWorkerManager manager) {
        workerManager = manager;
    }

    public static PythonWorkerManager getWorkerManager() {
        return workerManager;
    }

    @Override
    public void construct(Map<String, Object> config) {
        this.config = config != null ? config : Map.of();
        this.pythonModule = (String) this.config.getOrDefault("python_module", "");
        this.pythonClass = (String) this.config.getOrDefault("python_class", "");

        if (pythonModule.isEmpty() || pythonClass.isEmpty()) {
            log.error("DGHandlerPython requires 'python_module' and 'python_class' in config");
        }
    }

    @Override
    public DGResponse execute(DGRequest request) {
        if (stopped) {
            return DGResponse.error(request.getRequestId(), "Handler was stopped");
        }

        if (workerManager == null || !workerManager.isRunning()) {
            return DGResponse.error(request.getRequestId(),
                    "Python worker pool is not running — enable it in config/python/py4j.json");
        }

        if (pythonModule.isEmpty() || pythonClass.isEmpty()) {
            return DGResponse.error(request.getRequestId(),
                    "Python handler not configured: python_module and python_class are required in handler config");
        }

        long startTime = System.currentTimeMillis();

        try {
            // Serialize DGRequest to JSON
            String requestJson = JsonUtil.toJson(request);
            String configJson = JsonUtil.toJson(config);

            log.debug("Dispatching to Python handler: {}.{} (request={})",
                    pythonModule, pythonClass, request.getRequestId());

            // Execute via worker pool
            String responseJson = workerManager.executeHandler(
                    pythonModule, pythonClass, requestJson, configJson);

            // Parse the Python response
            Map<String, Object> responseMap = JsonUtil.fromJson(responseJson,
                    new TypeReference<Map<String, Object>>() {});

            long execTime = System.currentTimeMillis() - startTime;

            // Check for error from Python
            String pyStatus = (String) responseMap.getOrDefault("status", "SUCCESS");
            if ("ERROR".equalsIgnoreCase(pyStatus)) {
                String errorMsg = (String) responseMap.getOrDefault("error_message",
                        "Python handler returned an error");
                DGResponse resp = DGResponse.error(request.getRequestId(), errorMsg);
                resp.setHandlerId(pythonModule + "." + pythonClass);
                resp.setExecutionTimeMs(execTime);
                return resp;
            }

            // Build success response
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) responseMap.getOrDefault("data", Map.of());
            DGResponse resp = DGResponse.success(request.getRequestId(), data);
            resp.setHandlerId(pythonModule + "." + pythonClass);
            resp.setExecutionTimeMs(execTime);
            return resp;

        } catch (Exception e) {
            long execTime = System.currentTimeMillis() - startTime;
            log.error("Python handler {}.{} failed: {}", pythonModule, pythonClass, e.getMessage(), e);
            DGResponse resp = DGResponse.error(request.getRequestId(),
                    "Python handler execution failed: " + e.getMessage());
            resp.setHandlerId(pythonModule + "." + pythonClass);
            resp.setExecutionTimeMs(execTime);
            return resp;
        }
    }

    @Override
    public void stop() {
        stopped = true;
    }

    @Override
    public void cleanup() {
        config = null;
    }

    @Override
    public String getHandlerId() {
        return "PythonHandler[" + pythonModule + "." + pythonClass + "]";
    }
}
