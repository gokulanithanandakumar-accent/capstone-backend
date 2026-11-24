package com.insuranceapp.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insuranceapp.backend.entity.Customer;
import com.insuranceapp.backend.entity.CustomerPolicy;
import com.insuranceapp.backend.entity.CustomerStatus;
import com.insuranceapp.backend.entity.InsuranceProduct;
import com.insuranceapp.backend.entity.PremiumInstallment;
import com.insuranceapp.backend.repository.CustomerPolicyRepository;
import com.insuranceapp.backend.repository.CustomerRepository;
import com.insuranceapp.backend.repository.InsuranceProductRepository;
import com.insuranceapp.backend.repository.PremiumInstallmentRepository;

/** FR-3 against PostgreSQL. Each test is rolled back. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PolicyRegistrationIntegrationTest {
    private static final String EMAIL = "goutham@gmail.com";
    private static final String PASSWORD = "Goutham@123";

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
    private PremiumInstallmentRepository installmentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void registersPolicyWithInstallmentScheduleAndRejectsDuplicate() throws Exception {
        Customer goutham = createCustomer(LocalDate.now().minusYears(30));
        InsuranceProduct savings = product("HD-SAV-001");
        LocalDate start = LocalDate.now();
        String token = login();
        String body = objectMapper.writeValueAsString(Map.of("productId", savings.getId(), "startDate", start.toString()));

        mockMvc.perform(get("/api/policies/registration-details").header("Authorization", "Bearer " + token)
                .param("productId", savings.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerName").value("Goutham"))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.startDate").value(start.toString()))
                .andExpect(jsonPath("$.maturityDate").value(start.plusMonths(120).minusDays(1).toString()))
                .andExpect(jsonPath("$.totalPremiumPayable").value(300000.00))
                .andExpect(jsonPath("$.numberOfInstallments").value(40));

        String response = mockMvc.perform(post("/api/policies").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.policyNumber").value(org.hamcrest.Matchers.startsWith("HDP-" + start.getYear() + "-")))
                .andExpect(jsonPath("$.numberOfInstallments").value(40))
                .andReturn().getResponse().getContentAsString();

        String policyNumber = objectMapper.readTree(response).get("policyNumber").asText();
        CustomerPolicy policy = policyRepository.findAll().stream()
                .filter(p -> p.getPolicyNumber().equals(policyNumber)).findFirst().orElseThrow();
        assertThat(policy.getCustomer().getId()).isEqualTo(goutham.getId());
        assertThat(policy.getStatus()).isEqualTo("ACTIVE");
        assertThat(policy.getPremiumFrequency()).isEqualTo("QUARTERLY");
        assertThat(policy.getTotalPremiumPayable()).isEqualByComparingTo("300000.00");

        List<PremiumInstallment> installments = installmentRepository
                .findByPolicyIdOrderByInstallmentNumberAsc(policy.getId());
        assertThat(installments).hasSize(40);
        assertThat(installments.get(0).getDueDate()).isEqualTo(start);
        assertThat(installments.get(0).getGracePeriodEndDate()).isEqualTo(start.plusMonths(1));
        assertThat(installments.get(1).getDueDate()).isEqualTo(start.plusMonths(3));
        assertThat(installments).allSatisfy(i -> {
            assertThat(i.getScheduledAmount()).isEqualByComparingTo("7500.00");
            assertThat(i.getPaymentStatus()).isEqualTo("PENDING");
        });

        mockMvc.perform(post("/api/policies").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("You have already registered a policy for HealthDekho Smart Savings."));

        mockMvc.perform(get("/api/policies/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].policyNumber").value(policyNumber));
    }

    @Test
    void rejectsCustomerOutsideEntryAgeRange() throws Exception {
        createCustomer(LocalDate.now().minusYears(19));
        InsuranceProduct savings = product("HD-SAV-001");
        String token = login();

        mockMvc.perform(post("/api/policies").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        Map.of("productId", savings.getId(), "startDate", LocalDate.now().toString()))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("between 21 and 55")));
        assertThat(policyRepository.existsByCustomerIdAndProductId(
                customerRepository.findByEmailIgnoreCase(EMAIL).orElseThrow().getId(), savings.getId())).isFalse();
    }

    private InsuranceProduct product(String code) {
        return productRepository.findByActiveTrueOrderByProductCodeAsc().stream()
                .filter(p -> p.getProductCode().equals(code)).findFirst().orElseThrow();
    }

    private Customer createCustomer(LocalDate dateOfBirth) {
        Customer customer = new Customer();
        customer.setEmail(EMAIL);
        customer.setFullName("Goutham");
        customer.setDateOfBirth(dateOfBirth);
        customer.setPhone("9000000003");
        customer.setStatus(CustomerStatus.ACTIVE);
        customer.setPasswordHash(passwordEncoder.encode(PASSWORD));
        return customerRepository.saveAndFlush(customer);
    }

    private String login() throws Exception {
        String response = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", EMAIL, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }
}
