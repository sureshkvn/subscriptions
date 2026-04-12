package com.sureshkvn.subscriptions.common.exception;

/**
 * Thrown when a requested resource cannot be found in the data store.
 * Maps to HTTP 404 Not Found via {@link GlobalExceptionHandler}.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue) {
        super(String.format("%s not found with %s: '%s'", resourceName, fieldName, fieldValue));
    }
}
