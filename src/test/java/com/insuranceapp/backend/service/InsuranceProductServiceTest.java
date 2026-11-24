package com.insuranceapp.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.insuranceapp.backend.dto.AvailableProductResponse;
import com.insuranceapp.backend.entity.CustomerPolicy;
import com.insuranceapp.backend.entity.InsuranceProduct;
import com.insuranceapp.backend.repository.CustomerPolicyRepository;
import com.insuranceapp.backend.repository.InsuranceProductRepository;

@ExtendWith(MockitoExtension.class)
class InsuranceProductServiceTest {
    @Mock
    private InsuranceProductRepository productRepository;

    @Mock
    private CustomerPolicyRepository customerPolicyRepository;

    @InjectMocks
    private InsuranceProductService service;

    private final InsuranceProductTestData data = new InsuranceProductTestData();

    @Test
    void excludesProductsTheCustomerAlreadyRegistered() {
        when(productRepository.findByActiveTrueOrderByProductCodeAsc())
                .thenReturn(List.of(data.health, data.savings, data.term));
        when(customerPolicyRepository.findAllWithProductByCustomerId(7L))
                .thenReturn(List.of(policyFor(data.term, "ACTIVE")));

        List<AvailableProductResponse> available = service.getAvailableProducts(7L);

        assertThat(available).extracting(AvailableProductResponse::productCode)
                .containsExactly("HD-HEALTH-001", "HD-SAV-001");
    }

    @Test
    void excludesRegisteredProductRegardlessOfPolicyStatus() {
        when(productRepository.findByActiveTrueOrderByProductCodeAsc()).thenReturn(List.of(data.term, data.health));
        when(customerPolicyRepository.findAllWithProductByCustomerId(7L))
                .thenReturn(List.of(policyFor(data.term, "LAPSED")));

        assertThat(service.getAvailableProducts(7L)).extracting(AvailableProductResponse::productCode)
                .containsExactly("HD-HEALTH-001");
    }

    @Test
    void returnsAllActiveProductsWithFullDetailsWhenNoPolicies() {
        when(productRepository.findByActiveTrueOrderByProductCodeAsc()).thenReturn(List.of(data.term));
        when(customerPolicyRepository.findAllWithProductByCustomerId(7L)).thenReturn(List.of());

        assertThat(service.getAvailableProducts(7L)).containsExactly(new AvailableProductResponse(201L,
                "HD-TERM-001", "HealthDekho Secure Term", "TERM", 240, new BigDecimal("5000000.00"), "MONTHLY",
                new BigDecimal("1250.00")));
    }

    @Test
    void returnsEmptyWhenCustomerRegisteredEveryActiveProduct() {
        when(productRepository.findByActiveTrueOrderByProductCodeAsc()).thenReturn(List.of(data.term));
        when(customerPolicyRepository.findAllWithProductByCustomerId(7L))
                .thenReturn(List.of(policyFor(data.term, "ACTIVE")));

        assertThat(service.getAvailableProducts(7L)).isEmpty();
    }

    private static CustomerPolicy policyFor(InsuranceProduct product, String status) {
        CustomerPolicy policy = new CustomerPolicy();
        policy.setProduct(product);
        policy.setStatus(status);
        return policy;
    }

    static final class InsuranceProductTestData {
        final InsuranceProduct term = product(201L, "HD-TERM-001", "HealthDekho Secure Term", "TERM", 240,
                "MONTHLY", "1250.00", "5000000.00");
        final InsuranceProduct health = product(202L, "HD-HEALTH-001", "HealthDekho Family Health", "HEALTH", 12,
                "YEARLY", "18000.00", "1000000.00");
        final InsuranceProduct savings = product(203L, "HD-SAV-001", "HealthDekho Smart Savings", "SAVINGS", 120,
                "QUARTERLY", "7500.00", "1500000.00");

        private static InsuranceProduct product(Long id, String code, String name, String type, int term,
                String frequency, String premium, String sumInsured) {
            InsuranceProduct product = new InsuranceProduct();
            ReflectionTestUtils.setField(product, "id", id);
            product.setProductCode(code);
            product.setPolicyName(name);
            product.setPolicyType(type);
            product.setTermMonths(term);
            product.setPremiumFrequency(frequency);
            product.setPremiumAmount(new BigDecimal(premium));
            product.setSumInsured(new BigDecimal(sumInsured));
            product.setActive(true);
            return product;
        }
    }
}
