package com.insuranceapp.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

/** FR-4B schedule row; amountDue includes the penalty that applies if paid today. */
public record PremiumInstallmentResponse(
        Long installmentId,
        int installmentNumber,
        YearMonth premiumPeriod,
        BigDecimal scheduledAmount,
        LocalDate dueDate,
        LocalDate gracePeriodEndDate,
        BigDecimal paidAmount,
        BigDecimal penaltyPaid,
        BigDecimal outstandingPremium,
        BigDecimal applicablePenalty,
        BigDecimal amountDue,
        String paymentStatus,
        boolean selectable) {
}