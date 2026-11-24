package com.insuranceapp.backend;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
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
import com.insuranceapp.backend.repository.CustomerPolicyRepository;
import com.insuranceapp.backend.repository.CustomerRepository;
import com.insuranceapp.backend.repository.InsuranceProductRepository;

/** FR-1B/FR-1C against PostgreSQL. Each test runs in a transaction that is rolled back. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DashboardIntegrationTest {
    private static final String PASSWORD = "Gokul@123";

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
    private PasswordEncoder passwordEncoder;

    private InsuranceProduct termProduct;

    @BeforeEach
    void setUp() {
        termProduct = productRepository.findByActiveTrueOrderByProductCodeAsc().stream()
                .filter(product -> product.getProductCode().equals("HD-TERM-001"))
                .findFirst().orElseThrow();
    }

    @Test
    void newCustomerSeesNoPoliciesAndAllActiveProducts() throws Exception {
        createCustomer("gokul@gmail.com", "Gokul");
        String token = login("gokul@gmail.com");

        mockMvc.perform(get("/api/policies/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/products/available").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].productCode", hasItems("HD-TERM-001", "HD-HEALTH-001", "HD-SAV-001")));
    }

    @Test
    void registeredPolicyAppearsOnDashboardAndProductIsExcluded() throws Exception {
        Customer gokul = createCustomer("gokul@gmail.com", "Gokul");
        createPolicy(gokul, "HDP-TEST-0001");
        String token = login("gokul@gmail.com");

        mockMvc.perform(get("/api/policies/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].policyNumber").value("HDP-TEST-0001"))
                .andExpect(jsonPath("$[0].policyName").value("HealthDekho Secure Term"))
                .andExpect(jsonPath("$[0].termMonths").value(240))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));

        mockMvc.perform(get("/api/products/available").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].productCode", not(hasItem("HD-TERM-001"))))
                .andExpect(jsonPath("$[*].productCode", hasItems("HD-HEALTH-001", "HD-SAV-001")));
    }

    @Test
    void customerCannotSeeAnotherCustomersPolicies() throws Exception {
        Customer gokul = createCustomer("gokul@gmail.com", "Gokul");
        createPolicy(gokul, "HDP-TEST-0002");
        createCustomer("yuvaraj@gmail.com", "Yuvaraj");
        String yuvarajToken = login("yuvaraj@gmail.com");

        mockMvc.perform(get("/api/policies/me").header("Authorization", "Bearer " + yuvarajToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/products/available").header("Authorization", "Bearer " + yuvarajToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].productCode", hasItem("HD-TERM-001")));
    }

    private Customer createCustomer(String email, String name) {
        Customer customer = new Customer();
        customer.setEmail(email);
        customer.setFullName(name);
        customer.setDateOfBirth(LocalDate.of(1992, 6, 1));
        customer.setPhone("9000000000");
        customer.setStatus(CustomerStatus.ACTIVE);
        customer.setPasswordHash(passwordEncoder.encode(PASSWORD));
        return customerRepository.saveAndFlush(customer);
    }

    private void createPolicy(Customer customer, String policyNumber) {
        CustomerPolicy policy = new CustomerPolicy();
        policy.setPolicyNumber(policyNumber);
        policy.setCustomer(customer);
        policy.setProduct(termProduct);
        policy.setStartDate(LocalDate.of(2026, 11, 20));
        policy.setMaturityDate(LocalDate.of(2046, 11, 19));
        policy.setPremiumFrequency("MONTHLY");
        policy.setPremiumAmount(new BigDecimal("1250.00"));
        policy.setSumInsured(new BigDecimal("5000000.00"));
        policy.setTotalPremiumPayable(new BigDecimal("300000.00"));
        policy.setStatus("ACTIVE");
        policyRepository.saveAndFlush(policy);
    }

    private String login(String email) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }
}
