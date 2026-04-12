package com.sureshkvn.subscriptions.common.exception;

/**
 * Thrown when a business rule or invariant is violated.
 * Maps to HTTP 409 Conflict or HTTP 422 Unprocessable Entity via {@link GlobalExceptionHandler}.
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
