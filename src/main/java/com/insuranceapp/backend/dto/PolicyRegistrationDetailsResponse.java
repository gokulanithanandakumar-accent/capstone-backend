package com.insuranceapp.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Pre-populated and calculated values shown on the policy registration form. */
public record PolicyRegistrationDetailsResponse(
        String customerName,
        LocalDate dateOfBirth,
        String email,
        String phone,
        Long productId,
        String productCode,
        String policyName,
        String policyType,
        int termMonths,
        LocalDate startDate,
        LocalDate maturityDate,
        String premiumFrequency,
        BigDecimal premiumAmount,
        BigDecimal sumInsured,
        BigDecimal totalPremiumPayable,
        int numberOfInstallments) {
}
