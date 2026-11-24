package com.insuranceapp.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import com.insuranceapp.backend.dto.PolicySearchRequest;
import com.insuranceapp.backend.dto.PolicySearchResponse;
import com.insuranceapp.backend.dto.PolicySearchResult;
import com.insuranceapp.backend.entity.Customer;
import com.insuranceapp.backend.entity.CustomerPolicy;
import com.insuranceapp.backend.entity.InsuranceProduct;
import com.insuranceapp.backend.entity.PremiumInstallment;
import com.insuranceapp.backend.entity.PremiumPayment;
import com.insuranceapp.backend.exception.InvalidSearchCriteriaException;
import com.insuranceapp.backend.repository.CustomerPolicyRepository;
import com.insuranceapp.backend.repository.PremiumInstallmentRepository;
import com.insuranceapp.backend.repository.PremiumPaymentRepository;
import com.insuranceapp.backend.service.PolicySearchCriteria.ByCustomerName;
import com.insuranceapp.backend.service.PolicySearchCriteria.ByPolicyNumber;
import com.insuranceapp.backend.service.PolicySearchCriteria.ByPolicyNumberAndDateOfBirth;

/** "Today" is fixed at 2027-01-25; installment 2 of the term policy is past its grace period. */
class PolicySearchServiceTest {
    private static final Long CUSTOMER_ID = 1002L;
    private static final LocalDate DOB = LocalDate.of(1992, 6, 10);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2027-01-25T06:00:00Z"), ZoneId.of("Asia/Kolkata"));

    private CustomerPolicyRepository policyRepository;
    private PremiumInstallmentRepository installmentRepository;
    private PremiumPaymentRepository paymentRepository;
    private PolicySearchService service;
    private CustomerPolicy termPolicy;
    private CustomerPolicy savingsPolicy;

    @BeforeEach
    void setUp() {
        policyRepository = mock(CustomerPolicyRepository.class);
        installmentRepository = mock(PremiumInstallmentRepository.class);
        paymentRepository = mock(PremiumPaymentRepository.class);
        service = new PolicySearchService(policyRepository, installmentRepository, paymentRepository,
                new PremiumPaymentCalculator(), CLOCK);

        Customer customer = new Customer();
        ReflectionTestUtils.setField(customer, "id", CUSTOMER_ID);
        customer.setFullName("Yuvaraj");
        customer.setDateOfBirth(DOB);
        termPolicy = policy(customer, 3001L, "HDP-2026-0001", "HealthDekho Secure Term", "TERM", "3750.00");
        savingsPolicy = policy(customer, 3002L, "HDP-2026-0002", "HealthDekho Smart Savings", "SAVINGS", "15000.00");

        PremiumInstallment term1 = installment(termPolicy, 4001L, 1, LocalDate.of(2026, 11, 20), "1250.00", "1375.00");
        PremiumInstallment term2 = installment(termPolicy, 4002L, 2, LocalDate.of(2026, 12, 20), "1250.00", "500.00");
        PremiumInstallment term3 = installment(termPolicy, 4003L, 3, LocalDate.of(2027, 1, 20), "1250.00", "0.00");
        PremiumInstallment savings1 = installment(savingsPolicy, 4101L, 1, LocalDate.of(2027, 1, 20), "7500.00", "7500.00");
        PremiumInstallment savings2 = installment(savingsPolicy, 4102L, 2, LocalDate.of(2027, 4, 20), "7500.00", "0.00");
        when(installmentRepository.findByPolicyIdIn(any()))
                .thenReturn(List.of(term1, term2, term3, savings1, savings2));
        PremiumPayment latePayment = new PremiumPayment();
        latePayment.setInstallment(term1);
        latePayment.setAmountPaid(new BigDecimal("1375.00"));
        latePayment.setPenaltyAmount(new BigDecimal("125.00"));
        when(paymentRepository.findAllByPolicyIdIn(any())).thenReturn(List.of(latePayment));
    }

