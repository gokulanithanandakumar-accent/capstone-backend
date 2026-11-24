package com.insuranceapp.backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import org.springframework.stereotype.Component;

import com.insuranceapp.backend.exception.BusinessRuleException;

/** Deterministic policy date and premium calculations (FR-3). */
@Component
public class PolicyCalculator {
    static final int GRACE_PERIOD_MONTHS = 1;

    public record PolicyTerms(LocalDate startDate, LocalDate maturityDate, PremiumFrequency frequency,
            int numberOfInstallments, BigDecimal premiumAmount, BigDecimal totalPremiumPayable) {
    }

    public record ScheduledInstallment(int installmentNumber, LocalDate dueDate, LocalDate gracePeriodEndDate,
            BigDecimal scheduledAmount) {
    }

    /** Validates the product values and derives maturity date, installment count and total premium payable. */
    public PolicyTerms calculateTerms(LocalDate startDate, int termMonths, String premiumFrequency,
            BigDecimal premiumAmount, BigDecimal sumInsured) {
        if (termMonths <= 0) {
            throw new BusinessRuleException("Policy term must be positive.");
        }
        if (premiumAmount == null || premiumAmount.signum() <= 0) {
            throw new BusinessRuleException("Premium amount must be positive.");
        }
        if (sumInsured == null || sumInsured.signum() <= 0) {
            throw new BusinessRuleException("Sum insured must be positive.");
        }
        PremiumFrequency frequency = PremiumFrequency.from(premiumFrequency)
                .orElseThrow(() -> new BusinessRuleException("Unsupported premium frequency: " + premiumFrequency));
        if (termMonths % frequency.monthsPerInstallment() != 0) {
            throw new BusinessRuleException("Policy term is not compatible with the premium frequency.");
        }

        LocalDate maturityDate = maturityDate(startDate, termMonths);
        if (!maturityDate.isAfter(startDate)) {
            throw new BusinessRuleException("Policy maturity date must be after the policy start date.");
        }
        int installments = termMonths / frequency.monthsPerInstallment();
        BigDecimal total = premiumAmount.multiply(BigDecimal.valueOf(installments)).setScale(2, RoundingMode.HALF_UP);
        return new PolicyTerms(startDate, maturityDate, frequency, installments,
                premiumAmount.setScale(2, RoundingMode.HALF_UP), total);
    }

    /** Maturity is the last day of cover: e.g. 2026-11-20 + 240 months gives 2046-11-19. */
    public LocalDate maturityDate(LocalDate startDate, int termMonths) {
        return startDate.plusMonths(termMonths).minusDays(1);
    }

    /** One installment per frequency period from the start date, each with a one-month grace period. */
    public List<ScheduledInstallment> buildSchedule(PolicyTerms terms) {
        int monthsPerInstallment = terms.frequency().monthsPerInstallment();
        return IntStream.rangeClosed(1, terms.numberOfInstallments())
                .mapToObj(number -> {
                    LocalDate dueDate = terms.startDate().plusMonths((long) (number - 1) * monthsPerInstallment);
                    return new ScheduledInstallment(number, dueDate, dueDate.plusMonths(GRACE_PERIOD_MONTHS),
                            terms.premiumAmount());
                })
                .toList();
    }
}
