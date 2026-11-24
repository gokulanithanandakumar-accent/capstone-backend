package com.insuranceapp.backend.dto;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** FR-5 search criteria bound from query parameters. Allowed combinations are enforced by the service. */
public record PolicySearchRequest(
        @Size(max = 30, message = "Policy identifier must not exceed 30 characters")
        @Pattern(regexp = "^\\s*[A-Za-z0-9-]*\\s*$",
                message = "Policy identifier may contain only letters, digits and hyphens")
        String policyNumber,
        @Size(max = 100, message = "Customer name must not exceed 100 characters")
        @Pattern(regexp = "^[\\p{L} .'-]*$",
                message = "Customer name may contain only letters, spaces, apostrophes, periods and hyphens")
        String customerName,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @PastOrPresent(message = "Date of birth cannot be in the future")
        LocalDate dateOfBirth,
        @Min(value = 0, message = "Page number cannot be negative") Integer page,
        @Min(value = 1, message = "Page size must be at least 1")
        @Max(value = 50, message = "Page size cannot exceed 50") Integer size) {
}