/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential.
 */
package com.dgfacade.common.exception;

public class HandlerNotFoundException extends DGFacadeException {
    public HandlerNotFoundException(String requestType, String userId) {
        super("DGF_HANDLER_NOT_FOUND",
              "No handler found for request_type='" + requestType + "' user='" + userId + "'");
    }
}
