package com.insuranceapp.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.insuranceapp.backend.dto.PolicyPremiumDetailsResponse;
import com.insuranceapp.backend.dto.PremiumPaymentRequest;
import com.insuranceapp.backend.dto.PremiumPaymentRequest.InstallmentPayment;
import com.insuranceapp.backend.dto.PremiumPaymentResponse;
import com.insuranceapp.backend.entity.Customer;
import com.insuranceapp.backend.entity.CustomerPolicy;
import com.insuranceapp.backend.entity.InsuranceProduct;
import com.insuranceapp.backend.entity.PremiumInstallment;
import com.insuranceapp.backend.entity.PremiumPayment;
import com.insuranceapp.backend.exception.BusinessRuleException;
import com.insuranceapp.backend.exception.DuplicatePaymentException;
import com.insuranceapp.backend.exception.ResourceNotFoundException;
import com.insuranceapp.backend.repository.CustomerPolicyRepository;
import com.insuranceapp.backend.repository.PremiumInstallmentRepository;
import com.insuranceapp.backend.repository.PremiumPaymentRepository;

/** Uses the specification's sample policy HDP-2026-0001 with "today" fixed at 2027-01-25. */
class PremiumPaymentServiceTest {
    private static final Long CUSTOMER_ID = 1001L;
    private static final String POLICY_NUMBER = "HDP-2026-0001";
    private static final LocalDate TODAY = LocalDate.of(2027, 1, 25);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2027-01-25T06:00:00Z"), ZoneId.of("Asia/Kolkata"));

    private CustomerPolicyRepository policyRepository;
    private PremiumInstallmentRepository installmentRepository;
    private PremiumPaymentRepository paymentRepository;
    private PremiumPaymentService service;
    private CustomerPolicy policy;
    private PremiumInstallment first;
    private PremiumInstallment second;
    private PremiumInstallment third;
    private List<PremiumInstallment> installments;

    @BeforeEach
    void setUp() {
        policyRepository = mock(CustomerPolicyRepository.class);
        installmentRepository = mock(PremiumInstallmentRepository.class);
        paymentRepository = mock(PremiumPaymentRepository.class);
        service = new PremiumPaymentService(policyRepository, installmentRepository, paymentRepository,
                new PremiumPaymentCalculator(), CLOCK);

        Customer customer = new Customer();
        ReflectionTestUtils.setField(customer, "id", CUSTOMER_ID);
        customer.setFullName("Gokul");
        InsuranceProduct product = new InsuranceProduct();
        product.setPolicyName("HealthDekho Secure Term");
        product.setTermMonths(3);
        policy = new CustomerPolicy();
        ReflectionTestUtils.setField(policy, "id", 3001L);
        policy.setPolicyNumber(POLICY_NUMBER);
        policy.setCustomer(customer);
        policy.setProduct(product);
        policy.setStartDate(LocalDate.of(2026, 11, 20));
        policy.setMaturityDate(LocalDate.of(2027, 2, 19));
        policy.setPremiumFrequency("MONTHLY");
        policy.setTotalPremiumPayable(new BigDecimal("3750.00"));
        policy.setStatus("ACTIVE");

        first = installment(4001L, 1, LocalDate.of(2026, 11, 20), "1250.00", "PAID");
        second = installment(4002L, 2, LocalDate.of(2026, 12, 20), "500.00", "PARTIALLY_PAID");
        third = installment(4003L, 3, LocalDate.of(2027, 1, 20), "0.00", "PENDING");
        installments = List.of(first, second, third);

        when(policyRepository.findOwnedPolicy(POLICY_NUMBER, CUSTOMER_ID)).thenReturn(Optional.of(policy));
        when(installmentRepository.findByPolicyIdOrderByInstallmentNumberAsc(3001L)).thenReturn(installments);
        when(installmentRepository.findByPolicyIdForUpdate(3001L)).thenReturn(installments);
        when(paymentRepository.findAllByPolicyId(3001L)).thenReturn(List.of(
                payment(first, "TXN-HD-000001", LocalDate.of(2026, 11, 20), "1250.00", "UPI"),
                payment(second, "TXN-HD-000002", LocalDate.of(2026, 12, 20), "500.00", "NET_BANKING")));
        when(paymentRepository.findMaxTransactionSequence()).thenReturn(3L);
    }

