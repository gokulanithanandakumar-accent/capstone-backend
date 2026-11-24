package com.insuranceapp.backend.dto;

import java.math.BigDecimal;
import java.util.List;

import com.insuranceapp.backend.service.PaymentMethod;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** One or more selected installments with the amount paid for each; date and reference are generated. */
public record PremiumPaymentRequest(
        @NotNull(message = "Payment method is required") PaymentMethod paymentMethod,
        @NotEmpty(message = "Select at least one installment to pay")
        @Size(max = 600, message = "Too many installments selected")
        List<@Valid @NotNull InstallmentPayment> payments) {

    public record InstallmentPayment(
            @NotNull(message = "Installment is required") Long installmentId,
            @NotNull(message = "Amount paid is required")
            @DecimalMin(value = "0.01", message = "Amount paid must be greater than zero")
            @Digits(integer = 17, fraction = 2, message = "Amount paid can have at most 2 decimal places")
            BigDecimal amount) {
    }
}