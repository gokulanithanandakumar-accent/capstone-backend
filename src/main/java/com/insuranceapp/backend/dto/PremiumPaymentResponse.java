package com.insuranceapp.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PremiumPaymentResponse(
        String policyNumber,
        LocalDate paymentDate,
        List<PaymentReceipt> receipts,
        BigDecimal totalAmountPaid,
        BigDecimal totalPenaltyAmount,
        String policyStatus,
        String message) {

    public record PaymentReceipt(
            String transactionReference,
            int installmentNumber,
            BigDecimal amountPaid,
            BigDecimal penaltyAmount,
            BigDecimal remainingOutstanding,
            String paymentStatus) {
    }
}