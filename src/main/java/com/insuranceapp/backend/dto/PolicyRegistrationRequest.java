package com.insuranceapp.backend.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

/**
 * Only the product, start date and (optionally) the AI recommendation the product was chosen from are accepted;
 * customer details and all financial values are taken from the database.
 */
public record PolicyRegistrationRequest(
        @NotNull(message = "Insurance product is required") Long productId,
        @NotNull(message = "Policy start date is required") LocalDate startDate,
        Long recommendationItemId) {
}
