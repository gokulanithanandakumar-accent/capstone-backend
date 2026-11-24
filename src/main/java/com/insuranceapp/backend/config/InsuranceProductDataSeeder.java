package com.insuranceapp.backend.config;

import java.math.BigDecimal;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.insuranceapp.backend.entity.InsuranceProduct;
import com.insuranceapp.backend.repository.InsuranceProductRepository;

/** Seeds the insurance products from section 5.5.2 of the specification. */
@Component
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true")
public class InsuranceProductDataSeeder implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(InsuranceProductDataSeeder.class);

    record SampleProduct(String productCode, String policyName, String policyType, int termMonths,
            String premiumFrequency, String premiumAmount, String sumInsured, int minEntryAge, int maxEntryAge) {
    }

    static final List<SampleProduct> SAMPLE_PRODUCTS = List.of(
            new SampleProduct("HD-TERM-001", "HealthDekho Secure Term", "TERM", 240, "MONTHLY", "1250.00",
                    "5000000.00", 18, 60),
            new SampleProduct("HD-HEALTH-001", "HealthDekho Family Health", "HEALTH", 12, "YEARLY", "18000.00",
                    "1000000.00", 18, 65),
            new SampleProduct("HD-SAV-001", "HealthDekho Smart Savings", "SAVINGS", 120, "QUARTERLY", "7500.00",
                    "1500000.00", 21, 55));

    private final InsuranceProductRepository productRepository;

    public InsuranceProductDataSeeder(InsuranceProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        List<InsuranceProduct> missing = SAMPLE_PRODUCTS.stream()
                .filter(sample -> !productRepository.existsByProductCode(sample.productCode()))
                .map(InsuranceProductDataSeeder::toProduct)
                .toList();
        if (!missing.isEmpty()) {
            productRepository.saveAll(missing);
        }
        log.info("Seeded {} sample insurance product(s)", missing.size());
    }

    private static InsuranceProduct toProduct(SampleProduct sample) {
        InsuranceProduct product = new InsuranceProduct();
        product.setProductCode(sample.productCode());
        product.setPolicyName(sample.policyName());
        product.setPolicyType(sample.policyType());
        product.setTermMonths(sample.termMonths());
        product.setPremiumFrequency(sample.premiumFrequency());
        product.setPremiumAmount(new BigDecimal(sample.premiumAmount()));
        product.setSumInsured(new BigDecimal(sample.sumInsured()));
        product.setMinEntryAge(sample.minEntryAge());
        product.setMaxEntryAge(sample.maxEntryAge());
        product.setActive(true);
        return product;
    }
}
