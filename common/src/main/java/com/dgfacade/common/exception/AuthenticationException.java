/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential. Patent Pending.
 */
package com.dgfacade.common.exception;

public class AuthenticationException extends DGFacadeException {
    public AuthenticationException(String message) {
        super("DGF_AUTH_FAILED", message);
    }
}
