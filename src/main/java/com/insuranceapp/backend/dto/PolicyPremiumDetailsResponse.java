package com.insuranceapp.backend.dto;

import java.util.List;

public record PolicyPremiumDetailsResponse(
        PremiumPolicySummaryResponse policy,
        List<PremiumInstallmentResponse> installments,
        List<PremiumPaymentHistoryResponse> payments) {
}