/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential.
 */
package com.dgfacade.common.exception;

public class TTLExceededException extends DGFacadeException {
    public TTLExceededException(String handlerId, int ttlMinutes) {
        super("DGF_TTL_EXCEEDED",
              "Handler '" + handlerId + "' exceeded TTL of " + ttlMinutes + " minutes");
    }
}
