/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential.
 */
package com.dgfacade.messaging.confluent;

import com.dgfacade.messaging.kafka.KafkaDataSubscriber;
import java.util.*;

/**
 * Confluent Kafka subscriber — extends the standard Kafka subscriber with
 * SASL authentication, Schema Registry, and Confluent Cloud-specific
 * properties injected automatically from the broker config.
 *
 * <p>Mirrors the authentication and schema-registry injection logic from
 * {@link ConfluentKafkaDataPublisher}. See that class for full config
 * block documentation.</p>
 *
 * <p>All standard Kafka consumer properties (group_id, auto_offset_reset,
 * max_poll_records, backpressure, reconnection) are inherited from the
 * parent {@link KafkaDataSubscriber}.</p>
 */
public class ConfluentKafkaDataSubscriber extends KafkaDataSubscriber {

    @Override
    protected void doConnect() {
        injectConfluentProperties();
        super.doConnect();
        log.info("Confluent Kafka consumer connected (SASL enabled)");
    }

    /**
     * Reads the "authentication" and "schema_registry" blocks from the broker
     * config and injects the corresponding native Kafka client properties into
     * the "properties" map so that the parent's doConnect() picks them up.
     */
    @SuppressWarnings("unchecked")
    private void injectConfluentProperties() {
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

            if (!props.containsKey("sasl.jaas.config")) {
                String jaas = (String) auth.get("sasl_jaas_config");
                if (jaas == null || jaas.isBlank()) {
                    String apiKey = str(auth, "api_key", "");
                    String apiSecret = str(auth, "api_secret", "");
                    if (!apiKey.isEmpty()) {
                        jaas = ConfluentKafkaDataPublisher.buildJaasConfig(
                                saslMechanism, apiKey, apiSecret);
                    }
                }
                if (jaas != null && !jaas.isBlank()) {
                    props.put("sasl.jaas.config", jaas);
                }
            }

            props.putIfAbsent("client.dns.lookup", "use_all_dns_ips");
            props.putIfAbsent("ssl.endpoint.identification.algorithm", "https");

            log.debug("Confluent auth injected: protocol={}, sasl.mechanism={}",
                    mechanism, saslMechanism);
        }

        // ── Schema Registry ─────────────────────────────────────────────
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
        }
    }

    private static String str(Map<?, ?> map, String key, String defaultValue) {
        Object v = map.get(key);
        return v != null ? v.toString() : defaultValue;
    }
}
