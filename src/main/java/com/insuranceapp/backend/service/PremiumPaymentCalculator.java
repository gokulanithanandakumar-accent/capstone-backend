package com.insuranceapp.backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.insuranceapp.backend.entity.PremiumInstallment;
import com.insuranceapp.backend.exception.BusinessRuleException;
import com.insuranceapp.backend.exception.DuplicatePaymentException;

/**
 * Deterministic FR-4 premium payment rules.
 * <p>
 * {@code paid_amount} on an installment is the cumulative amount received, including any late payment penalty.
 * A 10% penalty on the outstanding premium applies once per installment when it is paid after the grace period
 * end date; the payment that incurs the penalty must at least cover it.
 */
@Component
public class PremiumPaymentCalculator {
    static final BigDecimal PENALTY_RATE = new BigDecimal("0.10");
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    /** Financial position of an installment on a given date. */
    public record InstallmentPosition(YearMonth premiumPeriod, BigDecimal premiumPaid, BigDecimal penaltyPaid,
            BigDecimal outstandingPremium, BigDecimal applicablePenalty, BigDecimal amountDue,
            InstallmentStatus status) {
    }

    /** Outcome of applying one payment to an installment. */
    public record PaymentApplication(BigDecimal amountPaid, BigDecimal penaltyAmount, BigDecimal newPaidAmount,
            BigDecimal remainingOutstanding, InstallmentStatus newStatus) {
    }

    public InstallmentPosition position(PremiumInstallment installment, BigDecimal penaltyPaid, LocalDate asOf) {
        BigDecimal penalty = scale(penaltyPaid);
        BigDecimal premiumPaid = scale(installment.getPaidAmount()).subtract(penalty);
        BigDecimal outstanding = scale(installment.getScheduledAmount()).subtract(premiumPaid).max(ZERO);
        boolean late = asOf.isAfter(installment.getGracePeriodEndDate());
        BigDecimal applicablePenalty = outstanding.signum() > 0 && late && penalty.signum() == 0
                ? outstanding.multiply(PENALTY_RATE).setScale(2, RoundingMode.HALF_UP)
                : ZERO;
        return new InstallmentPosition(YearMonth.from(installment.getDueDate()), premiumPaid, penalty, outstanding,
                applicablePenalty, outstanding.add(applicablePenalty),
                status(outstanding, premiumPaid, installment.getGracePeriodEndDate(), asOf));
    }

    /**
     * Policy-level payment status for FR-5, using the FR-4 status vocabulary: PAID when every installment is paid,
     * OVERDUE when any installment is past its grace period with premium outstanding, PARTIALLY_PAID when some
     * premium has been received, otherwise PENDING.
     */
    public InstallmentStatus policyPaymentStatus(List<InstallmentPosition> positions) {
        Map<InstallmentStatus, Long> counts = positions.stream().collect(Collectors.groupingBy(
                InstallmentPosition::status, () -> new EnumMap<>(InstallmentStatus.class), Collectors.counting()));
        if (!positions.isEmpty() && counts.getOrDefault(InstallmentStatus.PAID, 0L) == positions.size()) {
            return InstallmentStatus.PAID;
        }
        if (counts.containsKey(InstallmentStatus.OVERDUE)) {
            return InstallmentStatus.OVERDUE;
        }
        boolean anyPremiumPaid = positions.stream().anyMatch(position -> position.premiumPaid().signum() > 0);
        return anyPremiumPaid ? InstallmentStatus.PARTIALLY_PAID : InstallmentStatus.PENDING;
    }

    public PaymentApplication applyPayment(PremiumInstallment installment, BigDecimal penaltyPaid, BigDecimal amount,
            LocalDate paymentDate) {
        InstallmentPosition position = position(installment, penaltyPaid, paymentDate);
        int number = installment.getInstallmentNumber();
        if (!position.status().isPayable()) {
            throw new DuplicatePaymentException("Installment " + number + " has already been paid.");
        }
        BigDecimal payment = scale(amount);
        if (payment.signum() <= 0) {
            throw new BusinessRuleException("Payment amount for installment " + number + " must be greater than zero.");
        }
        if (payment.compareTo(position.amountDue()) > 0) {
            throw new BusinessRuleException("Payment of " + payment + " for installment " + number
                    + " exceeds the outstanding amount of " + position.amountDue() + ".");
        }
        if (payment.compareTo(position.applicablePenalty()) < 0) {
            throw new BusinessRuleException("Installment " + number + " is past its grace period. The payment must "
                    + "cover at least the late payment penalty of " + position.applicablePenalty() + ".");
        }

        BigDecimal newPaidAmount = scale(installment.getPaidAmount()).add(payment);
        BigDecimal newPremiumPaid = position.premiumPaid().add(payment.subtract(position.applicablePenalty()));
        BigDecimal remaining = scale(installment.getScheduledAmount()).subtract(newPremiumPaid).max(ZERO);
        return new PaymentApplication(payment, position.applicablePenalty(), newPaidAmount, remaining,
                status(remaining, newPremiumPaid, installment.getGracePeriodEndDate(), paymentDate));
    }

    private static InstallmentStatus status(BigDecimal outstanding, BigDecimal premiumPaid, LocalDate graceEnd,
            LocalDate asOf) {
        if (outstanding.signum() == 0) {
            return InstallmentStatus.PAID;
        }
        if (asOf.isAfter(graceEnd)) {
            return InstallmentStatus.OVERDUE;
        }
        return premiumPaid.signum() > 0 ? InstallmentStatus.PARTIALLY_PAID : InstallmentStatus.PENDING;
    }

    private static BigDecimal scale(BigDecimal value) {
        return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }
}