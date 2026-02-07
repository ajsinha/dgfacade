/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, modification, or use is strictly prohibited without
 * explicit written permission from the copyright holder.
 * Patent Pending.
 */
package com.dgfacade.server.service;

import com.dgfacade.common.handler.DGHandler;
import com.dgfacade.common.handler.HandlerRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DefaultHandlerRegistry implements HandlerRegistry {

    private static final Logger log = LoggerFactory.getLogger(DefaultHandlerRegistry.class);
    private final Map<String, Class<? extends DGHandler>> handlerClasses = new ConcurrentHashMap<>();

    @Autowired
    private ApplicationContext applicationContext;

    @PostConstruct
    public void init() {
        // Auto-discover handlers from Spring context
        Map<String, DGHandler> beans = applicationContext.getBeansOfType(DGHandler.class);
        beans.values().forEach(handler -> {
            register(handler.getRequestType(), handler.getClass());
            log.info("Auto-registered handler: {} -> {}", handler.getRequestType(),
                     handler.getClass().getSimpleName());
        });
        log.info("HandlerRegistry initialized with {} handlers", handlerClasses.size());
    }

    @Override
    public void register(String requestType, Class<? extends DGHandler> handlerClass) {
        handlerClasses.put(requestType.toUpperCase(), handlerClass);
    }

    @Override
    public Optional<DGHandler> createHandler(String requestType) {
        Class<? extends DGHandler> clazz = handlerClasses.get(requestType.toUpperCase());
        if (clazz == null) return Optional.empty();
        try {
            return Optional.of(applicationContext.getBean(clazz));
        } catch (Exception e) {
            log.error("Failed to create handler instance for type: {}", requestType, e);
            return Optional.empty();
        }
    }

    @Override
    public Collection<String> getRegisteredTypes() {
        return Collections.unmodifiableCollection(handlerClasses.keySet());
    }

    @Override
    public boolean hasHandler(String requestType) {
        return handlerClasses.containsKey(requestType.toUpperCase());
    }
}
