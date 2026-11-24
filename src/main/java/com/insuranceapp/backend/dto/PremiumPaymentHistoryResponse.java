package com.insuranceapp.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PremiumPaymentHistoryResponse(
        String transactionReference,
        int installmentNumber,
        LocalDate paymentDate,
        BigDecimal amountPaid,
        BigDecimal penaltyAmount,
        String paymentMethod) {
}