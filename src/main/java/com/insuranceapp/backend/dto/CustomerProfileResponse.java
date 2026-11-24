package com.insuranceapp.backend.dto;

import java.time.LocalDate;

public record CustomerProfileResponse(Long customerId, String fullName, String email, LocalDate dateOfBirth,
        String phone) {
}
