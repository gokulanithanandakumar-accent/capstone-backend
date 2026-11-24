package com.insuranceapp.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.insuranceapp.backend.entity.PremiumInstallment;
import com.insuranceapp.backend.exception.BusinessRuleException;
import com.insuranceapp.backend.exception.DuplicatePaymentException;

class PremiumPaymentCalculatorTest {
    private static final BigDecimal NO_PENALTY = BigDecimal.ZERO;

    private final PremiumPaymentCalculator calculator = new PremiumPaymentCalculator();

    /** Sample installment 4002: due 2026-12-20, grace end 2027-01-20, 1250.00 scheduled. */
    private static PremiumInstallment installment(String paidAmount) {
        PremiumInstallment installment = new PremiumInstallment();
        installment.setInstallmentNumber(2);
        installment.setDueDate(LocalDate.of(2026, 12, 20));
        installment.setGracePeriodEndDate(LocalDate.of(2027, 1, 20));
        installment.setScheduledAmount(new BigDecimal("1250.00"));
        installment.setPaidAmount(new BigDecimal(paidAmount));
        return installment;
    }

    @Test
    void derivesStatusFromPaidAmountAndGracePeriod() {
        LocalDate withinGrace = LocalDate.of(2027, 1, 10);
        assertThat(calculator.position(installment("0.00"), NO_PENALTY, withinGrace).status())
                .isEqualTo(InstallmentStatus.PENDING);
        assertThat(calculator.position(installment("500.00"), NO_PENALTY, withinGrace).status())
                .isEqualTo(InstallmentStatus.PARTIALLY_PAID);
        assertThat(calculator.position(installment("1250.00"), NO_PENALTY, withinGrace).status())
                .isEqualTo(InstallmentStatus.PAID);
        assertThat(calculator.position(installment("500.00"), NO_PENALTY, LocalDate.of(2027, 1, 21)).status())
                .isEqualTo(InstallmentStatus.OVERDUE);
        assertThat(calculator.position(installment("1250.00"), NO_PENALTY, LocalDate.of(2027, 6, 1)).status())
                .isEqualTo(InstallmentStatus.PAID);
    }

    @Test
    void noPenaltyUpToAndIncludingGracePeriodEndDate() {
        var position = calculator.position(installment("500.00"), NO_PENALTY, LocalDate.of(2027, 1, 20));

        assertThat(position.premiumPeriod()).isEqualTo(YearMonth.of(2026, 12));
        assertThat(position.outstandingPremium()).isEqualByComparingTo("750.00");
        assertThat(position.applicablePenalty()).isEqualByComparingTo("0.00");
        assertThat(position.amountDue()).isEqualByComparingTo("750.00");
    }

    @Test
    void appliesTenPercentPenaltyOnOutstandingPremiumAfterGracePeriod() {
        var position = calculator.position(installment("500.00"), NO_PENALTY, LocalDate.of(2027, 1, 21));

        assertThat(position.applicablePenalty()).isEqualByComparingTo("75.00");
        assertThat(position.amountDue()).isEqualByComparingTo("825.00");
    }

    @Test
    void penaltyIsChargedOnlyOncePerInstallment() {
        // 575.00 received so far, of which 75.00 was the late payment penalty.
        var position = calculator.position(installment("575.00"), new BigDecimal("75.00"), LocalDate.of(2027, 3, 1));

        assertThat(position.premiumPaid()).isEqualByComparingTo("500.00");
        assertThat(position.outstandingPremium()).isEqualByComparingTo("750.00");
        assertThat(position.applicablePenalty()).isEqualByComparingTo("0.00");
        assertThat(position.status()).isEqualTo(InstallmentStatus.OVERDUE);
    }

    @Test
    void fullOnTimePaymentMarksInstallmentPaid() {
        var result = calculator.applyPayment(installment("500.00"), NO_PENALTY, new BigDecimal("750"),
                LocalDate.of(2027, 1, 5));

        assertThat(result.amountPaid()).isEqualByComparingTo("750.00");
        assertThat(result.penaltyAmount()).isEqualByComparingTo("0.00");
        assertThat(result.newPaidAmount()).isEqualByComparingTo("1250.00");
        assertThat(result.remainingOutstanding()).isEqualByComparingTo("0.00");
        assertThat(result.newStatus()).isEqualTo(InstallmentStatus.PAID);
    }

