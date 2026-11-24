package com.insuranceapp.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insuranceapp.backend.entity.Customer;
import com.insuranceapp.backend.entity.CustomerPolicy;
import com.insuranceapp.backend.entity.CustomerStatus;
import com.insuranceapp.backend.entity.InsuranceProduct;
import com.insuranceapp.backend.entity.PremiumInstallment;
import com.insuranceapp.backend.entity.PremiumPayment;
import com.insuranceapp.backend.repository.CustomerPolicyRepository;
import com.insuranceapp.backend.repository.CustomerRepository;
import com.insuranceapp.backend.repository.InsuranceProductRepository;
import com.insuranceapp.backend.repository.PremiumInstallmentRepository;
import com.insuranceapp.backend.repository.PremiumPaymentRepository;

/** FR-4 against PostgreSQL. Each test is rolled back. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PremiumPaymentIntegrationTest {
    private static final String EMAIL = "gokul@gmail.com";
    private static final String PASSWORD = "Gokul@123";
    private static final String POLICY_NUMBER = "HDP-TEST-0400";

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
    private PremiumPaymentRepository paymentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void paysInstallmentsWithPenaltyAndMarksPolicyFullyPaid() throws Exception {
        LocalDate today = LocalDate.now();
        CustomerPolicy policy = createPolicyWithInstallments(today);
        List<PremiumInstallment> installments = installmentRepository
                .findByPolicyIdOrderByInstallmentNumberAsc(policy.getId());
        Long overdueId = installments.get(0).getId();
        Long currentId = installments.get(1).getId();
        Long futureId = installments.get(2).getId();
        String token = login();

        mockMvc.perform(get("/api/policies/" + POLICY_NUMBER.toLowerCase() + "/premiums")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policy.customerName").value("Gokul"))
                .andExpect(jsonPath("$.policy.totalPremiumPayable").value(3750.00))
                .andExpect(jsonPath("$.policy.totalPremiumPaid").value(0))
                .andExpect(jsonPath("$.installments[0].paymentStatus").value("OVERDUE"))
                .andExpect(jsonPath("$.installments[0].amountDue").value(1375.00))
                .andExpect(jsonPath("$.installments[1].paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$.payments.length()").value(0));

        pay(token, "UPI", Map.of("installmentId", overdueId, "amount", 1375.00),
                Map.of("installmentId", currentId, "amount", 500.00))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.receipts[0].penaltyAmount").value(125.00))
                .andExpect(jsonPath("$.receipts[0].paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.receipts[1].paymentStatus").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.receipts[1].remainingOutstanding").value(750.00))
                .andExpect(jsonPath("$.policyStatus").value("ACTIVE"));

        PremiumInstallment overdue = installmentRepository.findById(overdueId).orElseThrow();
        assertThat(overdue.getPaidAmount()).isEqualByComparingTo("1375.00");
        assertThat(overdue.getPaymentStatus()).isEqualTo("PAID");
        List<PremiumPayment> payments = paymentRepository.findAllByPolicyId(policy.getId());
        assertThat(payments).hasSize(2).allSatisfy(payment -> {
            assertThat(payment.getTransactionReference()).matches("TXN-HD-\\d{6}");
            assertThat(payment.getPaymentDate()).isEqualTo(today);
            assertThat(payment.getPaymentMethod()).isEqualTo("UPI");
        });

        pay(token, "CARD", Map.of("installmentId", overdueId, "amount", 1.00))
                .andExpect(status().isConflict());
        pay(token, "CARD", Map.of("installmentId", currentId, "amount", 750.01))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(get("/api/policies/" + POLICY_NUMBER + "/premiums").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.policy.totalPremiumPaid").value(1750.00))
                .andExpect(jsonPath("$.policy.totalRemainingPremium").value(2000.00))
                .andExpect(jsonPath("$.policy.totalPenaltyPaid").value(125.00))
                .andExpect(jsonPath("$.payments.length()").value(2));

        pay(token, "NET_BANKING", Map.of("installmentId", currentId, "amount", 750.00),
                Map.of("installmentId", futureId, "amount", 1250.00))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.policyStatus").value("FULLY_PAID"));
        assertThat(policyRepository.findById(policy.getId()).orElseThrow().getStatus()).isEqualTo("FULLY_PAID");
    }

    @Test
    void doesNotExposePoliciesOfOtherCustomers() throws Exception {
        createCustomer();
        String token = login();

        mockMvc.perform(get("/api/policies/HDP-DOES-NOT-EXIST/premiums").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    private ResultActions pay(String token, String method, Map<?, ?>... payments) throws Exception {
        return mockMvc.perform(post("/api/policies/" + POLICY_NUMBER + "/premium-payments")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("paymentMethod", method, "payments", List.of(payments)))));
    }

    private CustomerPolicy createPolicyWithInstallments(LocalDate today) {
        Customer customer = createCustomer();
        InsuranceProduct product = productRepository.findByActiveTrueOrderByProductCodeAsc().stream()
                .filter(p -> p.getProductCode().equals("HD-TERM-001")).findFirst().orElseThrow();
        LocalDate start = today.minusMonths(2);
        CustomerPolicy policy = new CustomerPolicy();
        policy.setPolicyNumber(POLICY_NUMBER);
        policy.setCustomer(customer);
        policy.setProduct(product);
        policy.setStartDate(start);
        policy.setMaturityDate(start.plusMonths(3).minusDays(1));
        policy.setPremiumFrequency("MONTHLY");
        policy.setPremiumAmount(new BigDecimal("1250.00"));
        policy.setSumInsured(new BigDecimal("5000000.00"));
        policy.setTotalPremiumPayable(new BigDecimal("3750.00"));
        policy.setStatus("ACTIVE");
        policyRepository.saveAndFlush(policy);
        // Installment 1 is past its grace period; installment 2 is within it; installment 3 is in the future.
        List<LocalDate> dueDates = List.of(today.minusMonths(2), today, today.plusMonths(1));
        for (int i = 0; i < dueDates.size(); i++) {
            PremiumInstallment installment = new PremiumInstallment();
            installment.setPolicy(policy);
            installment.setInstallmentNumber(i + 1);
            installment.setDueDate(dueDates.get(i));
            installment.setGracePeriodEndDate(dueDates.get(i).plusMonths(1));
            installment.setScheduledAmount(new BigDecimal("1250.00"));
            installment.setPaidAmount(new BigDecimal("0.00"));
            installment.setPaymentStatus("PENDING");
            installmentRepository.save(installment);
        }
        installmentRepository.flush();
        return policy;
    }

    private Customer createCustomer() {
        Customer customer = new Customer();
        customer.setEmail(EMAIL);
        customer.setFullName("Gokul");
        customer.setDateOfBirth(LocalDate.of(1990, 4, 15));
        customer.setPhone("9000000002");
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