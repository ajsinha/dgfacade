/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential. Patent Pending.
 */
package com.dgfacade.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads external JARs from a configured directory (default: ./libs) into the classpath.
 * Enables dynamic handler class loading from external plugins.
 */
public class ExternalJarLoader {

    private static final Logger log = LoggerFactory.getLogger(ExternalJarLoader.class);

    private URLClassLoader classLoader;

    public void loadJars(String libDir) {
        File dir = new File(libDir);
        if (!dir.exists() || !dir.isDirectory()) {
            log.info("External libs directory does not exist: {}", libDir);
            dir.mkdirs();
            return;
        }
        File[] jars = dir.listFiles((d, name) -> name.endsWith(".jar"));
        if (jars == null || jars.length == 0) {
            log.info("No external JARs found in {}", libDir);
            return;
        }
        List<URL> urls = new ArrayList<>();
        for (File jar : jars) {
            try {
                urls.add(jar.toURI().toURL());
                log.info("Loaded external JAR: {}", jar.getName());
            } catch (Exception e) {
                log.error("Failed to load JAR: {}", jar.getName(), e);
            }
        }
        classLoader = new URLClassLoader(urls.toArray(new URL[0]), getClass().getClassLoader());
        Thread.currentThread().setContextClassLoader(classLoader);
        log.info("Loaded {} external JARs from {}", urls.size(), libDir);
    }

    public ClassLoader getClassLoader() {
        return classLoader != null ? classLoader : getClass().getClassLoader();
    }
}