    @Test
    void returnsPolicySummaryScheduleAndHistory() {
        PolicyPremiumDetailsResponse details = service.getPremiumDetails(CUSTOMER_ID, " hdp-2026-0001 ");

        assertThat(details.policy().policyNumber()).isEqualTo(POLICY_NUMBER);
        assertThat(details.policy().customerName()).isEqualTo("Gokul");
        assertThat(details.policy().policyName()).isEqualTo("HealthDekho Secure Term");
        assertThat(details.policy().totalPremiumPayable()).isEqualByComparingTo("3750.00");
        assertThat(details.policy().totalPremiumPaid()).isEqualByComparingTo("1750.00");
        assertThat(details.policy().totalRemainingPremium()).isEqualByComparingTo("2000.00");

        assertThat(details.installments()).extracting(row -> row.paymentStatus())
                .containsExactly("PAID", "OVERDUE", "PENDING");
        assertThat(details.installments()).extracting(row -> row.selectable()).containsExactly(false, true, true);
        var overdue = details.installments().get(1);
        assertThat(overdue.premiumPeriod()).isEqualTo(YearMonth.of(2026, 12));
        assertThat(overdue.outstandingPremium()).isEqualByComparingTo("750.00");
        assertThat(overdue.applicablePenalty()).isEqualByComparingTo("75.00");
        assertThat(overdue.amountDue()).isEqualByComparingTo("825.00");
        assertThat(details.installments().get(2).amountDue()).isEqualByComparingTo("1250.00");

        assertThat(details.payments()).extracting(p -> p.transactionReference())
                .containsExactly("TXN-HD-000001", "TXN-HD-000002");
        assertThat(details.payments().get(1).installmentNumber()).isEqualTo(2);
        assertThat(details.payments().get(1).paymentMethod()).isEqualTo("NET_BANKING");
    }

    @Test
    void paysMultipleInstallmentsAndRecordsTransactions() {
        PremiumPaymentResponse response = service.pay(CUSTOMER_ID, POLICY_NUMBER, new PremiumPaymentRequest(
                PaymentMethod.CARD, List.of(new InstallmentPayment(4003L, new BigDecimal("1250.00")),
                        new InstallmentPayment(4002L, new BigDecimal("100.00")))));

        assertThat(response.paymentDate()).isEqualTo(TODAY);
        assertThat(response.receipts()).extracting(r -> r.transactionReference())
                .containsExactly("TXN-HD-000004", "TXN-HD-000005");
        var overdueReceipt = response.receipts().get(0);
        assertThat(overdueReceipt.installmentNumber()).isEqualTo(2);
        assertThat(overdueReceipt.penaltyAmount()).isEqualByComparingTo("75.00");
        assertThat(overdueReceipt.remainingOutstanding()).isEqualByComparingTo("725.00");
        assertThat(overdueReceipt.paymentStatus()).isEqualTo("OVERDUE");
        assertThat(response.receipts().get(1).paymentStatus()).isEqualTo("PAID");
        assertThat(response.totalAmountPaid()).isEqualByComparingTo("1350.00");
        assertThat(response.totalPenaltyAmount()).isEqualByComparingTo("75.00");
        assertThat(response.policyStatus()).isEqualTo("ACTIVE");

        assertThat(second.getPaidAmount()).isEqualByComparingTo("600.00");
        assertThat(second.getPaymentStatus()).isEqualTo("OVERDUE");
        assertThat(third.getPaidAmount()).isEqualByComparingTo("1250.00");
        assertThat(third.getPaymentStatus()).isEqualTo("PAID");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PremiumPayment>> captor = ArgumentCaptor.forClass(List.class);
        verify(paymentRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2).allSatisfy(payment -> {
            assertThat(payment.getPaymentDate()).isEqualTo(TODAY);
            assertThat(payment.getPaymentMethod()).isEqualTo("CARD");
        });
        assertThat(captor.getValue().get(0).getInstallment()).isSameAs(second);
        assertThat(captor.getValue().get(0).getPenaltyAmount()).isEqualByComparingTo("75.00");
    }

    @Test
    void marksPolicyFullyPaidOnlyWhenAllInstallmentsArePaid() {
        PremiumPaymentResponse response = service.pay(CUSTOMER_ID, POLICY_NUMBER, new PremiumPaymentRequest(
                PaymentMethod.UPI, List.of(new InstallmentPayment(4002L, new BigDecimal("825.00")),
                        new InstallmentPayment(4003L, new BigDecimal("1250.00")))));

        assertThat(response.policyStatus()).isEqualTo("FULLY_PAID");
        assertThat(policy.getStatus()).isEqualTo("FULLY_PAID");
        assertThat(response.message()).contains("fully paid");
    }

