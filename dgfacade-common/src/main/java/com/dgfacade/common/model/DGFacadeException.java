/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, modification, or use is strictly prohibited without
 * explicit written permission from the copyright holder.
 * Patent Pending.
 */
package com.dgfacade.common.model;

public class DGFacadeException extends RuntimeException {
    private final ResponseStatus status;

    public DGFacadeException(String message) {
        super(message);
        this.status = ResponseStatus.ERROR;
    }

    public DGFacadeException(String message, ResponseStatus status) {
        super(message);
        this.status = status;
    }

    public DGFacadeException(String message, Throwable cause) {
        super(message, cause);
        this.status = ResponseStatus.ERROR;
    }

    public ResponseStatus getStatus() { return status; }
}
