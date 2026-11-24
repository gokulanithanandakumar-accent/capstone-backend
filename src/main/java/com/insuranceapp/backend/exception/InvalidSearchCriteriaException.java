package com.insuranceapp.backend.exception;

/** Search criteria that do not form one of the supported FR-5 search combinations (HTTP 400). */
public class InvalidSearchCriteriaException extends RuntimeException {
    public InvalidSearchCriteriaException(String message) {
        super(message);
    }
}