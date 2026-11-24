package com.insuranceapp.backend.exception;

/** A request that is well-formed but violates an insurance business rule. */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
