package com.insuranceapp.backend.ai;

import java.math.BigDecimal;

/** Product details shared with the AI model. Only products retrieved from the database are sent. */
public record ProductCandidate(
        String productCode,
        String policyName,
        String policyType,
        int termMonths,
        BigDecimal sumInsured,
        String premiumFrequency,
        BigDecimal premiumAmount) {
}
