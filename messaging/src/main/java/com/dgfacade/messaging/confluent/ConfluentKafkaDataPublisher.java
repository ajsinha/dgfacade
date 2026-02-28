/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential.
 */
package com.dgfacade.messaging.confluent;

import com.dgfacade.messaging.kafka.KafkaDataPublisher;
import java.util.*;

/**
 * Confluent Kafka publisher — extends the standard Kafka publisher with
 * SASL authentication, Schema Registry, and Confluent Cloud-specific
 * properties injected automatically from the broker config.
 *
 * <p>Config JSON {@code "type": "confluent-kafka"} triggers this class.
 * All standard Kafka properties are inherited; Confluent-specific fields
 * are read from the {@code "authentication"} and {@code "schema_registry"}
 * blocks and converted to native Kafka client properties.</p>
 *
 * <h3>Authentication Block</h3>
 * <pre>
 *   "authentication": {
 *     "mechanism": "SASL_SSL",          // SASL_SSL | SASL_PLAINTEXT
 *     "sasl_mechanism": "PLAIN",        // PLAIN | OAUTHBEARER | SCRAM-SHA-256 | SCRAM-SHA-512
 *     "api_key": "...",                 // Confluent Cloud API key  (or username)
 *     "api_secret": "...",              // Confluent Cloud API secret (or password)
 *     "sasl_jaas_config": "..."         // Optional: raw JAAS line (overrides api_key/secret)
 *   }
 * </pre>
 *
 * <h3>Schema Registry Block (optional)</h3>
 * <pre>
 *   "schema_registry": {
 *     "url": "https://psrc-xxxxx.region.confluent.cloud",
 *     "api_key": "...",
 *     "api_secret": "..."
 *   }
 * </pre>
 */
public class ConfluentKafkaDataPublisher extends KafkaDataPublisher {

    @Override
    protected void doConnect() {
        injectConfluentProperties();
        super.doConnect();
        log.info("Confluent Kafka producer connected (SASL enabled)");
    }

    /**
     * Reads the "authentication" and "schema_registry" blocks from the broker
     * config and injects the corresponding native Kafka client properties into
     * the "properties" map so that the parent's doConnect() picks them up.
     */
    @SuppressWarnings("unchecked")
    private void injectConfluentProperties() {
        // Ensure the properties sub-map exists
        Map<String, String> props = (Map<String, String>) config.get("properties");
        if (props == null) {
            props = new LinkedHashMap<>();
            config.put("properties", props);
        }

        // ── Authentication ──────────────────────────────────────────────
        Object authObj = config.get("authentication");
        if (authObj instanceof Map<?, ?> auth) {
            String mechanism = str(auth, "mechanism", "SASL_SSL");
            String saslMechanism = str(auth, "sasl_mechanism", "PLAIN");

            props.putIfAbsent("security.protocol", mechanism);
            props.putIfAbsent("sasl.mechanism", saslMechanism);

            // Build JAAS config from api_key/api_secret if not explicitly set
            if (!props.containsKey("sasl.jaas.config")) {
                String jaas = (String) auth.get("sasl_jaas_config");
                if (jaas == null || jaas.isBlank()) {
                    String apiKey = str(auth, "api_key", "");
                    String apiSecret = str(auth, "api_secret", "");
                    if (!apiKey.isEmpty()) {
                        jaas = buildJaasConfig(saslMechanism, apiKey, apiSecret);
                    }
                }
                if (jaas != null && !jaas.isBlank()) {
                    props.put("sasl.jaas.config", jaas);
                }
            }

            // Confluent Cloud defaults
            props.putIfAbsent("client.dns.lookup", "use_all_dns_ips");
            props.putIfAbsent("ssl.endpoint.identification.algorithm", "https");

            log.debug("Confluent auth injected: protocol={}, sasl.mechanism={}",
                    mechanism, saslMechanism);
        }

        // ── Schema Registry (informational — stored for downstream use) ─
        Object srObj = config.get("schema_registry");
        if (srObj instanceof Map<?, ?> sr) {
            String srUrl = (String) sr.get("url");
            if (srUrl != null) {
                props.putIfAbsent("schema.registry.url", srUrl);
            }
            String srKey = (String) sr.get("api_key");
            String srSecret = (String) sr.get("api_secret");
            if (srKey != null) {
                props.putIfAbsent("basic.auth.credentials.source", "USER_INFO");
                props.putIfAbsent("basic.auth.user.info", srKey + ":" + Objects.toString(srSecret, ""));
            }
            log.debug("Schema Registry configured: url={}", srUrl);
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    /**
     * Builds a JAAS config string for the given SASL mechanism.
     */
    static String buildJaasConfig(String saslMechanism, String username, String password) {
        return switch (saslMechanism.toUpperCase()) {
            case "PLAIN" ->
                "org.apache.kafka.common.security.plain.PlainLoginModule required " +
                "username=\"" + username + "\" password=\"" + password + "\";";
            case "SCRAM-SHA-256", "SCRAM-SHA-512" ->
                "org.apache.kafka.common.security.scram.ScramLoginModule required " +
                "username=\"" + username + "\" password=\"" + password + "\";";
            case "OAUTHBEARER" ->
                "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required " +
                "oauth.client.id=\"" + username + "\" oauth.client.secret=\"" + password + "\";";
            default ->
                "org.apache.kafka.common.security.plain.PlainLoginModule required " +
                "username=\"" + username + "\" password=\"" + password + "\";";
        };
    }

    private static String str(Map<?, ?> map, String key, String defaultValue) {
        Object v = map.get(key);
        return v != null ? v.toString() : defaultValue;
    }
}
