package com.insuranceapp.backend;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.insuranceapp.backend.entity.AiRecommendationItem;
import com.insuranceapp.backend.entity.AiRecommendationRequest;
import com.insuranceapp.backend.entity.Customer;
import com.insuranceapp.backend.entity.CustomerPolicy;
import com.insuranceapp.backend.entity.InsuranceProduct;
import com.insuranceapp.backend.entity.PremiumInstallment;
import com.insuranceapp.backend.entity.PremiumPayment;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;

@SpringBootTest
@Transactional
class EntitySchemaTests {
    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void createsDocumentedTablesAndPersistsRelationships() {
        Set<String> tables = Set.copyOf(jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = current_schema() and table_type = 'BASE TABLE'",
                String.class));
        assertTrue(tables.containsAll(Set.of("customer", "insurance_product", "customer_policy", "premium_installment",
                "premium_payment", "ai_recommendation_request", "ai_recommendation_item")));

        Customer customer = customer();
        InsuranceProduct product = product();
        CustomerPolicy policy = policy(customer, product);

        PremiumInstallment installment = new PremiumInstallment();
        installment.setPolicy(policy);
        installment.setInstallmentNumber(1);
        installment.setDueDate(LocalDate.of(2026, 1, 1));
        installment.setGracePeriodEndDate(LocalDate.of(2026, 2, 1));
        installment.setScheduledAmount(new BigDecimal("100.00"));
        installment.setPaidAmount(new BigDecimal("50.00"));
        installment.setPaymentStatus("PARTIALLY_PAID");
        entityManager.persist(installment);

        PremiumPayment payment = new PremiumPayment();
        payment.setInstallment(installment);
        payment.setTransactionReference("TEST-TXN-1");
        payment.setPaymentDate(LocalDate.of(2026, 1, 1));
        payment.setAmountPaid(new BigDecimal("50.00"));
        payment.setPenaltyAmount(BigDecimal.ZERO);
        payment.setPaymentMethod("UPI");
        entityManager.persist(payment);

        AiRecommendationRequest request = new AiRecommendationRequest();
        request.setCustomer(customer);
        request.setCalculatedAge(36);
        request.setModelName("test-model");
        request.setPromptVersion("v1");
        entityManager.persist(request);

        AiRecommendationItem item = new AiRecommendationItem();
        item.setRecommendationRequest(request);
        item.setProduct(product);
        item.setRankOrder(1);
        item.setRecommendationReason("Test reason");
        item.setSuitability("HIGH");
        entityManager.persist(item);
        entityManager.flush();

        assertNotNull(customer.getCreatedAt());
        assertNotNull(customer.getUpdatedAt());
        assertNotNull(policy.getCreatedAt());
        assertNotNull(payment.getCreatedAt());
        assertNotNull(request.getRequestedAt());
        assertNotNull(item.getId());
    }

    @Test
    void rejectsDuplicateCustomerProductPolicies() {
        Customer customer = customer();
        InsuranceProduct product = product();
        policy(customer, product);
        assertThrows(PersistenceException.class, () -> policy(customer, product));
    }

    @Test
    void rejectsFutureDateOfBirth() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into customer (email, password_hash, full_name, date_of_birth, status, created_at, updated_at) values (?, ?, ?, current_date + 1, ?, current_timestamp, current_timestamp)",
                "future@example.com", "hash", "Future", "ACTIVE"));
    }

    @Test
    void rejectsNonpositiveProductPremium() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into insurance_product (product_code, policy_name, policy_type, term_months, premium_frequency, premium_amount, sum_insured, min_entry_age, max_entry_age, active) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "TEST", "Test", "HEALTH", 12, "YEARLY", BigDecimal.ZERO, BigDecimal.TEN, 18, 65, true));
    }

    private Customer customer() {
        Customer customer = new Customer();
        customer.setEmail("test@example.com");
        customer.setPasswordHash("test-hash");
        customer.setFullName("Test Customer");
        customer.setDateOfBirth(LocalDate.of(1990, 1, 1));
        customer.setStatus("ACTIVE");
        entityManager.persist(customer);
        return customer;
    }

    private InsuranceProduct product() {
        InsuranceProduct product = new InsuranceProduct();
        product.setProductCode("TEST-PRODUCT");
        product.setPolicyName("Test Product");
        product.setPolicyType("HEALTH");
        product.setTermMonths(12);
        product.setPremiumFrequency("YEARLY");
        product.setPremiumAmount(new BigDecimal("100.00"));
        product.setSumInsured(new BigDecimal("1000.00"));
        product.setMinEntryAge(18);
        product.setMaxEntryAge(65);
        product.setActive(true);
        entityManager.persist(product);
        return product;
    }

    private CustomerPolicy policy(Customer customer, InsuranceProduct product) {
        CustomerPolicy policy = new CustomerPolicy();
        policy.setPolicyNumber("TEST-POLICY-" + java.util.UUID.randomUUID());
        policy.setCustomer(customer);
        policy.setProduct(product);
        policy.setStartDate(LocalDate.of(2026, 1, 1));
        policy.setMaturityDate(LocalDate.of(2026, 12, 31));
        policy.setPremiumFrequency("YEARLY");
        policy.setPremiumAmount(new BigDecimal("100.00"));
        policy.setSumInsured(new BigDecimal("1000.00"));
        policy.setTotalPremiumPayable(new BigDecimal("100.00"));
        policy.setStatus("ACTIVE");
        entityManager.persist(policy);
        return policy;
    }
}