    @Test
    void partialOnTimePaymentRecalculatesOutstanding() {
        var result = calculator.applyPayment(installment("0.00"), NO_PENALTY, new BigDecimal("500.00"),
                LocalDate.of(2026, 12, 20));

        assertThat(result.newPaidAmount()).isEqualByComparingTo("500.00");
        assertThat(result.remainingOutstanding()).isEqualByComparingTo("750.00");
        assertThat(result.newStatus()).isEqualTo(InstallmentStatus.PARTIALLY_PAID);
    }

    @Test
    void latePaymentIncludesPenalty() {
        var full = calculator.applyPayment(installment("500.00"), NO_PENALTY, new BigDecimal("825.00"),
                LocalDate.of(2027, 2, 1));
        assertThat(full.penaltyAmount()).isEqualByComparingTo("75.00");
        assertThat(full.newPaidAmount()).isEqualByComparingTo("1325.00");
        assertThat(full.remainingOutstanding()).isEqualByComparingTo("0.00");
        assertThat(full.newStatus()).isEqualTo(InstallmentStatus.PAID);

        var partial = calculator.applyPayment(installment("500.00"), NO_PENALTY, new BigDecimal("100.00"),
                LocalDate.of(2027, 2, 1));
        assertThat(partial.penaltyAmount()).isEqualByComparingTo("75.00");
        assertThat(partial.remainingOutstanding()).isEqualByComparingTo("725.00");
        assertThat(partial.newStatus()).isEqualTo(InstallmentStatus.OVERDUE);
    }

    @Test
    void rejectsInvalidPayments() {
        LocalDate late = LocalDate.of(2027, 2, 1);
        assertThatThrownBy(() -> calculator.applyPayment(installment("1250.00"), NO_PENALTY, BigDecimal.ONE, late))
                .isInstanceOf(DuplicatePaymentException.class)
                .hasMessage("Installment 2 has already been paid.");
        assertThatThrownBy(() -> calculator.applyPayment(installment("500.00"), NO_PENALTY,
                new BigDecimal("825.01"), late))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Payment of 825.01 for installment 2 exceeds the outstanding amount of 825.00.");
        assertThatThrownBy(() -> calculator.applyPayment(installment("500.00"), NO_PENALTY,
                new BigDecimal("50.00"), late))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("late payment penalty of 75.00");
        assertThatThrownBy(() -> calculator.applyPayment(installment("0.00"), NO_PENALTY, BigDecimal.ZERO,
                LocalDate.of(2026, 12, 1)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void derivesPolicyPaymentStatusFromInstallments() {
        LocalDate withinGrace = LocalDate.of(2027, 1, 10);
        var paid = calculator.position(installment("1250.00"), NO_PENALTY, withinGrace);
        var partial = calculator.position(installment("500.00"), NO_PENALTY, withinGrace);
        var pending = calculator.position(installment("0.00"), NO_PENALTY, withinGrace);
        var overdue = calculator.position(installment("0.00"), NO_PENALTY, LocalDate.of(2027, 2, 1));

        assertThat(calculator.policyPaymentStatus(List.of(paid, paid))).isEqualTo(InstallmentStatus.PAID);
        assertThat(calculator.policyPaymentStatus(List.of(paid, overdue, pending)))
                .isEqualTo(InstallmentStatus.OVERDUE);
        assertThat(calculator.policyPaymentStatus(List.of(paid, pending))).isEqualTo(InstallmentStatus.PARTIALLY_PAID);
        assertThat(calculator.policyPaymentStatus(List.of(partial, pending)))
                .isEqualTo(InstallmentStatus.PARTIALLY_PAID);
        assertThat(calculator.policyPaymentStatus(List.of(pending, pending))).isEqualTo(InstallmentStatus.PENDING);
        assertThat(calculator.policyPaymentStatus(List.of())).isEqualTo(InstallmentStatus.PENDING);
    }
}