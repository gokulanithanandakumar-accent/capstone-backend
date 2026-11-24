package com.insuranceapp.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import com.insuranceapp.backend.ai.AiRecommendationOutcome;
import com.insuranceapp.backend.ai.AiSuggestion;
import com.insuranceapp.backend.ai.InsuranceRecommendationAiClient;
import com.insuranceapp.backend.ai.ProductCandidate;
import com.insuranceapp.backend.ai.RecommendationPromptInput;
import com.insuranceapp.backend.config.RecommendationProperties;
import com.insuranceapp.backend.dto.RecommendationRequest;
import com.insuranceapp.backend.dto.RecommendationResponse;
import com.insuranceapp.backend.entity.AiRecommendationItem;
import com.insuranceapp.backend.entity.AiRecommendationRequest;
import com.insuranceapp.backend.entity.Customer;
import com.insuranceapp.backend.entity.CustomerPolicy;
import com.insuranceapp.backend.entity.InsuranceProduct;
import com.insuranceapp.backend.exception.AiServiceUnavailableException;
import com.insuranceapp.backend.exception.ResourceNotFoundException;
import com.insuranceapp.backend.repository.AiRecommendationItemRepository;
import com.insuranceapp.backend.repository.AiRecommendationRequestRepository;
import com.insuranceapp.backend.repository.CustomerPolicyRepository;
import com.insuranceapp.backend.repository.CustomerRepository;
import com.insuranceapp.backend.repository.InsuranceProductRepository;

class RecommendationServiceTest {
    private static final Long CUSTOMER_ID = 1001L;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-24T06:00:00Z"), ZoneId.of("Asia/Kolkata"));

    private CustomerRepository customerRepository;
    private InsuranceProductRepository productRepository;
    private CustomerPolicyRepository policyRepository;
    private AiRecommendationRequestRepository requestRepository;
    private AiRecommendationItemRepository itemRepository;
    private InsuranceRecommendationAiClient aiClient;
    private RecommendationService service;

    private InsuranceProduct term;
    private InsuranceProduct health;
    private InsuranceProduct savings;
    private Customer customer;

