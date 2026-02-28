/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential.
 */
package com.dgfacade.server.handler;

import com.dgfacade.common.model.DGRequest;
import com.dgfacade.common.model.DGResponse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Handler for hashing, encoding, and digest operations.
 *
 * <p>Provides cryptographic hash functions and encoding utilities
 * commonly needed for data integrity checks, token generation, and ETL.</p>
 *
 * <p>Supported operations via {@code payload.operation}:</p>
 * <ul>
 *   <li><b>MD5</b> — MD5 digest (hex)</li>
 *   <li><b>SHA1</b> — SHA-1 digest (hex)</li>
 *   <li><b>SHA256</b> — SHA-256 digest (hex)</li>
 *   <li><b>SHA512</b> — SHA-512 digest (hex)</li>
 *   <li><b>BASE64_ENCODE</b> — Base64 encode text</li>
 *   <li><b>BASE64_DECODE</b> — Base64 decode text</li>
 *   <li><b>HEX_ENCODE</b> — convert text to hex</li>
 *   <li><b>HEX_DECODE</b> — convert hex back to text</li>
 *   <li><b>UUID</b> — generate a new random UUID</li>
 *   <li><b>CHECKSUM</b> — returns MD5 + SHA256 together for quick comparison</li>
 * </ul>
 *
 * <p>Required payload fields: {@code operation}, {@code input} (except for UUID).</p>
 */
public class HashHandler implements DGHandler {

    private volatile boolean stopped = false;

    @Override
    public void construct(Map<String, Object> config) { /* no config needed */ }

    @Override
    public DGResponse execute(DGRequest request) {
        if (stopped) return DGResponse.error(request.getRequestId(), "Handler was stopped");
        try {
            Map<String, Object> payload = request.getPayload();
            if (payload == null) return DGResponse.error(request.getRequestId(), "Payload is required");

            String operation = String.valueOf(payload.getOrDefault("operation", "SHA256")).toUpperCase();
            String input = (String) payload.get("input");

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("operation", operation);

            switch (operation) {
                case "MD5" -> {
                    requireInput(input, operation);
                    result.put("input_length", input.length());
                    result.put("result", hexDigest("MD5", input));
                }
                case "SHA1" -> {
                    requireInput(input, operation);
                    result.put("input_length", input.length());
                    result.put("result", hexDigest("SHA-1", input));
                }
                case "SHA256" -> {
                    requireInput(input, operation);
                    result.put("input_length", input.length());
                    result.put("result", hexDigest("SHA-256", input));
                }
                case "SHA512" -> {
                    requireInput(input, operation);
                    result.put("input_length", input.length());
                    result.put("result", hexDigest("SHA-512", input));
                }
                case "BASE64_ENCODE" -> {
                    requireInput(input, operation);
                    String encoded = Base64.getEncoder().encodeToString(input.getBytes(StandardCharsets.UTF_8));
                    result.put("result", encoded);
                    result.put("encoded_length", encoded.length());
                }
                case "BASE64_DECODE" -> {
                    requireInput(input, operation);
                    String decoded = new String(Base64.getDecoder().decode(input), StandardCharsets.UTF_8);
                    result.put("result", decoded);
                    result.put("decoded_length", decoded.length());
                }
                case "HEX_ENCODE" -> {
                    requireInput(input, operation);
                    StringBuilder hex = new StringBuilder();
                    for (byte b : input.getBytes(StandardCharsets.UTF_8)) {
                        hex.append(String.format("%02x", b));
                    }
                    result.put("result", hex.toString());
                }
                case "HEX_DECODE" -> {
                    requireInput(input, operation);
                    byte[] bytes = new byte[input.length() / 2];
                    for (int i = 0; i < bytes.length; i++) {
                        bytes[i] = (byte) Integer.parseInt(input.substring(i * 2, i * 2 + 2), 16);
                    }
                    result.put("result", new String(bytes, StandardCharsets.UTF_8));
                }
                case "UUID" -> {
                    int count = 1;
                    if (payload.containsKey("count")) {
                        count = Math.min(((Number) payload.get("count")).intValue(), 100);
                    }
                    if (count == 1) {
                        result.put("result", UUID.randomUUID().toString());
                    } else {
                        List<String> uuids = new ArrayList<>(count);
                        for (int i = 0; i < count; i++) uuids.add(UUID.randomUUID().toString());
                        result.put("result", uuids);
                        result.put("count", count);
                    }
                }
                case "CHECKSUM" -> {
                    requireInput(input, operation);
                    result.put("md5", hexDigest("MD5", input));
                    result.put("sha256", hexDigest("SHA-256", input));
                    result.put("input_length", input.length());
                    result.put("input_bytes", input.getBytes(StandardCharsets.UTF_8).length);
                }
                default -> {
                    return DGResponse.error(request.getRequestId(),
                            "Unknown operation: " + operation +
                            ". Supported: MD5, SHA1, SHA256, SHA512, BASE64_ENCODE, BASE64_DECODE, " +
                            "HEX_ENCODE, HEX_DECODE, UUID, CHECKSUM");
                }
            }

            return DGResponse.success(request.getRequestId(), result);
        } catch (IllegalArgumentException e) {
            return DGResponse.error(request.getRequestId(), e.getMessage());
        } catch (Exception e) {
            return DGResponse.error(request.getRequestId(), "Hash error: " + e.getMessage());
        }
    }

    private void requireInput(String input, String operation) {
        if (input == null || input.isEmpty()) {
            throw new IllegalArgumentException("'input' field is required for " + operation);
        }
    }

    private String hexDigest(String algorithm, String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance(algorithm);
        byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : digest) hex.append(String.format("%02x", b));
        return hex.toString();
    }

    @Override
    public void stop() { stopped = true; }

    @Override
    public void cleanup() {}
}
