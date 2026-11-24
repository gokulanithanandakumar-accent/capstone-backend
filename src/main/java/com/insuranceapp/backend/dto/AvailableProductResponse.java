package com.insuranceapp.backend.dto;

import java.math.BigDecimal;

/** One row of the available insurance products table (FR-1C). */
public record AvailableProductResponse(
        Long productId,
        String productCode,
        String policyName,
        String policyType,
        int termMonths,
        BigDecimal sumInsured,
        String premiumFrequency,
        BigDecimal premiumAmount) {
}
