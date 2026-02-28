/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential.
 */
package com.dgfacade.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.dgfacade")
@EnableScheduling
public class DGFacadeApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(DGFacadeApplication.class);
        app.setRegisterShutdownHook(true); // Ensure Spring context fires ContextClosedEvent on JVM shutdown
        app.run(args);
    }
}