    @Test
    void searchesByCustomerNameWithPaginationAndDerivedPaymentFigures() {
        when(policyRepository.searchOwnedByCustomerName(eq(CUSTOMER_ID), eq("%yuvaraj k%"), any()))
                .thenReturn(new PageImpl<>(List.of(savingsPolicy, termPolicy), PageRequest.of(0, 10), 2));

        PolicySearchResponse response = service.search(CUSTOMER_ID,
                new PolicySearchRequest(null, "  Yuvaraj   K ", null, null, null));

        assertThat(response.totalElements()).isEqualTo(2);
        assertThat(response.totalPages()).isEqualTo(1);
        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(PolicySearchService.DEFAULT_PAGE_SIZE);
        assertThat(response.results()).extracting(PolicySearchResult::policyNumber)
                .containsExactly("HDP-2026-0002", "HDP-2026-0001");

        PolicySearchResult savings = response.results().get(0);
        assertThat(savings.premiumPaymentStatus()).isEqualTo("PARTIALLY_PAID");
        assertThat(savings.totalPremiumPaid()).isEqualByComparingTo("7500.00");
        assertThat(savings.remainingPremium()).isEqualByComparingTo("7500.00");

        PolicySearchResult term = response.results().get(1);
        assertThat(term.customerName()).isEqualTo("Yuvaraj");
        assertThat(term.dateOfBirth()).isEqualTo(DOB);
        assertThat(term.policyName()).isEqualTo("HealthDekho Secure Term");
        assertThat(term.policyType()).isEqualTo("TERM");
        assertThat(term.startDate()).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(term.maturityDate()).isEqualTo(LocalDate.of(2027, 11, 19));
        assertThat(term.totalPremiumPayable()).isEqualByComparingTo("3750.00");
        // The 125.00 penalty on installment 1 is excluded from premium paid: 1250 + 500.
        assertThat(term.totalPremiumPaid()).isEqualByComparingTo("1750.00");
        assertThat(term.remainingPremium()).isEqualByComparingTo("2000.00");
        assertThat(term.premiumPaymentStatus()).isEqualTo("OVERDUE");
    }

    @Test
    void passesRequestedPageToRepository() {
        when(policyRepository.searchOwnedByCustomerName(anyLong(), anyString(), any()))
                .thenReturn(new PageImpl<>(List.of(termPolicy), PageRequest.of(1, 1), 2));

        PolicySearchResponse response = service.search(CUSTOMER_ID,
                new PolicySearchRequest(null, "yuva", null, 1, 1));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(policyRepository).searchOwnedByCustomerName(eq(CUSTOMER_ID), eq("%yuva%"), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(1);
        assertThat(response.totalPages()).isEqualTo(2);
        assertThat(response.results()).hasSize(1);
    }

    @Test
    void searchesByPolicyIdentifierCaseInsensitively() {
        when(policyRepository.findOwnedPolicy("HDP-2026-0001", CUSTOMER_ID)).thenReturn(Optional.of(termPolicy));

        PolicySearchResponse response = service.search(CUSTOMER_ID,
                new PolicySearchRequest(" hdp-2026-0001 ", null, null, null, null));

        assertThat(response.results()).extracting(PolicySearchResult::policyNumber).containsExactly("HDP-2026-0001");
        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.totalPages()).isEqualTo(1);
    }

    @Test
    void searchesByPolicyIdentifierAndDateOfBirth() {
        when(policyRepository.findOwnedPolicy("HDP-2026-0001", CUSTOMER_ID)).thenReturn(Optional.of(termPolicy));

        assertThat(service.search(CUSTOMER_ID, new PolicySearchRequest("HDP-2026-0001", null, DOB, null, null))
                .results()).hasSize(1);
        PolicySearchResponse mismatch = service.search(CUSTOMER_ID,
                new PolicySearchRequest("HDP-2026-0001", null, DOB.plusDays(1), null, null));
        assertThat(mismatch.results()).isEmpty();
        assertThat(mismatch.totalElements()).isZero();
    }

