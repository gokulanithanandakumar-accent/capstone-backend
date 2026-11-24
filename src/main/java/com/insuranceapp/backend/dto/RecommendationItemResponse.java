package com.insuranceapp.backend.dto;

import java.math.BigDecimal;

public record RecommendationItemResponse(
        Long recommendationItemId,
        int rankOrder,
        Long productId,
        String productCode,
        String policyName,
        String policyType,
        int termMonths,
        BigDecimal sumInsured,
        String premiumFrequency,
        BigDecimal premiumAmount,
        String recommendationReason,
        String suitability) {
}
