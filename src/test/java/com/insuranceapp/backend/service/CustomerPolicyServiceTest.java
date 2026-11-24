package com.insuranceapp.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.insuranceapp.backend.dto.CustomerPolicyResponse;
import com.insuranceapp.backend.entity.CustomerPolicy;
import com.insuranceapp.backend.entity.InsuranceProduct;
import com.insuranceapp.backend.repository.CustomerPolicyRepository;

@ExtendWith(MockitoExtension.class)
class CustomerPolicyServiceTest {
    @Mock
    private CustomerPolicyRepository customerPolicyRepository;

    @InjectMocks
    private CustomerPolicyService service;

    @Test
    void mapsCustomerPoliciesToDashboardRows() {
        InsuranceProduct product = new InsuranceProduct();
        ReflectionTestUtils.setField(product, "id", 201L);
        product.setPolicyName("HealthDekho Secure Term");
        product.setPolicyType("TERM");
        product.setTermMonths(240);

        CustomerPolicy policy = new CustomerPolicy();
        policy.setPolicyNumber("HDP-2026-0001");
        policy.setProduct(product);
        policy.setStartDate(LocalDate.of(2026, 11, 20));
        policy.setMaturityDate(LocalDate.of(2046, 11, 19));
        policy.setPremiumFrequency("MONTHLY");
        policy.setPremiumAmount(new BigDecimal("1250.00"));
        policy.setSumInsured(new BigDecimal("5000000.00"));
        policy.setTotalPremiumPayable(new BigDecimal("300000.00"));
        policy.setStatus("ACTIVE");
        when(customerPolicyRepository.findAllWithProductByCustomerId(7L)).thenReturn(List.of(policy));

        List<CustomerPolicyResponse> rows = service.getPoliciesForCustomer(7L);

        assertThat(rows).containsExactly(new CustomerPolicyResponse("HDP-2026-0001", "HealthDekho Secure Term",
                "TERM", 240, LocalDate.of(2026, 11, 20), LocalDate.of(2046, 11, 19), "MONTHLY",
                new BigDecimal("1250.00"), new BigDecimal("5000000.00"), "ACTIVE"));
    }

    @Test
    void returnsEmptyListWhenCustomerHasNoPolicies() {
        when(customerPolicyRepository.findAllWithProductByCustomerId(7L)).thenReturn(List.of());

        assertThat(service.getPoliciesForCustomer(7L)).isEmpty();
    }
}
