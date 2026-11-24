package com.insuranceapp.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One row of the customer dashboard policy table (FR-1B). */
public record CustomerPolicyResponse(
        String policyNumber,
        String policyName,
        String policyType,
        int termMonths,
        LocalDate startDate,
        LocalDate maturityDate,
        String premiumFrequency,
        BigDecimal premiumAmount,
        BigDecimal sumInsured,
        String status) {
}
