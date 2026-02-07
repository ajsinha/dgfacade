/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, modification, or use is strictly prohibited without
 * explicit written permission from the copyright holder.
 * Patent Pending.
 */
package com.dgfacade.web.config;

import com.dgfacade.common.config.DGFacadeProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;

@Component
public class ExternalLibConfig {

    private static final Logger log = LoggerFactory.getLogger(ExternalLibConfig.class);
    private final DGFacadeProperties properties;

    public ExternalLibConfig(DGFacadeProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void loadExternalLibraries() {
        String libsPath = properties.getExternalLibsPath();
        File libsDir = new File(libsPath);

        if (!libsDir.exists() || !libsDir.isDirectory()) {
            log.info("External libs directory does not exist: {}. Skipping.", libsPath);
            return;
        }

        File[] jarFiles = libsDir.listFiles((dir, name) -> name.endsWith(".jar"));
        if (jarFiles == null || jarFiles.length == 0) {
            log.info("No JAR files found in: {}", libsPath);
            return;
        }

        try {
            URL[] urls = new URL[jarFiles.length];
            for (int i = 0; i < jarFiles.length; i++) {
                urls[i] = jarFiles[i].toURI().toURL();
                log.info("Loading external library: {}", jarFiles[i].getName());
            }

            URLClassLoader classLoader = new URLClassLoader(urls,
                    Thread.currentThread().getContextClassLoader());
            Thread.currentThread().setContextClassLoader(classLoader);
            log.info("Loaded {} external libraries from {}", jarFiles.length, libsPath);
        } catch (Exception e) {
            log.error("Failed to load external libraries from {}", libsPath, e);
        }
    }
}
