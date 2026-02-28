/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential.
 */
package com.dgfacade.messaging.config;

import com.dgfacade.common.model.BrokerConfig;
import com.dgfacade.messaging.core.*;
import com.dgfacade.messaging.kafka.*;
import com.dgfacade.messaging.confluent.*;
import com.dgfacade.messaging.activemq.*;
import com.dgfacade.messaging.rabbitmq.*;
import com.dgfacade.messaging.ibmmq.*;
import com.dgfacade.messaging.filesystem.*;
import com.dgfacade.messaging.sql.*;

/**
 * Factory to create DataPublisher and DataSubscriber instances based on BrokerConfig.
 * Supports: Kafka, Confluent Kafka, ActiveMQ, RabbitMQ, IBM MQ, FileSystem, SQL.
 */
public final class MessagingFactory {

    private MessagingFactory() {}

    public static DataPublisher createPublisher(BrokerConfig brokerConfig) {
        return switch (brokerConfig.getBrokerType()) {
            case KAFKA -> new KafkaDataPublisher();
            case CONFLUENT_KAFKA -> new ConfluentKafkaDataPublisher();
            case ACTIVEMQ -> new ActiveMQDataPublisher();
            case RABBITMQ -> new RabbitMQDataPublisher();
            case IBMMQ -> new IbmMQDataPublisher();
            case FILESYSTEM -> new FileSystemDataPublisher();
            case SQL -> new SqlDataPublisher();
        };
    }

    public static DataSubscriber createSubscriber(BrokerConfig brokerConfig) {
        return switch (brokerConfig.getBrokerType()) {
            case KAFKA -> new KafkaDataSubscriber();
            case CONFLUENT_KAFKA -> new ConfluentKafkaDataSubscriber();
            case ACTIVEMQ -> new ActiveMQDataSubscriber();
            case RABBITMQ -> new RabbitMQDataSubscriber();
            case IBMMQ -> new IbmMQDataSubscriber();
            case FILESYSTEM -> new FileSystemDataSubscriber();
            case SQL -> new SqlDataSubscriber();
        };
    }
}
