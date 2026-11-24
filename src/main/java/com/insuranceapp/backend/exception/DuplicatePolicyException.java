package com.insuranceapp.backend.exception;

public class DuplicatePolicyException extends RuntimeException {
    public DuplicatePolicyException(String message) {
        super(message);
    }
}
