/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, modification, or use is strictly prohibited without
 * explicit written permission from the copyright holder.
 * Patent Pending.
 */
package com.dgfacade.common.security;

import com.dgfacade.common.model.ApiKey;
import java.util.List;
import java.util.Optional;

/**
 * Service interface for API key management and validation.
 */
public interface ApiKeyService {

    /** Validate an API key and check if it's allowed for the given request type. */
    boolean validate(String apiKey, String requestType);

    /** Get API key details by key string. */
    Optional<ApiKey> getApiKey(String key);

    /** Get all configured API keys. */
    List<ApiKey> getAllApiKeys();

    /** Reload API keys from the configuration source. */
    void reload();
}
