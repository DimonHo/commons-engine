package com.commonsengine.platform.exception;

/**
 * Thrown when a requested resource cannot be found.
 */
public class NotFoundException extends BusinessRuleException {

    public NotFoundException(String code, String message) {
        super(code, message);
    }

    public NotFoundException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
