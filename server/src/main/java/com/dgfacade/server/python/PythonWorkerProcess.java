/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential.
 */
package com.dgfacade.server.python;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents a single Python worker subprocess.
 *
 * <p>Each worker runs {@code dgfacade_worker.py}, which opens a TCP server on
 * a designated port.  The Java side connects to that port and exchanges JSON
 * messages using a simple length-prefixed protocol:</p>
 *
 * <pre>
 *   → 4-byte big-endian length + UTF-8 JSON request
 *   ← 4-byte big-endian length + UTF-8 JSON response
 * </pre>
 */
public class PythonWorkerProcess {

    private static final Logger log = LoggerFactory.getLogger(PythonWorkerProcess.class);

    public enum State { STARTING, READY, BUSY, DEAD, STOPPED }

    private final int workerId;
    private final int port;
    private final PythonConfig config;

    private Process process;
    private volatile State state = State.STOPPED;
    private Instant startedAt;
    private Instant lastHealthCheck;
    private final AtomicLong requestsHandled = new AtomicLong(0);
    private final AtomicLong requestsFailed = new AtomicLong(0);
    private final AtomicInteger restartCount = new AtomicInteger(0);
    private Thread stdoutThread;
    private Thread stderrThread;

    // Circular buffer for recent log lines from this worker
    private final List<String> recentLogs = Collections.synchronizedList(new ArrayList<>());
    private static final int MAX_LOG_LINES = 200;

    public PythonWorkerProcess(int workerId, int port, PythonConfig config) {
        this.workerId = workerId;
        this.port = port;
        this.config = config;
    }

    /**
     * Start the Python worker subprocess.
     */
    public synchronized boolean start(String propertiesJson) {
        if (state == State.READY || state == State.BUSY) {
            log.warn("Python worker {} already running", workerId);
            return true;
        }

        state = State.STARTING;
        try {
            // Build PYTHONPATH
            String pythonPath = String.join(File.pathSeparator, config.getPythonPath());

            // Build command
            List<String> cmd = new ArrayList<>();
            cmd.add(config.getPythonBinary());
            cmd.add("-u"); // unbuffered output
            cmd.add("python/dgfacade_worker.py");
            cmd.add("--port");
            cmd.add(String.valueOf(port));
            cmd.add("--worker-id");
            cmd.add(String.valueOf(workerId));

            ProcessBuilder pb = new ProcessBuilder(cmd);
            Map<String, String> env = pb.environment();
            // Merge PYTHONPATH
            String existingPath = env.getOrDefault("PYTHONPATH", "");
            env.put("PYTHONPATH", pythonPath + (existingPath.isEmpty() ? "" : File.pathSeparator + existingPath));
            // Pass properties JSON as environment variable to avoid CLI escaping issues
            env.put("DGFACADE_PROPERTIES", propertiesJson);

            pb.redirectErrorStream(false);
            process = pb.start();

            // Stream stdout/stderr to logger
            stdoutThread = streamToLogger(process.getInputStream(), "python-worker-" + workerId + "-stdout");
            stderrThread = streamToLogger(process.getErrorStream(), "python-worker-" + workerId + "-stderr");

            // Wait for the worker to become ready (it prints READY on stdout)
            if (!waitForReady()) {
                log.error("Python worker {} failed to start within {} seconds", workerId,
                        config.getWorkerStartupTimeoutSeconds());
                kill();
                state = State.DEAD;
                return false;
            }

            startedAt = Instant.now();
            state = State.READY;
            log.info("Python worker {} started on port {} (pid={})", workerId, port, process.pid());
            return true;

        } catch (Exception e) {
            log.error("Failed to start Python worker {}: {}", workerId, e.getMessage(), e);
            state = State.DEAD;
            return false;
        }
    }

