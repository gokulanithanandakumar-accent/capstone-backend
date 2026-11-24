package com.insuranceapp.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.insuranceapp.backend.entity.InsuranceProduct;
import com.insuranceapp.backend.repository.InsuranceProductRepository;

@ExtendWith(MockitoExtension.class)
class InsuranceProductDataSeederTest {
    @Mock
    private InsuranceProductRepository productRepository;

    @Test
    @SuppressWarnings("unchecked")
    void seedsOnlyMissingSpecificationProducts() {
        when(productRepository.existsByProductCode(anyString())).thenReturn(false);
        when(productRepository.existsByProductCode("HD-HEALTH-001")).thenReturn(true);

        new InsuranceProductDataSeeder(productRepository).run();

        ArgumentCaptor<List<InsuranceProduct>> captor = ArgumentCaptor.forClass(List.class);
        verify(productRepository).saveAll(captor.capture());
        List<InsuranceProduct> saved = captor.getValue();
        assertThat(saved).extracting(InsuranceProduct::getProductCode).containsExactly("HD-TERM-001", "HD-SAV-001");
        InsuranceProduct term = saved.get(0);
        assertThat(term.getTermMonths()).isEqualTo(240);
        assertThat(term.getPremiumAmount()).isEqualByComparingTo(new BigDecimal("1250.00"));
        assertThat(term.getMinEntryAge()).isEqualTo(18);
        assertThat(term.getMaxEntryAge()).isEqualTo(60);
        assertThat(saved).allSatisfy(product -> assertThat(product.isActive()).isTrue());
    }

    @Test
    void doesNothingWhenAllProductsExist() {
        when(productRepository.existsByProductCode(anyString())).thenReturn(true);

        new InsuranceProductDataSeeder(productRepository).run();

        verify(productRepository, never()).saveAll(anyList());
    }
}
