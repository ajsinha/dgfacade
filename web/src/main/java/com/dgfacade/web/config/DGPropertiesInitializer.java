/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential.
 */
package com.dgfacade.web.config;

import com.dgfacade.common.config.DGProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bridges Spring's {@link Environment} into the static {@link DGProperties}
 * singleton so that non-Spring POJOs can access application properties.
 *
 * <p>This component runs during the {@code @PostConstruct} phase — before any
 * HTTP request processing begins — ensuring the singleton is always available
 * when handler classes, model objects, or utility code need it.</p>
 *
 * <p>Only properties from enumerable sources (application.properties, YAML,
 * system properties, environment variables) are captured. Spring's internal
 * synthetic sources are skipped.</p>
 */
@Component
public class DGPropertiesInitializer {

    private static final Logger log = LoggerFactory.getLogger(DGPropertiesInitializer.class);

    private final Environment environment;

    public DGPropertiesInitializer(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void init() {
        Map<String, String> props = new LinkedHashMap<>();

        MutablePropertySources sources = ((AbstractEnvironment) environment).getPropertySources();
        for (org.springframework.core.env.PropertySource<?> source : sources) {
            if (source instanceof EnumerablePropertySource<?> enumerable) {
                for (String key : enumerable.getPropertyNames()) {
                    if (!props.containsKey(key)) {
                        try {
                            String resolved = environment.getProperty(key);
                            if (resolved != null) {
                                props.put(key, resolved);
                            }
                        } catch (Exception e) {
                            // Skip properties that fail to resolve (e.g. circular placeholders)
                        }
                    }
                }
            }
        }

        DGProperties.init(props);
        log.info("DGProperties initialized with {} properties", props.size());

        // Log key dgfacade.* properties for diagnostics
        if (log.isDebugEnabled()) {
            props.entrySet().stream()
                    .filter(e -> e.getKey().startsWith("dgfacade."))
                    .forEach(e -> log.debug("  {} = {}", e.getKey(), e.getValue()));
        }
    }
}