    @BeforeEach
    void setUp() {
        customerRepository = mock(CustomerRepository.class);
        productRepository = mock(InsuranceProductRepository.class);
        policyRepository = mock(CustomerPolicyRepository.class);
        requestRepository = mock(AiRecommendationRequestRepository.class);
        itemRepository = mock(AiRecommendationItemRepository.class);
        aiClient = mock(InsuranceRecommendationAiClient.class);
        service = new RecommendationService(customerRepository, productRepository, policyRepository,
                requestRepository, itemRepository, aiClient,
                new RecommendationProperties("global.anthropic.claude-haiku-4-5-20251001-v1:0", "rec-v1", 3), CLOCK,
                mock(PlatformTransactionManager.class));

        term = product(201L, "HD-TERM-001", "HealthDekho Secure Term", "TERM", 240, 18, 60);
        health = product(202L, "HD-HEALTH-001", "HealthDekho Family Health", "HEALTH", 12, 18, 65);
        savings = product(203L, "HD-SAV-001", "HealthDekho Smart Savings", "SAVINGS", 120, 21, 55);

        customer = new Customer();
        ReflectionTestUtils.setField(customer, "id", CUSTOMER_ID);
        customer.setDateOfBirth(LocalDate.of(1990, 4, 15));

        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));
        when(customerRepository.getReferenceById(CUSTOMER_ID)).thenReturn(customer);
        when(productRepository.findByActiveTrueOrderByProductCodeAsc()).thenReturn(List.of(health, savings, term));
        when(policyRepository.findAllWithProductByCustomerId(CUSTOMER_ID)).thenReturn(List.of());
        when(productRepository.findAllById(anyList())).thenAnswer(invocation -> {
            List<Long> ids = invocation.getArgument(0);
            return List.of(term, health, savings).stream().filter(p -> ids.contains(p.getId())).toList();
        });
        when(requestRepository.save(any(AiRecommendationRequest.class))).thenAnswer(invocation -> {
            AiRecommendationRequest request = invocation.getArgument(0);
            ReflectionTestUtils.setField(request, "id", 6001L);
            return request;
        });
        AtomicLong itemIds = new AtomicLong(7001L);
        when(itemRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<AiRecommendationItem> items = new ArrayList<>(invocation.getArgument(0));
            items.forEach(item -> ReflectionTestUtils.setField(item, "id", itemIds.getAndIncrement()));
            return items;
        });
    }

    @Test
    void calculatesAgeWithJavaTimeApi() {
        assertThat(service.calculateAge(LocalDate.of(1990, 11, 24))).isEqualTo(36);
        assertThat(service.calculateAge(LocalDate.of(1990, 11, 25))).isEqualTo(35);
        assertThat(service.calculateAge(LocalDate.of(2026, 11, 24))).isZero();
    }

    @Test
    void sendsOnlyUnregisteredAgeEligibleProductsToAi() {
        CustomerPolicy healthPolicy = new CustomerPolicy();
        healthPolicy.setProduct(health);
        when(policyRepository.findAllWithProductByCustomerId(CUSTOMER_ID)).thenReturn(List.of(healthPolicy));
        when(aiClient.recommend(any())).thenReturn(new AiRecommendationOutcome(List.of(), null));

        // Age 20: savings requires a minimum entry age of 21 and health is already registered.
        RecommendationResponse response = service.generateRecommendations(CUSTOMER_ID,
                new RecommendationRequest(LocalDate.of(2006, 1, 1), "  Family income protection  ", "", null));

        ArgumentCaptor<RecommendationPromptInput> captor = ArgumentCaptor.forClass(RecommendationPromptInput.class);
        verify(aiClient).recommend(captor.capture());
        RecommendationPromptInput input = captor.getValue();
        assertThat(input.customerAge()).isEqualTo(20);
        assertThat(input.insuranceGoals()).isEqualTo("Family income protection");
        assertThat(input.preferredTerm()).isNull();
        assertThat(input.maxRecommendations()).isEqualTo(3);
        assertThat(input.availableProducts()).extracting(ProductCandidate::productCode).containsExactly("HD-TERM-001");
        assertThat(response.calculatedAge()).isEqualTo(20);
    }

    @Test
    void usesStoredDateOfBirthWhenNotProvided() {
        when(aiClient.recommend(any())).thenReturn(new AiRecommendationOutcome(List.of(), null));

        RecommendationResponse response = service.generateRecommendations(CUSTOMER_ID,
                new RecommendationRequest(null, null, null, "HEALTH"));

        assertThat(response.calculatedAge()).isEqualTo(36);
        ArgumentCaptor<RecommendationPromptInput> captor = ArgumentCaptor.forClass(RecommendationPromptInput.class);
        verify(aiClient).recommend(captor.capture());
        assertThat(captor.getValue().preferredPolicyType()).isEqualTo("HEALTH");
        assertThat(captor.getValue().availableProducts()).hasSize(3);
    }

    @Test
    void keepsOnlyValidAiSuggestionsAndPersistsRequestAndItems() {
        CustomerPolicy termPolicy = new CustomerPolicy();
        termPolicy.setProduct(term);
        when(policyRepository.findAllWithProductByCustomerId(CUSTOMER_ID)).thenReturn(List.of(termPolicy));
        when(aiClient.recommend(any())).thenReturn(new AiRecommendationOutcome(List.of(
                new AiSuggestion("HD-TERM-001", "Already registered", "HIGH"),
                new AiSuggestion("HD-FAKE-999", "Invented product", "HIGH"),
                new AiSuggestion(" HD-HEALTH-001 ", "Annual family health cover matches stated preference", "high"),
                new AiSuggestion("HD-SAV-001", "Unknown rating", "EXCELLENT"),
                new AiSuggestion("HD-HEALTH-001", "Duplicate", "LOW"),
                new AiSuggestion("HD-SAV-001", "Savings option supports a secondary wealth goal", "MEDIUM"),
                new AiSuggestion(null, "No code", "LOW")), "claude-haiku-4-5"));

        RecommendationResponse response = service.generateRecommendations(CUSTOMER_ID,
                new RecommendationRequest(null, "Health coverage for self and parents", "1 year", "HEALTH"));

        assertThat(response.recommendationRequestId()).isEqualTo(6001L);
        assertThat(response.recommendations()).hasSize(2);
        var first = response.recommendations().get(0);
        assertThat(first.recommendationItemId()).isEqualTo(7001L);
        assertThat(first.rankOrder()).isEqualTo(1);
        assertThat(first.productId()).isEqualTo(202L);
        assertThat(first.productCode()).isEqualTo("HD-HEALTH-001");
        assertThat(first.policyName()).isEqualTo("HealthDekho Family Health");
        assertThat(first.policyType()).isEqualTo("HEALTH");
        assertThat(first.termMonths()).isEqualTo(12);
        assertThat(first.sumInsured()).isEqualByComparingTo("1000000.00");
        assertThat(first.premiumFrequency()).isEqualTo("MONTHLY");
        assertThat(first.premiumAmount()).isEqualByComparingTo("1000.00");
        assertThat(first.recommendationReason()).isEqualTo("Annual family health cover matches stated preference");
        assertThat(first.suitability()).isEqualTo("HIGH");
        var second = response.recommendations().get(1);
        assertThat(second.rankOrder()).isEqualTo(2);
        assertThat(second.productCode()).isEqualTo("HD-SAV-001");
        assertThat(second.suitability()).isEqualTo("MEDIUM");

        ArgumentCaptor<AiRecommendationRequest> requestCaptor = ArgumentCaptor.forClass(AiRecommendationRequest.class);
        verify(requestRepository).save(requestCaptor.capture());
        AiRecommendationRequest saved = requestCaptor.getValue();
        assertThat(saved.getCustomer()).isSameAs(customer);
        assertThat(saved.getInsuranceGoals()).isEqualTo("Health coverage for self and parents");
        assertThat(saved.getPreferredTerm()).isEqualTo("1 year");
        assertThat(saved.getPreferredPolicyType()).isEqualTo("HEALTH");
        assertThat(saved.getCalculatedAge()).isEqualTo(36);
        assertThat(saved.getModelName()).isEqualTo("global.anthropic.claude-haiku-4-5-20251001-v1:0");
        assertThat(saved.getModelVersion()).isEqualTo("claude-haiku-4-5");
        assertThat(saved.getPromptVersion()).isEqualTo("rec-v1");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AiRecommendationItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(itemRepository).saveAll(itemsCaptor.capture());
        assertThat(itemsCaptor.getValue()).allSatisfy(item -> {
            assertThat(item.getRecommendationRequest()).isSameAs(saved);
            assertThat(item.isSelectedForRegistration()).isFalse();
        });
    }

    @Test
    void limitsRecommendationsToConfiguredMaximum() {
        service = new RecommendationService(customerRepository, productRepository, policyRepository,
                requestRepository, itemRepository, aiClient, new RecommendationProperties("model", "rec-v1", 1), CLOCK,
                mock(PlatformTransactionManager.class));
        when(aiClient.recommend(any())).thenReturn(new AiRecommendationOutcome(List.of(
                new AiSuggestion("HD-TERM-001", "Best fit", "HIGH"),
                new AiSuggestion("HD-HEALTH-001", "Second", "MEDIUM")), null));

        RecommendationResponse response = service.generateRecommendations(CUSTOMER_ID,
                new RecommendationRequest(null, "Protection", null, null));

        assertThat(response.recommendations()).extracting(r -> r.productCode()).containsExactly("HD-TERM-001");
    }

    @Test
    void skipsProductDeactivatedBeforePersistence() {
        when(aiClient.recommend(any())).thenReturn(new AiRecommendationOutcome(List.of(
                new AiSuggestion("HD-TERM-001", "Best fit", "HIGH"),
                new AiSuggestion("HD-HEALTH-001", "Second", "MEDIUM")), null));
        when(productRepository.findAllById(anyList())).thenAnswer(invocation -> {
            term.setActive(false);
            return List.of(term, health);
        });

        RecommendationResponse response = service.generateRecommendations(CUSTOMER_ID,
                new RecommendationRequest(null, "Protection", null, null));

        assertThat(response.recommendations()).extracting(r -> r.productCode()).containsExactly("HD-HEALTH-001");
        assertThat(response.recommendations().get(0).rankOrder()).isEqualTo(1);
    }

    @Test
    void doesNotCallAiWhenNoProductIsEligible() {
        // Age 70 is above every product's maximum entry age.
        RecommendationResponse response = service.generateRecommendations(CUSTOMER_ID,
                new RecommendationRequest(LocalDate.of(1956, 1, 1), null, null, null));

        assertThat(response.recommendationRequestId()).isNull();
        assertThat(response.calculatedAge()).isEqualTo(70);
        assertThat(response.recommendations()).isEmpty();
        verifyNoInteractions(aiClient, requestRepository, itemRepository);
    }

    @Test
    void propagatesAiFailureWithoutPersisting() {
        when(aiClient.recommend(any())).thenThrow(new AiServiceUnavailableException("unavailable", null));

        assertThatThrownBy(() -> service.generateRecommendations(CUSTOMER_ID,
                new RecommendationRequest(null, "Protection", null, null)))
                .isInstanceOf(AiServiceUnavailableException.class);
        verify(requestRepository, never()).save(any());
        verify(itemRepository, never()).saveAll(anyList());
    }

    @Test
    void throwsWhenCustomerDoesNotExist() {
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generateRecommendations(CUSTOMER_ID,
                new RecommendationRequest(null, "Protection", null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(aiClient);
    }

    private static InsuranceProduct product(Long id, String code, String name, String type, int termMonths,
            int minAge, int maxAge) {
        InsuranceProduct product = new InsuranceProduct();
        ReflectionTestUtils.setField(product, "id", id);
        product.setProductCode(code);
        product.setPolicyName(name);
        product.setPolicyType(type);
        product.setTermMonths(termMonths);
        product.setPremiumFrequency("MONTHLY");
        product.setPremiumAmount(new BigDecimal("1000.00"));
        product.setSumInsured(new BigDecimal("1000000.00"));
        product.setMinEntryAge(minAge);
        product.setMaxEntryAge(maxAge);
        product.setActive(true);
        return product;
    }
}
