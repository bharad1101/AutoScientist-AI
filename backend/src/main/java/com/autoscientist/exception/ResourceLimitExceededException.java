package com.autoscientist.exception;

/**
 * Exception thrown when a requested operation exceeds configured resource budgets.
 */
public class ResourceLimitExceededException extends RuntimeException {
    public ResourceLimitExceededException(String message) {
        super(message);
    }
}