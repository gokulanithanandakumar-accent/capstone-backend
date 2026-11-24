package com.insuranceapp.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One row of the FR-5 search results table. */
public record PolicySearchResult(
        String policyNumber,
        String customerName,
        LocalDate dateOfBirth,
        String policyName,
        String policyType,
        LocalDate startDate,
        LocalDate maturityDate,
        BigDecimal totalPremiumPayable,
        String premiumPaymentStatus,
        BigDecimal totalPremiumPaid,
        BigDecimal remainingPremium) {
}