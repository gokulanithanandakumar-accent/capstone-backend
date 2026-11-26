package com.insuranceapp.backend.dto;

public record RegisterResponse(
        Long customerId,
        String fullName,
        String email,
        String message) {
}
