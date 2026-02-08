/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential. Patent Pending.
 */
package com.dgfacade.server.handler;

import com.dgfacade.common.model.DGRequest;
import com.dgfacade.common.model.DGResponse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Admin-only handler for HTTP health probes and URL testing.
 *
 * <p>Makes HTTP requests to given URLs and reports status code, response time,
 * headers, and body snippet. Useful for monitoring upstream dependencies,
 * verifying endpoint availability, and pre-flight checks before publishing.</p>
 *
 * <p>Required payload fields:</p>
 * <ul>
 *   <li>{@code url} — the target URL (required)</li>
 *   <li>{@code method} — HTTP method: GET (default), HEAD, POST, PUT, DELETE</li>
 *   <li>{@code timeout_seconds} — request timeout (default 10, max 30)</li>
 *   <li>{@code headers} — optional map of request headers</li>
 *   <li>{@code body} — optional request body for POST/PUT</li>
 *   <li>{@code follow_redirects} — follow 3xx redirects (default true)</li>
 * </ul>
 */
public class HttpProbeHandler implements DGHandler {

    private volatile boolean stopped = false;
    private int maxTimeoutSeconds = 30;

    @Override
    public void construct(Map<String, Object> config) {
        if (config != null && config.containsKey("max_timeout_seconds")) {
            maxTimeoutSeconds = ((Number) config.get("max_timeout_seconds")).intValue();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public DGResponse execute(DGRequest request) {
        if (stopped) return DGResponse.error(request.getRequestId(), "Handler was stopped");
        try {
            Map<String, Object> payload = request.getPayload();
            if (payload == null || !payload.containsKey("url")) {
                return DGResponse.error(request.getRequestId(), "'url' field is required");
            }

            String url = (String) payload.get("url");
            String method = String.valueOf(payload.getOrDefault("method", "GET")).toUpperCase();
            int timeoutSec = Math.min(
                    ((Number) payload.getOrDefault("timeout_seconds", 10)).intValue(),
                    maxTimeoutSeconds);
            boolean followRedirects = Boolean.parseBoolean(
                    String.valueOf(payload.getOrDefault("follow_redirects", "true")));

            // Build HTTP client
            HttpClient.Builder clientBuilder = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(timeoutSec));
            if (followRedirects) {
                clientBuilder.followRedirects(HttpClient.Redirect.NORMAL);
            } else {
                clientBuilder.followRedirects(HttpClient.Redirect.NEVER);
            }
            HttpClient client = clientBuilder.build();

            // Build request
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(timeoutSec));

            // Custom headers
            Map<String, String> customHeaders = (Map<String, String>) payload.get("headers");
            if (customHeaders != null) {
                customHeaders.forEach(reqBuilder::header);
            }

            // Method and body
            String body = (String) payload.get("body");
            switch (method) {
                case "GET" -> reqBuilder.GET();
                case "HEAD" -> reqBuilder.method("HEAD", HttpRequest.BodyPublishers.noBody());
                case "DELETE" -> reqBuilder.DELETE();
                case "POST" -> reqBuilder.POST(body != null ?
                        HttpRequest.BodyPublishers.ofString(body) : HttpRequest.BodyPublishers.noBody());
                case "PUT" -> reqBuilder.PUT(body != null ?
                        HttpRequest.BodyPublishers.ofString(body) : HttpRequest.BodyPublishers.noBody());
                default -> {
                    return DGResponse.error(request.getRequestId(),
                            "Unsupported method: " + method + ". Use GET, HEAD, POST, PUT, DELETE.");
                }
            }

            // Execute and time the request
            Instant start = Instant.now();
            HttpResponse<String> response = client.send(reqBuilder.build(),
                    HttpResponse.BodyHandlers.ofString());
            long latencyMs = Duration.between(start, Instant.now()).toMillis();

            // Build result
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("url", url);
            result.put("method", method);
            result.put("status_code", response.statusCode());
            result.put("status_family", getStatusFamily(response.statusCode()));
            result.put("latency_ms", latencyMs);
            result.put("content_length", response.body() != null ? response.body().length() : 0);

            // Response headers (top-level only)
            Map<String, String> responseHeaders = new LinkedHashMap<>();
            response.headers().map().forEach((k, v) -> responseHeaders.put(k, String.join(", ", v)));
            result.put("response_headers", responseHeaders);

            // Body snippet (first 500 chars)
            if (response.body() != null && !response.body().isEmpty()) {
                String snippet = response.body().length() > 500 ?
                        response.body().substring(0, 500) + "... [truncated]" : response.body();
                result.put("body_snippet", snippet);
            }

            result.put("reachable", response.statusCode() < 500);
            result.put("healthy", response.statusCode() >= 200 && response.statusCode() < 300);
            result.put("probe_timestamp", Instant.now().toString());

            return DGResponse.success(request.getRequestId(), result);
        } catch (java.net.http.HttpTimeoutException e) {
            return DGResponse.error(request.getRequestId(), "Timeout: " + e.getMessage());
        } catch (java.net.ConnectException e) {
            return DGResponse.error(request.getRequestId(), "Connection refused: " + e.getMessage());
        } catch (Exception e) {
            return DGResponse.error(request.getRequestId(), "Probe error: " + e.getMessage());
        }
    }

    private String getStatusFamily(int code) {
        return switch (code / 100) {
            case 1 -> "INFORMATIONAL";
            case 2 -> "SUCCESS";
            case 3 -> "REDIRECTION";
            case 4 -> "CLIENT_ERROR";
            case 5 -> "SERVER_ERROR";
            default -> "UNKNOWN";
        };
    }

    @Override
    public void stop() { stopped = true; }

    @Override
    public void cleanup() {}
}
