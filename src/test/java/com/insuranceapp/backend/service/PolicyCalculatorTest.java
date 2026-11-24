package com.insuranceapp.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.insuranceapp.backend.exception.BusinessRuleException;

class PolicyCalculatorTest {
    private final PolicyCalculator calculator = new PolicyCalculator();

    @Test
    void calculatesSampleMonthlyTermPolicy() {
        PolicyCalculator.PolicyTerms terms = calculator.calculateTerms(LocalDate.of(2026, 11, 20), 240, "MONTHLY",
                new BigDecimal("1250"), new BigDecimal("5000000"));

        assertThat(terms.maturityDate()).isEqualTo(LocalDate.of(2046, 11, 19));
        assertThat(terms.frequency()).isEqualTo(PremiumFrequency.MONTHLY);
        assertThat(terms.numberOfInstallments()).isEqualTo(240);
        assertThat(terms.premiumAmount()).isEqualByComparingTo("1250.00");
        assertThat(terms.totalPremiumPayable()).isEqualByComparingTo("300000.00");
    }

    @Test
    void calculatesYearlyAndQuarterlyTotals() {
        PolicyCalculator.PolicyTerms yearly = calculator.calculateTerms(LocalDate.of(2026, 11, 20), 12, "YEARLY",
                new BigDecimal("18000"), new BigDecimal("1000000"));
        assertThat(yearly.numberOfInstallments()).isEqualTo(1);
        assertThat(yearly.totalPremiumPayable()).isEqualByComparingTo("18000.00");
        assertThat(yearly.maturityDate()).isEqualTo(LocalDate.of(2027, 11, 19));

        PolicyCalculator.PolicyTerms quarterly = calculator.calculateTerms(LocalDate.of(2026, 11, 20), 120,
                "quarterly", new BigDecimal("7500"), new BigDecimal("1500000"));
        assertThat(quarterly.numberOfInstallments()).isEqualTo(40);
        assertThat(quarterly.totalPremiumPayable()).isEqualByComparingTo("300000.00");
    }

    @Test
    void buildsScheduleWithOneMonthGracePeriod() {
        PolicyCalculator.PolicyTerms terms = calculator.calculateTerms(LocalDate.of(2026, 11, 20), 240, "MONTHLY",
                new BigDecimal("1250"), new BigDecimal("5000000"));

        List<PolicyCalculator.ScheduledInstallment> schedule = calculator.buildSchedule(terms);

        assertThat(schedule).hasSize(240);
        assertThat(schedule.get(0)).isEqualTo(new PolicyCalculator.ScheduledInstallment(1, LocalDate.of(2026, 11, 20),
                LocalDate.of(2026, 12, 20), new BigDecimal("1250.00")));
        assertThat(schedule.get(1)).isEqualTo(new PolicyCalculator.ScheduledInstallment(2, LocalDate.of(2026, 12, 20),
                LocalDate.of(2027, 1, 20), new BigDecimal("1250.00")));
        assertThat(schedule.get(239).dueDate()).isEqualTo(LocalDate.of(2046, 10, 20));
        assertThat(schedule.stream().map(PolicyCalculator.ScheduledInstallment::scheduledAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(terms.totalPremiumPayable());
    }

    @Test
    void scheduleDueDatesDoNotDriftAfterMonthEnd() {
        PolicyCalculator.PolicyTerms terms = calculator.calculateTerms(LocalDate.of(2027, 1, 31), 3, "MONTHLY",
                new BigDecimal("100"), new BigDecimal("1000"));

        assertThat(calculator.buildSchedule(terms)).extracting(PolicyCalculator.ScheduledInstallment::dueDate)
                .containsExactly(LocalDate.of(2027, 1, 31), LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 31));
    }

    @Test
    void quarterlyScheduleIsSpacedThreeMonthsApart() {
        PolicyCalculator.PolicyTerms terms = calculator.calculateTerms(LocalDate.of(2026, 11, 20), 120, "QUARTERLY",
                new BigDecimal("7500"), new BigDecimal("1500000"));

        List<PolicyCalculator.ScheduledInstallment> schedule = calculator.buildSchedule(terms);
        assertThat(schedule.get(1).dueDate()).isEqualTo(LocalDate.of(2027, 2, 20));
        assertThat(schedule.get(1).gracePeriodEndDate()).isEqualTo(LocalDate.of(2027, 3, 20));
    }

    @Test
    void rejectsInvalidProductValues() {
        LocalDate start = LocalDate.of(2026, 11, 20);
        assertThatThrownBy(() -> calculator.calculateTerms(start, 0, "MONTHLY", BigDecimal.TEN, BigDecimal.TEN))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("term");
        assertThatThrownBy(() -> calculator.calculateTerms(start, 12, "MONTHLY", BigDecimal.ZERO, BigDecimal.TEN))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Premium amount");
        assertThatThrownBy(() -> calculator.calculateTerms(start, 12, "MONTHLY", BigDecimal.TEN, null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Sum insured");
        assertThatThrownBy(() -> calculator.calculateTerms(start, 12, "WEEKLY", BigDecimal.TEN, BigDecimal.TEN))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Unsupported premium frequency");
        assertThatThrownBy(() -> calculator.calculateTerms(start, 10, "QUARTERLY", BigDecimal.TEN, BigDecimal.TEN))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("not compatible");
    }
}
