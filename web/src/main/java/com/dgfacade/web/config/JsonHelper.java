/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential.
 */
package com.dgfacade.web.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Helper bean for JSON serialization in Thymeleaf templates.
 * Usage in templates: ${@jsonHelper.toJson(object)}
 */
@Component("jsonHelper")
public class JsonHelper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public String toJson(Object obj) {
        if (obj == null) return "{}";
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
