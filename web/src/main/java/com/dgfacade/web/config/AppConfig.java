/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential.
 */
package com.dgfacade.web.config;

import com.dgfacade.server.python.DGHandlerPython;
import com.dgfacade.server.python.PythonConfig;
import com.dgfacade.server.python.PythonWorkerManager;
import com.dgfacade.common.util.ConfigPropertyResolver;
import com.dgfacade.server.channel.ChannelAccessor;
import com.dgfacade.server.cluster.ClusterService;
import com.dgfacade.server.config.ConfigAutoReloadService;
import com.dgfacade.server.config.ExternalJarLoader;
import com.dgfacade.server.config.HandlerConfigRegistry;
import com.dgfacade.server.engine.ExecutionEngine;
import com.dgfacade.server.ingestion.IngestionService;
import com.dgfacade.server.metrics.MetricsService;
import com.dgfacade.server.service.BrokerService;
import com.dgfacade.server.service.InputChannelService;
import com.dgfacade.server.service.OutputChannelService;
import com.dgfacade.server.service.UserService;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.InetAddress;

@Configuration
public class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

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

    @Value("${dgfacade.ingesters.config-dir:config/ingesters}")
    private String ingestersConfigDir;

    @Value("${dgfacade.python.config-dir:config/python}")
    private String pythonConfigDir;

    // --- Cluster Configuration ---
    @Value("${dgfacade.cluster.seed-nodes:}")
    private String clusterSeedNodes;

    @Value("${dgfacade.cluster.node-role:BOTH}")
    private String clusterNodeRole;

    @Value("${dgfacade.cluster.heartbeat-seconds:15}")
    private int clusterHeartbeatSeconds;

    @Value("${server.port:8090}")
    private int serverPort;

    @Value("${dgfacade.version:1.6.1}")
    private String version;

    @Value("${dgfacade.config.auto-reload-seconds:300}")
    private int autoReloadSeconds;

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
    public ChannelAccessor channelAccessor(BrokerService brokerService,
                                           InputChannelService inputChannelService,
                                           OutputChannelService outputChannelService) {
        return new ChannelAccessor(brokerService, inputChannelService, outputChannelService);
    }

    @Bean
    public ClusterService clusterService() {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            host = "localhost";
        }
        return new ClusterService(host, serverPort, version,
                clusterNodeRole, clusterSeedNodes, clusterHeartbeatSeconds);
    }

    @Bean
    public ExecutionEngine executionEngine(HandlerConfigRegistry registry,
                                           UserService userService,
                                           MetricsService metricsService,
                                           ChannelAccessor channelAccessor,
                                           ClusterService clusterService) {
        ExecutionEngine engine = new ExecutionEngine(registry, userService);
        engine.setMetricsService(metricsService);
        engine.setChannelAccessor(channelAccessor);
        engine.setClusterService(clusterService);
        return engine;
    }

    @Bean
    public IngestionService ingestionService(ExecutionEngine executionEngine,
                                             BrokerService brokerService,
                                             InputChannelService inputChannelService) {
        return new IngestionService(ingestersConfigDir, executionEngine, brokerService, inputChannelService);
    }

    @Bean
    public PythonWorkerManager pythonWorkerManager() {
        PythonConfig pyConfig = PythonConfig.load(pythonConfigDir);
        PythonWorkerManager manager = new PythonWorkerManager(pyConfig, pythonConfigDir);
        // Inject the manager into the static bridge handler
        DGHandlerPython.setWorkerManager(manager);
        return manager;
    }

    @Bean
    public ConfigAutoReloadService configAutoReloadService(
            HandlerConfigRegistry handlerConfigRegistry) {
        ConfigAutoReloadService svc = new ConfigAutoReloadService(autoReloadSeconds);
        svc.register("handlers", handlersDir, handlerConfigRegistry::reload);
        // Brokers, channels, and ingesters read from disk on demand — no caching.
        // Register their directories anyway so the fingerprint log shows change detection.
        svc.register("brokers", brokersConfigDir, () ->
                log.info("ConfigAutoReload: broker configs are read-on-demand; no cache to refresh"));
        svc.register("input-channels", inputChannelsConfigDir, () ->
                log.info("ConfigAutoReload: input-channel configs are read-on-demand; no cache to refresh"));
        svc.register("output-channels", outputChannelsConfigDir, () ->
                log.info("ConfigAutoReload: output-channel configs are read-on-demand; no cache to refresh"));
        svc.register("ingesters", ingestersConfigDir, () ->
                log.info("ConfigAutoReload: ingester configs are read-on-demand; no cache to refresh"));
        svc.start();
        return svc;
    }
}