    @Test
    void returnsEmptyResultWithoutLoadingPaymentsWhenNothingMatches() {
        when(policyRepository.findOwnedPolicy("HDP-2026-9999", CUSTOMER_ID)).thenReturn(Optional.empty());

        PolicySearchResponse response = service.search(CUSTOMER_ID,
                new PolicySearchRequest("HDP-2026-9999", null, null, null, null));

        assertThat(response.results()).isEmpty();
        assertThat(response.totalPages()).isZero();
        verify(installmentRepository, never()).findByPolicyIdIn(any());
        verify(paymentRepository, never()).findAllByPolicyIdIn(any());
    }

    @Test
    void singleRecordLookupBeyondFirstPageIsEmpty() {
        when(policyRepository.findOwnedPolicy("HDP-2026-0001", CUSTOMER_ID)).thenReturn(Optional.of(termPolicy));

        PolicySearchResponse response = service.search(CUSTOMER_ID,
                new PolicySearchRequest("HDP-2026-0001", null, null, 1, 10));

        assertThat(response.results()).isEmpty();
        assertThat(response.totalElements()).isEqualTo(1);
    }

    @Test
    void resolvesEachSupportedCombination() {
        assertThat(service.resolveCriteria(new PolicySearchRequest("hdp-2026-0001", "", null, null, null)))
                .isEqualTo(new ByPolicyNumber("HDP-2026-0001"));
        assertThat(service.resolveCriteria(new PolicySearchRequest(null, " Yuvaraj ", null, null, null)))
                .isEqualTo(new ByCustomerName("Yuvaraj"));
        assertThat(service.resolveCriteria(new PolicySearchRequest("HDP-2026-0001", null, DOB, null, null)))
                .isEqualTo(new ByPolicyNumberAndDateOfBirth("HDP-2026-0001", DOB));
    }

    @ParameterizedTest
    @CsvSource(nullValues = "null", value = {
            "null, null, null, Enter a Policy Identifier or Customer Name to search.",
            "'  ', '  ', null, Enter a Policy Identifier or Customer Name to search.",
            "null, null, 1992-06-10, Date of Birth can only be used together with Policy Identifier.",
            "null, Yuvaraj, 1992-06-10, Date of Birth can only be used together with Policy Identifier.",
            "HDP-2026-0001, Yuvaraj, null, 'Search by Policy Identifier, Customer Name, or Policy Identifier and Date of Birth.'",
            "null, Y, null, Customer name must contain at least 2 characters."
    })
    void rejectsUnsupportedCriteria(String policyNumber, String customerName, LocalDate dateOfBirth, String message) {
        assertThatThrownBy(() -> service.search(CUSTOMER_ID,
                new PolicySearchRequest(policyNumber, customerName, dateOfBirth, null, null)))
                .isInstanceOf(InvalidSearchCriteriaException.class)
                .hasMessage(message);
        verifyNoInteractions(policyRepository, installmentRepository, paymentRepository);
    }

    private static CustomerPolicy policy(Customer customer, Long id, String number, String name, String type,
            String total) {
        InsuranceProduct product = new InsuranceProduct();
        product.setPolicyName(name);
        product.setPolicyType(type);
        CustomerPolicy policy = new CustomerPolicy();
        ReflectionTestUtils.setField(policy, "id", id);
        policy.setPolicyNumber(number);
        policy.setCustomer(customer);
        policy.setProduct(product);
        policy.setStartDate(LocalDate.of(2026, 11, 20));
        policy.setMaturityDate(LocalDate.of(2027, 11, 19));
        policy.setTotalPremiumPayable(new BigDecimal(total));
        policy.setStatus("ACTIVE");
        return policy;
    }

    private static PremiumInstallment installment(CustomerPolicy policy, Long id, int number, LocalDate dueDate,
            String scheduled, String paid) {
        PremiumInstallment installment = new PremiumInstallment();
        ReflectionTestUtils.setField(installment, "id", id);
        installment.setPolicy(policy);
        installment.setInstallmentNumber(number);
        installment.setDueDate(dueDate);
        installment.setGracePeriodEndDate(dueDate.plusMonths(1));
        installment.setScheduledAmount(new BigDecimal(scheduled));
        installment.setPaidAmount(new BigDecimal(paid));
        installment.setPaymentStatus("PENDING");
        return installment;
    }
}
