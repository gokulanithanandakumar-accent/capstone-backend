package com.insuranceapp.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** FR-4A policy details; premium totals exclude late payment penalties, which are reported separately. */
public record PremiumPolicySummaryResponse(
        String policyNumber,
        String customerName,
        String policyName,
        int termMonths,
        LocalDate startDate,
        LocalDate maturityDate,
        String premiumFrequency,
        String policyStatus,
        BigDecimal totalPremiumPayable,
        BigDecimal totalPremiumPaid,
        BigDecimal totalRemainingPremium,
        BigDecimal totalPenaltyPaid) {
}