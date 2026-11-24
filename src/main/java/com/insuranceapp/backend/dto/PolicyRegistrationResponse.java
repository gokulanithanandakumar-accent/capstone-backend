package com.insuranceapp.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PolicyRegistrationResponse(
        String policyNumber,
        String policyName,
        LocalDate startDate,
        LocalDate maturityDate,
        BigDecimal totalPremiumPayable,
        int numberOfInstallments,
        String status,
        String message) {
}
