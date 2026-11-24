package com.insuranceapp.backend.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

/** FR-2 inputs; the customer may provide one or more of them. */
public record RecommendationRequest(
        @PastOrPresent(message = "Date of birth cannot be in the future") LocalDate dateOfBirth,
        @Size(max = 500, message = "Insurance goals must be at most 500 characters") String insuranceGoals,
        @Size(max = 50, message = "Preferred policy term must be at most 50 characters") String preferredTerm,
        @Size(max = 50, message = "Insurance type must be at most 50 characters") String preferredPolicyType) {

    @AssertTrue(message = "Provide at least one input: date of birth, insurance goals, preferred policy term or insurance type")
    public boolean isAnyInputProvided() {
        return dateOfBirth != null || hasText(insuranceGoals) || hasText(preferredTerm)
                || hasText(preferredPolicyType);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
