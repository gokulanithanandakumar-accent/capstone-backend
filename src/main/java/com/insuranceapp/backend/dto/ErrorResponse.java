package com.insuranceapp.backend.dto;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(Instant timestamp, int status, String error, String message, String path,
        Map<String, String> fieldErrors) {

    public static ErrorResponse of(Instant timestamp, HttpStatus status, String message, String path) {
        return new ErrorResponse(timestamp, status.value(), status.getReasonPhrase(), message, path, Map.of());
    }
}