    /**
     * Send a JSON request to the Python worker and get a JSON response.
     */
    public String executeRequest(String requestJson) throws IOException {
        if (state != State.READY) {
            throw new IOException("Worker " + workerId + " is not ready (state=" + state + ")");
        }

        state = State.BUSY;
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(config.getRequestTimeoutSeconds() * 1000);

            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            // Send length-prefixed JSON
            byte[] requestBytes = requestJson.getBytes(StandardCharsets.UTF_8);
            out.writeInt(requestBytes.length);
            out.write(requestBytes);
            out.flush();

            // Read length-prefixed JSON response
            int responseLength = in.readInt();
            if (responseLength <= 0 || responseLength > 50 * 1024 * 1024) {
                throw new IOException("Invalid response length from worker " + workerId + ": " + responseLength);
            }
            byte[] responseBytes = new byte[responseLength];
            in.readFully(responseBytes);

            requestsHandled.incrementAndGet();
            return new String(responseBytes, StandardCharsets.UTF_8);

        } catch (Exception e) {
            requestsFailed.incrementAndGet();
            throw new IOException("Worker " + workerId + " execution failed: " + e.getMessage(), e);
        } finally {
            // Only return to READY if process is still alive
            if (isAlive()) {
                state = State.READY;
            } else {
                state = State.DEAD;
            }
        }
    }

    /**
     * Send a health check ping to the worker.
     */
    public boolean healthCheck() {
        if (!isAlive()) {
            state = State.DEAD;
            return false;
        }
        try {
            String response = executeRequest("{\"command\":\"ping\"}");
            lastHealthCheck = Instant.now();
            return response != null && response.contains("\"pong\"");
        } catch (Exception e) {
            log.warn("Health check failed for Python worker {}: {}", workerId, e.getMessage());
            return false;
        }
    }

    /**
     * Gracefully stop the worker process.
     */
    public synchronized void stop() {
        state = State.STOPPED;
        if (process != null && process.isAlive()) {
            try {
                // Send shutdown command via TCP
                try (Socket socket = new Socket("127.0.0.1", port)) {
                    socket.setSoTimeout(5000);
                    DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                    byte[] cmd = "{\"command\":\"shutdown\"}".getBytes(StandardCharsets.UTF_8);
                    out.writeInt(cmd.length);
                    out.write(cmd);
                    out.flush();
                } catch (Exception ignored) { /* best effort */ }

                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    log.warn("Python worker {} force-killed", workerId);
                }
            } catch (InterruptedException e) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
            }
            log.info("Python worker {} stopped", workerId);
        }
    }

    /**
     * Force-kill the process.
     */
    public void kill() {
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
        }
        state = State.DEAD;
    }

    public boolean isAlive() {
        return process != null && process.isAlive();
    }

    public int getRestartCount() { return restartCount.get(); }
    public void incrementRestartCount() { restartCount.incrementAndGet(); }

    // --- Status ---
    public int getWorkerId() { return workerId; }
    public int getPort() { return port; }
    public State getState() { return state; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getLastHealthCheck() { return lastHealthCheck; }
    public long getRequestsHandled() { return requestsHandled.get(); }
    public long getRequestsFailed() { return requestsFailed.get(); }
    public long getPid() { return process != null ? process.pid() : -1; }

    /**
     * Get recent log lines captured from this worker's stdout/stderr.
     */
    public List<String> getRecentLogs() {
        synchronized (recentLogs) {
            return new ArrayList<>(recentLogs);
        }
    }

    public Map<String, Object> toStatusMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("worker_id", workerId);
        m.put("port", port);
        m.put("state", state.name());
        m.put("pid", getPid());
        m.put("started_at", startedAt != null ? startedAt.toString() : null);
        m.put("last_health_check", lastHealthCheck != null ? lastHealthCheck.toString() : null);
        m.put("requests_handled", requestsHandled.get());
        m.put("requests_failed", requestsFailed.get());
        m.put("restart_count", restartCount.get());
        m.put("alive", isAlive());
        return m;
    }

    // --- Private helpers ---

    private boolean waitForReady() {
        long deadline = System.currentTimeMillis() + (config.getWorkerStartupTimeoutSeconds() * 1000L);
        while (System.currentTimeMillis() < deadline) {
            if (!isAlive()) return false;
            // Try TCP connection to see if the worker is listening
            try (Socket s = new Socket("127.0.0.1", port)) {
                s.setSoTimeout(2000);
                DataOutputStream out = new DataOutputStream(s.getOutputStream());
                DataInputStream in = new DataInputStream(s.getInputStream());
                byte[] ping = "{\"command\":\"ping\"}".getBytes(StandardCharsets.UTF_8);
                out.writeInt(ping.length);
                out.write(ping);
                out.flush();
                int len = in.readInt();
                byte[] resp = new byte[len];
                in.readFully(resp);
                String r = new String(resp, StandardCharsets.UTF_8);
                if (r.contains("pong")) return true;
            } catch (Exception ignored) {
                // Not ready yet
            }
            try { Thread.sleep(500); } catch (InterruptedException e) { return false; }
        }
        return false;
    }

    private Thread streamToLogger(InputStream is, String name) {
        Thread t = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (config.isLogPythonOutput()) {
                        log.info("[{}] {}", name, line);
                    }
                    // Capture to recent logs buffer
                    synchronized (recentLogs) {
                        if (recentLogs.size() >= MAX_LOG_LINES) {
                            recentLogs.remove(0);
                        }
                        recentLogs.add(java.time.Instant.now().toString() + " " + line);
                    }
                }
            } catch (IOException e) {
                // Stream closed — expected on shutdown
            }
        }, name);
        t.setDaemon(true);
        t.start();
        return t;
    }
}
