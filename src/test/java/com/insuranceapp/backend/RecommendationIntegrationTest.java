package com.insuranceapp.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insuranceapp.backend.ai.AiRecommendationOutcome;
import com.insuranceapp.backend.ai.AiSuggestion;
import com.insuranceapp.backend.ai.InsuranceRecommendationAiClient;
import com.insuranceapp.backend.ai.ProductCandidate;
import com.insuranceapp.backend.ai.RecommendationPromptInput;
import com.insuranceapp.backend.entity.AiRecommendationItem;
import com.insuranceapp.backend.entity.AiRecommendationRequest;
import com.insuranceapp.backend.entity.Customer;
import com.insuranceapp.backend.entity.CustomerPolicy;
import com.insuranceapp.backend.entity.CustomerStatus;
import com.insuranceapp.backend.entity.InsuranceProduct;
import com.insuranceapp.backend.repository.AiRecommendationItemRepository;
import com.insuranceapp.backend.repository.AiRecommendationRequestRepository;
import com.insuranceapp.backend.repository.CustomerPolicyRepository;
import com.insuranceapp.backend.repository.CustomerRepository;
import com.insuranceapp.backend.repository.InsuranceProductRepository;

/** FR-2 against PostgreSQL with the AI model mocked. Each test is rolled back. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RecommendationIntegrationTest {
    private static final String PASSWORD = "Yuvaraj@123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private InsuranceProductRepository productRepository;

    @Autowired
    private CustomerPolicyRepository policyRepository;

    @Autowired
    private AiRecommendationRequestRepository requestRepository;

    @Autowired
    private AiRecommendationItemRepository itemRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private InsuranceRecommendationAiClient aiClient;

    @Test
    void persistsValidatedRecommendationsAgainstTheCustomer() throws Exception {
        Customer yuvaraj = createCustomer();
        InsuranceProduct term = productRepository.findByActiveTrueOrderByProductCodeAsc().stream()
                .filter(p -> p.getProductCode().equals("HD-TERM-001")).findFirst().orElseThrow();
        createPolicy(yuvaraj, term);
        when(aiClient.recommend(any())).thenReturn(new AiRecommendationOutcome(List.of(
                new AiSuggestion("HD-TERM-001", "Already registered", "HIGH"),
                new AiSuggestion("HD-NOT-REAL", "Hallucinated product", "HIGH"),
                new AiSuggestion("HD-HEALTH-001", "Annual family health cover matches stated preference", "HIGH")),
                "claude-haiku-4-5"));
        String token = login();

        String body = mockMvc.perform(post("/api/recommendations").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "insuranceGoals", "Health coverage for self and parents",
                        "preferredTerm", "1 year",
                        "preferredPolicyType", "HEALTH"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.calculatedAge").value(34))
                .andExpect(jsonPath("$.recommendations.length()").value(1))
                .andExpect(jsonPath("$.recommendations[0].productCode").value("HD-HEALTH-001"))
                .andExpect(jsonPath("$.recommendations[0].policyName").value("HealthDekho Family Health"))
                .andExpect(jsonPath("$.recommendations[0].rankOrder").value(1))
                .andExpect(jsonPath("$.recommendations[0].suitability").value("HIGH"))
                .andReturn().getResponse().getContentAsString();

        ArgumentCaptor<RecommendationPromptInput> captor = ArgumentCaptor.forClass(RecommendationPromptInput.class);
        verify(aiClient).recommend(captor.capture());
        assertThat(captor.getValue().customerAge()).isEqualTo(34);
        assertThat(captor.getValue().availableProducts()).extracting(ProductCandidate::productCode)
                .contains("HD-HEALTH-001", "HD-SAV-001")
                .doesNotContain("HD-TERM-001");

        JsonNode json = objectMapper.readTree(body);
        AiRecommendationRequest saved = requestRepository.findById(json.get("recommendationRequestId").asLong())
                .orElseThrow();
        assertThat(saved.getCustomer().getId()).isEqualTo(yuvaraj.getId());
        assertThat(saved.getInsuranceGoals()).isEqualTo("Health coverage for self and parents");
        assertThat(saved.getPreferredTerm()).isEqualTo("1 year");
        assertThat(saved.getPreferredPolicyType()).isEqualTo("HEALTH");
        assertThat(saved.getCalculatedAge()).isEqualTo(34);
        assertThat(saved.getModelName()).isNotBlank();
        assertThat(saved.getModelVersion()).isEqualTo("claude-haiku-4-5");
        assertThat(saved.getPromptVersion()).isEqualTo("rec-v1");
        assertThat(saved.getRequestedAt()).isNotNull();

        AiRecommendationItem item = itemRepository
                .findById(json.at("/recommendations/0/recommendationItemId").asLong()).orElseThrow();
        assertThat(item.getRecommendationRequest().getId()).isEqualTo(saved.getId());
        assertThat(item.getProduct().getProductCode()).isEqualTo("HD-HEALTH-001");
        assertThat(item.getRecommendationReason()).isEqualTo("Annual family health cover matches stated preference");
        assertThat(item.isSelectedForRegistration()).isFalse();
    }

    private Customer createCustomer() {
        Customer customer = new Customer();
        customer.setEmail("yuvaraj@gmail.com");
        customer.setFullName("Yuvaraj");
        customer.setDateOfBirth(LocalDate.now().minusYears(34).minusDays(10));
        customer.setPhone("9000000001");
        customer.setStatus(CustomerStatus.ACTIVE);
        customer.setPasswordHash(passwordEncoder.encode(PASSWORD));
        return customerRepository.saveAndFlush(customer);
    }

    private void createPolicy(Customer customer, InsuranceProduct product) {
        CustomerPolicy policy = new CustomerPolicy();
        policy.setPolicyNumber("HDP-TEST-0100");
        policy.setCustomer(customer);
        policy.setProduct(product);
        policy.setStartDate(LocalDate.of(2026, 11, 20));
        policy.setMaturityDate(LocalDate.of(2046, 11, 19));
        policy.setPremiumFrequency("MONTHLY");
        policy.setPremiumAmount(new BigDecimal("1250.00"));
        policy.setSumInsured(new BigDecimal("5000000.00"));
        policy.setTotalPremiumPayable(new BigDecimal("300000.00"));
        policy.setStatus("ACTIVE");
        policyRepository.saveAndFlush(policy);
    }

    private String login() throws Exception {
        String response = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", "yuvaraj@gmail.com", "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }
}
