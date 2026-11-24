package com.insuranceapp.backend.dto;

import java.time.Instant;

public record LoginResponse(String accessToken, String tokenType, Instant expiresAt, Long customerId,
        String fullName, String email) {
}
