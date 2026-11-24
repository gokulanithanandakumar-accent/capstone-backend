package com.insuranceapp.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.insuranceapp.backend.dto.PolicyRegistrationDetailsResponse;
import com.insuranceapp.backend.dto.PolicyRegistrationRequest;
import com.insuranceapp.backend.dto.PolicyRegistrationResponse;
import com.insuranceapp.backend.entity.AiRecommendationItem;
import com.insuranceapp.backend.entity.Customer;
import com.insuranceapp.backend.entity.CustomerPolicy;
import com.insuranceapp.backend.entity.InsuranceProduct;
import com.insuranceapp.backend.entity.PremiumInstallment;
import com.insuranceapp.backend.exception.BusinessRuleException;
import com.insuranceapp.backend.exception.DuplicatePolicyException;
import com.insuranceapp.backend.exception.ResourceNotFoundException;
import com.insuranceapp.backend.repository.AiRecommendationItemRepository;
import com.insuranceapp.backend.repository.CustomerPolicyRepository;
import com.insuranceapp.backend.repository.CustomerRepository;
import com.insuranceapp.backend.repository.InsuranceProductRepository;
import com.insuranceapp.backend.repository.PremiumInstallmentRepository;

class PolicyRegistrationServiceTest {
    private static final Long CUSTOMER_ID = 1001L;
    private static final Long PRODUCT_ID = 201L;
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 24);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-24T06:00:00Z"), ZoneId.of("Asia/Kolkata"));

    private CustomerRepository customerRepository;
    private InsuranceProductRepository productRepository;
    private CustomerPolicyRepository policyRepository;
    private PremiumInstallmentRepository installmentRepository;
    private AiRecommendationItemRepository itemRepository;
    private PolicyRegistrationService service;
    private Customer customer;
    private InsuranceProduct term;

    @BeforeEach
    void setUp() {
        customerRepository = mock(CustomerRepository.class);
        productRepository = mock(InsuranceProductRepository.class);
        policyRepository = mock(CustomerPolicyRepository.class);
        installmentRepository = mock(PremiumInstallmentRepository.class);
        itemRepository = mock(AiRecommendationItemRepository.class);
        service = new PolicyRegistrationService(customerRepository, productRepository, policyRepository,
                installmentRepository, itemRepository, new PolicyCalculator(), CLOCK);

        customer = new Customer();
        ReflectionTestUtils.setField(customer, "id", CUSTOMER_ID);
        customer.setFullName("Gokul");
        customer.setEmail("gokul@gmail.com");
        customer.setPhone("9000000002");
        customer.setDateOfBirth(LocalDate.of(1990, 4, 15));

        term = new InsuranceProduct();
        ReflectionTestUtils.setField(term, "id", PRODUCT_ID);
        term.setProductCode("HD-TERM-001");
        term.setPolicyName("HealthDekho Secure Term");
        term.setPolicyType("TERM");
        term.setTermMonths(240);
        term.setPremiumFrequency("MONTHLY");
        term.setPremiumAmount(new BigDecimal("1250.00"));
        term.setSumInsured(new BigDecimal("5000000.00"));
        term.setMinEntryAge(18);
        term.setMaxEntryAge(60);
        term.setActive(true);

        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(term));
        when(policyRepository.findMaxPolicySequence("HDP-2026-")).thenReturn(3);
        when(policyRepository.save(any(CustomerPolicy.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void registersPolicyWithCalculatedValuesAndInstallments() {
        PolicyRegistrationResponse response = service.register(CUSTOMER_ID,
                new PolicyRegistrationRequest(PRODUCT_ID, LocalDate.of(2026, 11, 30), null));

        assertThat(response.policyNumber()).isEqualTo("HDP-2026-0004");
        assertThat(response.maturityDate()).isEqualTo(LocalDate.of(2046, 11, 29));
        assertThat(response.totalPremiumPayable()).isEqualByComparingTo("300000.00");
        assertThat(response.numberOfInstallments()).isEqualTo(240);
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.message()).contains("HDP-2026-0004");

        ArgumentCaptor<CustomerPolicy> policyCaptor = ArgumentCaptor.forClass(CustomerPolicy.class);
        verify(policyRepository).save(policyCaptor.capture());
        CustomerPolicy policy = policyCaptor.getValue();
        assertThat(policy.getCustomer()).isSameAs(customer);
        assertThat(policy.getProduct()).isSameAs(term);
        assertThat(policy.getStartDate()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(policy.getPremiumFrequency()).isEqualTo("MONTHLY");
        assertThat(policy.getPremiumAmount()).isEqualByComparingTo("1250.00");
        assertThat(policy.getSumInsured()).isEqualByComparingTo("5000000.00");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PremiumInstallment>> installmentsCaptor = ArgumentCaptor.forClass(List.class);
        verify(installmentRepository).saveAll(installmentsCaptor.capture());
        List<PremiumInstallment> installments = installmentsCaptor.getValue();
        assertThat(installments).hasSize(240).allSatisfy(installment -> {
            assertThat(installment.getPolicy()).isSameAs(policy);
            assertThat(installment.getPaidAmount()).isEqualByComparingTo("0");
            assertThat(installment.getPaymentStatus()).isEqualTo("PENDING");
        });
        assertThat(installments.get(0).getDueDate()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(installments.get(0).getGracePeriodEndDate()).isEqualTo(LocalDate.of(2026, 12, 30));
    }

    @Test
    void rejectsDuplicateRegistration() {
        when(policyRepository.existsByCustomerIdAndProductId(CUSTOMER_ID, PRODUCT_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.register(CUSTOMER_ID,
                new PolicyRegistrationRequest(PRODUCT_ID, TODAY, null)))
                .isInstanceOf(DuplicatePolicyException.class)
                .hasMessage("You have already registered a policy for HealthDekho Secure Term.");
        verify(policyRepository, never()).save(any());
        verify(installmentRepository, never()).saveAll(anyList());
    }

    @Test
    void rejectsCustomerBelowMinimumEntryAge() {
        customer.setDateOfBirth(LocalDate.of(2010, 1, 1));

        assertThatThrownBy(() -> service.register(CUSTOMER_ID,
                new PolicyRegistrationRequest(PRODUCT_ID, TODAY, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Entry age must be between 18 and 60")
                .hasMessageContaining("16");
        verify(policyRepository, never()).save(any());
    }

    @Test
    void rejectsCustomerAboveMaximumEntryAge() {
        customer.setDateOfBirth(LocalDate.of(1965, 1, 1));

        assertThatThrownBy(() -> service.register(CUSTOMER_ID,
                new PolicyRegistrationRequest(PRODUCT_ID, TODAY, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not eligible");
    }

    @Test
    void evaluatesEntryAgeOnStartDate() {
        // Turns 18 on 2026-12-01: ineligible today, eligible from that start date.
        customer.setDateOfBirth(LocalDate.of(2008, 12, 1));

        assertThatThrownBy(() -> service.register(CUSTOMER_ID,
                new PolicyRegistrationRequest(PRODUCT_ID, LocalDate.of(2026, 11, 30), null)))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(service.register(CUSTOMER_ID,
                new PolicyRegistrationRequest(PRODUCT_ID, LocalDate.of(2026, 12, 1), null)).status())
                .isEqualTo("ACTIVE");
    }

    @Test
    void rejectsStartDateInThePast() {
        assertThatThrownBy(() -> service.register(CUSTOMER_ID,
                new PolicyRegistrationRequest(PRODUCT_ID, TODAY.minusDays(1), null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Policy start date cannot be in the past.");
    }

    @Test
    void rejectsMissingOrFutureDateOfBirth() {
        customer.setDateOfBirth(null);
        assertThatThrownBy(() -> service.register(CUSTOMER_ID,
                new PolicyRegistrationRequest(PRODUCT_ID, TODAY, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Date of birth is required");

        customer.setDateOfBirth(TODAY.plusDays(1));
        assertThatThrownBy(() -> service.register(CUSTOMER_ID,
                new PolicyRegistrationRequest(PRODUCT_ID, TODAY, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("cannot be in the future");
    }

    @Test
    void rejectsInactiveOrUnknownProduct() {
        term.setActive(false);
        assertThatThrownBy(() -> service.register(CUSTOMER_ID,
                new PolicyRegistrationRequest(PRODUCT_ID, TODAY, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("no longer available");

        assertThatThrownBy(() -> service.register(CUSTOMER_ID,
                new PolicyRegistrationRequest(999L, TODAY, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void marksRecommendationItemSelectedForRegistration() {
        AiRecommendationItem item = new AiRecommendationItem();
        item.setProduct(term);
        when(itemRepository.findByIdAndRecommendationRequestCustomerId(7001L, CUSTOMER_ID))
                .thenReturn(Optional.of(item));

        service.register(CUSTOMER_ID, new PolicyRegistrationRequest(PRODUCT_ID, TODAY, 7001L));

        assertThat(item.isSelectedForRegistration()).isTrue();
    }

    @Test
    void rejectsRecommendationItemForAnotherProductOrCustomer() {
        InsuranceProduct other = new InsuranceProduct();
        ReflectionTestUtils.setField(other, "id", 202L);
        AiRecommendationItem item = new AiRecommendationItem();
        item.setProduct(other);
        when(itemRepository.findByIdAndRecommendationRequestCustomerId(7001L, CUSTOMER_ID))
                .thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.register(CUSTOMER_ID,
                new PolicyRegistrationRequest(PRODUCT_ID, TODAY, 7001L)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("recommendation is not valid");
        assertThatThrownBy(() -> service.register(CUSTOMER_ID,
                new PolicyRegistrationRequest(PRODUCT_ID, TODAY, 8888L)))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(item.isSelectedForRegistration()).isFalse();
        verify(policyRepository, never()).save(any());
    }

    @Test
    void registrationDetailsDefaultStartDateToToday() {
        PolicyRegistrationDetailsResponse details = service.getRegistrationDetails(CUSTOMER_ID, PRODUCT_ID, null);

        assertThat(details.customerName()).isEqualTo("Gokul");
        assertThat(details.dateOfBirth()).isEqualTo(LocalDate.of(1990, 4, 15));
        assertThat(details.email()).isEqualTo("gokul@gmail.com");
        assertThat(details.phone()).isEqualTo("9000000002");
        assertThat(details.productCode()).isEqualTo("HD-TERM-001");
        assertThat(details.startDate()).isEqualTo(TODAY);
        assertThat(details.maturityDate()).isEqualTo(LocalDate.of(2046, 11, 23));
        assertThat(details.totalPremiumPayable()).isEqualByComparingTo("300000.00");
        assertThat(details.numberOfInstallments()).isEqualTo(240);
    }

    @Test
    void registrationDetailsRejectPastStartDate() {
        assertThatThrownBy(() -> service.getRegistrationDetails(CUSTOMER_ID, PRODUCT_ID, TODAY.minusDays(3)))
                .isInstanceOf(BusinessRuleException.class);
    }
}
