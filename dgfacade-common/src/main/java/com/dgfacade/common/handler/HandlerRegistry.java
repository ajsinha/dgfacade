/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, modification, or use is strictly prohibited without
 * explicit written permission from the copyright holder.
 * Patent Pending.
 */
package com.dgfacade.common.handler;

import java.util.Collection;
import java.util.Optional;

/**
 * Registry for discovering and creating handler instances.
 */
public interface HandlerRegistry {

    /** Register a handler class for a given request type. */
    void register(String requestType, Class<? extends DGHandler> handlerClass);

    /** Create a new handler instance for the given request type. */
    Optional<DGHandler> createHandler(String requestType);

    /** Get all registered request types. */
    Collection<String> getRegisteredTypes();

    /** Check if a handler exists for the given request type. */
    boolean hasHandler(String requestType);
}