    @Test
    void keepsPolicyActiveWhileAnyInstallmentIsOutstanding() {
        service.pay(CUSTOMER_ID, POLICY_NUMBER, new PremiumPaymentRequest(PaymentMethod.UPI,
                List.of(new InstallmentPayment(4003L, new BigDecimal("1250.00")))));

        assertThat(policy.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void rejectsPaymentForAlreadyPaidInstallment() {
        assertThatThrownBy(() -> service.pay(CUSTOMER_ID, POLICY_NUMBER, new PremiumPaymentRequest(
                PaymentMethod.UPI, List.of(new InstallmentPayment(4001L, new BigDecimal("10.00"))))))
                .isInstanceOf(DuplicatePaymentException.class)
                .hasMessage("Installment 1 has already been paid.");
        verify(paymentRepository, never()).saveAll(anyList());
    }

    @Test
    void rejectsSameInstallmentTwiceInOneRequest() {
        assertThatThrownBy(() -> service.pay(CUSTOMER_ID, POLICY_NUMBER, new PremiumPaymentRequest(
                PaymentMethod.UPI, List.of(new InstallmentPayment(4003L, new BigDecimal("600.00")),
                        new InstallmentPayment(4003L, new BigDecimal("650.00"))))))
                .isInstanceOf(DuplicatePaymentException.class);
        verify(paymentRepository, never()).saveAll(anyList());
    }

    @Test
    void rejectsPaymentOnFullyPaidPolicy() {
        policy.setStatus("FULLY_PAID");

        assertThatThrownBy(() -> service.pay(CUSTOMER_ID, POLICY_NUMBER, new PremiumPaymentRequest(
                PaymentMethod.UPI, List.of(new InstallmentPayment(4003L, new BigDecimal("1.00"))))))
                .isInstanceOf(DuplicatePaymentException.class);
    }

    @Test
    void rejectsPaymentExceedingOutstandingAmount() {
        assertThatThrownBy(() -> service.pay(CUSTOMER_ID, POLICY_NUMBER, new PremiumPaymentRequest(
                PaymentMethod.UPI, List.of(new InstallmentPayment(4003L, new BigDecimal("1250.01"))))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("exceeds the outstanding amount of 1250.00");
        verify(paymentRepository, never()).saveAll(anyList());
        assertThat(third.getPaidAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void rejectsInstallmentFromAnotherPolicy() {
        assertThatThrownBy(() -> service.pay(CUSTOMER_ID, POLICY_NUMBER, new PremiumPaymentRequest(
                PaymentMethod.UPI, List.of(new InstallmentPayment(9999L, new BigDecimal("1.00"))))))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void policiesOfOtherCustomersAreNotFound() {
        when(policyRepository.findOwnedPolicy(POLICY_NUMBER, 2002L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPremiumDetails(2002L, POLICY_NUMBER))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("No policy found with identifier HDP-2026-0001.");
        assertThatThrownBy(() -> service.pay(2002L, POLICY_NUMBER, new PremiumPaymentRequest(PaymentMethod.UPI,
                List.of(new InstallmentPayment(4003L, BigDecimal.TEN)))))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private PremiumInstallment installment(Long id, int number, LocalDate dueDate, String paid, String status) {
        PremiumInstallment installment = new PremiumInstallment();
        ReflectionTestUtils.setField(installment, "id", id);
        installment.setPolicy(policy);
        installment.setInstallmentNumber(number);
        installment.setDueDate(dueDate);
        installment.setGracePeriodEndDate(dueDate.plusMonths(1));
        installment.setScheduledAmount(new BigDecimal("1250.00"));
        installment.setPaidAmount(new BigDecimal(paid));
        installment.setPaymentStatus(status);
        return installment;
    }

    private static PremiumPayment payment(PremiumInstallment installment, String reference, LocalDate date,
            String amount, String method) {
        PremiumPayment payment = new PremiumPayment();
        payment.setInstallment(installment);
        payment.setTransactionReference(reference);
        payment.setPaymentDate(date);
        payment.setAmountPaid(new BigDecimal(amount));
        payment.setPenaltyAmount(new BigDecimal("0.00"));
        payment.setPaymentMethod(method);
        return payment;
    }
}