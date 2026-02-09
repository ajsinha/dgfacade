/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential. Patent Pending.
 */
package com.dgfacade.web.config;

import com.dgfacade.common.util.ConfigPropertyResolver;
import com.dgfacade.server.config.ExternalJarLoader;
import com.dgfacade.server.config.HandlerConfigRegistry;
import com.dgfacade.server.engine.ExecutionEngine;
import com.dgfacade.server.metrics.MetricsService;
import com.dgfacade.server.service.BrokerService;
import com.dgfacade.server.service.InputChannelService;
import com.dgfacade.server.service.OutputChannelService;
import com.dgfacade.server.service.UserService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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

    @Value("${dgfacade.input-channels.config-dir:config/input-channels}")
    private String inputChannelsConfigDir;

    @Value("${dgfacade.output-channels.config-dir:config/output-channels}")
    private String outputChannelsConfigDir;

    @Bean
    public ConfigPropertyResolver configPropertyResolver() {
        ConfigPropertyResolver resolver = new ConfigPropertyResolver();
        resolver.loadClasspathProperties("application.properties");
        return resolver;
    }

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
    public BrokerService brokerService(ConfigPropertyResolver resolver) {
        BrokerService svc = new BrokerService(brokersConfigDir);
        svc.setPropertyResolver(resolver);
        return svc;
    }

    @Bean
    public InputChannelService inputChannelService(ConfigPropertyResolver resolver) {
        InputChannelService svc = new InputChannelService(inputChannelsConfigDir);
        svc.setPropertyResolver(resolver);
        return svc;
    }

    @Bean
    public OutputChannelService outputChannelService(ConfigPropertyResolver resolver) {
        OutputChannelService svc = new OutputChannelService(outputChannelsConfigDir);
        svc.setPropertyResolver(resolver);
        return svc;
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
