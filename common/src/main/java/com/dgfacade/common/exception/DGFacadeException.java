/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential.
 */
package com.dgfacade.common.exception;

/**
 * Base exception for all DGFacade errors.
 */
public class DGFacadeException extends RuntimeException {
    private final String errorCode;

    public DGFacadeException(String message) {
        super(message);
        this.errorCode = "DGF_GENERIC";
    }

    public DGFacadeException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public DGFacadeException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() { return errorCode; }
}
