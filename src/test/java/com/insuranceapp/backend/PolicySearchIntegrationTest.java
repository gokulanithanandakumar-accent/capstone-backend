package com.insuranceapp.backend;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
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

/** FR-5 against PostgreSQL. Each test is rolled back. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PolicySearchIntegrationTest {
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
    private PremiumInstallmentRepository installmentRepository;

    @Autowired
    private PremiumPaymentRepository paymentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        LocalDate today = LocalDate.now();
        Customer yuvaraj = customer("yuvaraj@gmail.com", "Yuvaraj", LocalDate.of(1992, 6, 10), "9000000003");
        Customer goutham = customer("goutham@gmail.com", "Goutham", LocalDate.of(1988, 3, 5), "9000000004");

        // Term policy: installment 1 overdue, installment 2 paid late with a 125.00 penalty, installment 3 upcoming.
        CustomerPolicy term = policy(yuvaraj, "HD-TERM-001", "HDP-TEST-0501", today.minusMonths(3), "1250.00", 3);
        installment(term, 1, today.minusMonths(3), "1250.00", "0.00", "PENDING");
        PremiumInstallment paidLate = installment(term, 2, today.minusMonths(2), "1250.00", "1375.00", "PAID");
        installment(term, 3, today.plusMonths(1), "1250.00", "0.00", "PENDING");
        payment(paidLate, "TXN-IT-000501", "1375.00", "125.00");

        // Savings policy started today: installment 1 partly paid within its grace period.
        CustomerPolicy savings = policy(yuvaraj, "HD-SAV-001", "HDP-TEST-0502", today, "7500.00", 2);
        PremiumInstallment partial = installment(savings, 1, today, "7500.00", "500.00", "PARTIALLY_PAID");
        installment(savings, 2, today.plusMonths(3), "7500.00", "0.00", "PENDING");
        payment(partial, "TXN-IT-000502", "500.00", "0.00");

        policy(goutham, "HD-TERM-001", "HDP-TEST-0503", today, "1250.00", 3);
        installmentRepository.flush();
        paymentRepository.flush();
        token = login("yuvaraj@gmail.com");
    }

    @Test
    void searchesOwnPoliciesByCustomerNameWithPagination() throws Exception {
        mockMvc.perform(search().param("customerName", "  yuva "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.results[0].policyNumber").value("HDP-TEST-0502"))
                .andExpect(jsonPath("$.results[0].policyType").value("SAVINGS"))
                .andExpect(jsonPath("$.results[0].premiumPaymentStatus").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.results[0].totalPremiumPayable").value(15000.00))
                .andExpect(jsonPath("$.results[0].totalPremiumPaid").value(500.00))
                .andExpect(jsonPath("$.results[0].remainingPremium").value(14500.00))
                .andExpect(jsonPath("$.results[1].policyNumber").value("HDP-TEST-0501"))
                .andExpect(jsonPath("$.results[1].customerName").value("Yuvaraj"))
                .andExpect(jsonPath("$.results[1].dateOfBirth").value("1992-06-10"))
                .andExpect(jsonPath("$.results[1].premiumPaymentStatus").value("OVERDUE"))
                .andExpect(jsonPath("$.results[1].totalPremiumPaid").value(1250.00))
                .andExpect(jsonPath("$.results[1].remainingPremium").value(2500.00));

        mockMvc.perform(search().param("customerName", "Yuvaraj").param("page", "1").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.results[0].policyNumber").value("HDP-TEST-0501"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void searchesByPolicyIdentifierAndDateOfBirth() throws Exception {
        mockMvc.perform(search().param("policyNumber", "hdp-test-0501"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.results[0].policyNumber").value("HDP-TEST-0501"));
        mockMvc.perform(search().param("policyNumber", "HDP-TEST-0501").param("dateOfBirth", "1992-06-10"))
                .andExpect(jsonPath("$.results.length()").value(1));
        mockMvc.perform(search().param("policyNumber", "HDP-TEST-0501").param("dateOfBirth", "1992-06-11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void neverReturnsOtherCustomersPolicies() throws Exception {
        mockMvc.perform(search().param("customerName", "Goutham"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(0));
        mockMvc.perform(search().param("policyNumber", "HDP-TEST-0503"))
                .andExpect(jsonPath("$.results.length()").value(0));
    }

    @Test
    void rejectsInvalidCriteria() throws Exception {
        mockMvc.perform(search().param("customerName", "Yuvaraj").param("policyNumber", "HDP-TEST-0501"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(search().param("dateOfBirth", "1992-06-10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Date of Birth can only be used together with Policy Identifier."));
    }

    private MockHttpServletRequestBuilder search() {
        return get("/api/policies/search").header("Authorization", "Bearer " + token);
    }

    private Customer customer(String email, String name, LocalDate dateOfBirth, String phone) {
        Customer customer = new Customer();
        customer.setEmail(email);
        customer.setFullName(name);
        customer.setDateOfBirth(dateOfBirth);
        customer.setPhone(phone);
        customer.setStatus(CustomerStatus.ACTIVE);
        customer.setPasswordHash(passwordEncoder.encode(PASSWORD));
        return customerRepository.saveAndFlush(customer);
    }

    private CustomerPolicy policy(Customer customer, String productCode, String number, LocalDate start,
            String premium, int installments) {
        InsuranceProduct product = productRepository.findByActiveTrueOrderByProductCodeAsc().stream()
                .filter(p -> p.getProductCode().equals(productCode)).findFirst().orElseThrow();
        BigDecimal premiumAmount = new BigDecimal(premium);
        CustomerPolicy policy = new CustomerPolicy();
        policy.setPolicyNumber(number);
        policy.setCustomer(customer);
        policy.setProduct(product);
        policy.setStartDate(start);
        policy.setMaturityDate(start.plusMonths(product.getTermMonths()).minusDays(1));
        policy.setPremiumFrequency(product.getPremiumFrequency());
        policy.setPremiumAmount(premiumAmount);
        policy.setSumInsured(product.getSumInsured());
        policy.setTotalPremiumPayable(premiumAmount.multiply(BigDecimal.valueOf(installments)));
        policy.setStatus("ACTIVE");
        return policyRepository.saveAndFlush(policy);
    }

    private PremiumInstallment installment(CustomerPolicy policy, int number, LocalDate dueDate, String scheduled,
            String paid, String status) {
        PremiumInstallment installment = new PremiumInstallment();
        installment.setPolicy(policy);
        installment.setInstallmentNumber(number);
        installment.setDueDate(dueDate);
        installment.setGracePeriodEndDate(dueDate.plusMonths(1));
        installment.setScheduledAmount(new BigDecimal(scheduled));
        installment.setPaidAmount(new BigDecimal(paid));
        installment.setPaymentStatus(status);
        return installmentRepository.save(installment);
    }

    private void payment(PremiumInstallment installment, String reference, String amount, String penalty) {
        PremiumPayment payment = new PremiumPayment();
        payment.setInstallment(installment);
        payment.setTransactionReference(reference);
        payment.setPaymentDate(LocalDate.now());
        payment.setAmountPaid(new BigDecimal(amount));
        payment.setPenaltyAmount(new BigDecimal(penalty));
        payment.setPaymentMethod("UPI");
        paymentRepository.save(payment);
    }

    private String login(String email) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }
}
