/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, modification, or use is strictly prohibited without
 * explicit written permission from the copyright holder.
 * Patent Pending.
 */
package com.dgfacade.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "dgfacade")
public class DGFacadeProperties {

    private String appName = "DGFacade";
    private String version = "1.1.0";
    private String configPath = "./config";
    private String externalLibsPath = "./libs";

    private Kafka kafka = new Kafka();
    private Activemq activemq = new Activemq();
    private Actor actor = new Actor();
    private Security security = new Security();
    private Streaming streaming = new Streaming();

    public static class Kafka {
        private boolean enabled = false;
        private String bootstrapServers = "localhost:9092";
        private String requestTopic = "dgfacade-requests";
        private String responseTopic = "dgfacade-responses";
        private String groupId = "dgfacade-group";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getBootstrapServers() { return bootstrapServers; }
        public void setBootstrapServers(String bootstrapServers) { this.bootstrapServers = bootstrapServers; }
        public String getRequestTopic() { return requestTopic; }
        public void setRequestTopic(String requestTopic) { this.requestTopic = requestTopic; }
        public String getResponseTopic() { return responseTopic; }
        public void setResponseTopic(String responseTopic) { this.responseTopic = responseTopic; }
        public String getGroupId() { return groupId; }
        public void setGroupId(String groupId) { this.groupId = groupId; }
    }

    public static class Activemq {
        private boolean enabled = false;
        private String brokerUrl = "tcp://localhost:61616";
        private String requestQueue = "dgfacade.requests";
        private String responseQueue = "dgfacade.responses";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getBrokerUrl() { return brokerUrl; }
        public void setBrokerUrl(String brokerUrl) { this.brokerUrl = brokerUrl; }
        public String getRequestQueue() { return requestQueue; }
        public void setRequestQueue(String requestQueue) { this.requestQueue = requestQueue; }
        public String getResponseQueue() { return responseQueue; }
        public void setResponseQueue(String responseQueue) { this.responseQueue = responseQueue; }
    }

    public static class Actor {
        private int minPoolSize = 5;
        private int maxPoolSize = 100;
        private int mailboxCapacity = 1000;
        private long handlerTimeoutSeconds = 60;

        public int getMinPoolSize() { return minPoolSize; }
        public void setMinPoolSize(int minPoolSize) { this.minPoolSize = minPoolSize; }
        public int getMaxPoolSize() { return maxPoolSize; }
        public void setMaxPoolSize(int maxPoolSize) { this.maxPoolSize = maxPoolSize; }
        public int getMailboxCapacity() { return mailboxCapacity; }
        public void setMailboxCapacity(int mailboxCapacity) { this.mailboxCapacity = mailboxCapacity; }
        public long getHandlerTimeoutSeconds() { return handlerTimeoutSeconds; }
        public void setHandlerTimeoutSeconds(long handlerTimeoutSeconds) { this.handlerTimeoutSeconds = handlerTimeoutSeconds; }
    }

    public static class Security {
        private String usersFile = "config/users.json";
        private String apiKeysFile = "config/apikeys.json";

        public String getUsersFile() { return usersFile; }
        public void setUsersFile(String usersFile) { this.usersFile = usersFile; }
        public String getApiKeysFile() { return apiKeysFile; }
        public void setApiKeysFile(String apiKeysFile) { this.apiKeysFile = apiKeysFile; }
    }

    public static class Streaming {
        private boolean enabled = true;
        private long defaultTtlMinutes = 30;
        private long maxTtlMinutes = 480;
        private int maxConcurrentSessions = 50;
        /** Comma-separated list of default channels: WEBSOCKET,KAFKA,ACTIVEMQ */
        private String defaultResponseChannels = "WEBSOCKET";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public long getDefaultTtlMinutes() { return defaultTtlMinutes; }
        public void setDefaultTtlMinutes(long defaultTtlMinutes) { this.defaultTtlMinutes = defaultTtlMinutes; }
        public long getMaxTtlMinutes() { return maxTtlMinutes; }
        public void setMaxTtlMinutes(long maxTtlMinutes) { this.maxTtlMinutes = maxTtlMinutes; }
        public int getMaxConcurrentSessions() { return maxConcurrentSessions; }
        public void setMaxConcurrentSessions(int maxConcurrentSessions) { this.maxConcurrentSessions = maxConcurrentSessions; }
        public String getDefaultResponseChannels() { return defaultResponseChannels; }
        public void setDefaultResponseChannels(String defaultResponseChannels) { this.defaultResponseChannels = defaultResponseChannels; }
    }

    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getConfigPath() { return configPath; }
    public void setConfigPath(String configPath) { this.configPath = configPath; }
    public String getExternalLibsPath() { return externalLibsPath; }
    public void setExternalLibsPath(String externalLibsPath) { this.externalLibsPath = externalLibsPath; }
    public Kafka getKafka() { return kafka; }
    public void setKafka(Kafka kafka) { this.kafka = kafka; }
    public Activemq getActivemq() { return activemq; }
    public void setActivemq(Activemq activemq) { this.activemq = activemq; }
    public Actor getActor() { return actor; }
    public void setActor(Actor actor) { this.actor = actor; }
    public Security getSecurity() { return security; }
    public void setSecurity(Security security) { this.security = security; }
    public Streaming getStreaming() { return streaming; }
    public void setStreaming(Streaming streaming) { this.streaming = streaming; }
}
