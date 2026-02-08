/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential. Patent Pending.
 */
package com.dgfacade.web.config;

import com.dgfacade.server.config.ExternalJarLoader;
import com.dgfacade.server.config.HandlerConfigRegistry;
import com.dgfacade.server.engine.ExecutionEngine;
import com.dgfacade.server.metrics.MetricsService;
import com.dgfacade.server.service.BrokerService;
import com.dgfacade.server.service.ChannelService;
import com.dgfacade.server.service.UserService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

@Configuration
public class AppConfig {

    @Value("${dgfacade.config.handlers-dir:config/handlers}")
    private String handlersDir;

    @Value("${dgfacade.config.users-file:config/users.json}")
    private String usersFile;

    @Value("${dgfacade.config.apikeys-file:config/apikeys.json}")
    private String apiKeysFile;

    @Value("${dgfacade.config.external-libs-dir:./libs}")
    private String externalLibsDir;

    @Value("${dgfacade.brokers.config-dir:config/brokers}")
    private String brokersConfigDir;

    @Value("${dgfacade.channels.config-dir:config/channels}")
    private String channelsConfigDir;

    @Bean
    public ExternalJarLoader externalJarLoader() {
        ExternalJarLoader loader = new ExternalJarLoader();
        loader.loadJars(externalLibsDir);
        return loader;
    }

    @Bean
    public UserService userService() {
        return new UserService(usersFile, apiKeysFile);
    }

    @Bean
    public HandlerConfigRegistry handlerConfigRegistry() {
        return new HandlerConfigRegistry(handlersDir);
    }

    @Bean
    public MetricsService metricsService(MeterRegistry meterRegistry) {
        return new MetricsService(meterRegistry);
    }

    @Bean
    public BrokerService brokerService() {
        return new BrokerService(brokersConfigDir);
    }

    @Bean
    public ChannelService channelService() {
        return new ChannelService(channelsConfigDir);
    }

    @Bean
    public ExecutionEngine executionEngine(HandlerConfigRegistry registry,
                                           UserService userService,
                                           MetricsService metricsService) {
        ExecutionEngine engine = new ExecutionEngine(registry, userService);
        engine.setMetricsService(metricsService);
        return engine;
    }
}
