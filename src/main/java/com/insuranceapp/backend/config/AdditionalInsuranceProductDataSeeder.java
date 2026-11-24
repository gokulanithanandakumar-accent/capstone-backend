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

/** Adds 12 extra demo products without changing the existing product seeder. */
@Component
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true")
public class AdditionalInsuranceProductDataSeeder implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(AdditionalInsuranceProductDataSeeder.class);

    record SampleProduct(String productCode, String policyName, String policyType, int termMonths,
            String premiumFrequency, String premiumAmount, String sumInsured, int minEntryAge, int maxEntryAge) {
    }

    static final List<SampleProduct> ADDITIONAL_PRODUCTS = List.of(
            new SampleProduct("HD-TERM-002", "HealthDekho Life Shield", "TERM", 360, "MONTHLY", "1650.00", "7500000.00", 18, 55),
            new SampleProduct("HD-TERM-003", "HealthDekho Family Protect Term", "TERM", 240, "YEARLY", "14500.00", "10000000.00", 21, 60),

            new SampleProduct("HD-HEALTH-002", "HealthDekho Health Secure Plus", "HEALTH", 12, "YEARLY", "24000.00", "1500000.00", 18, 65),
            new SampleProduct("HD-HEALTH-003", "HealthDekho Critical Care", "HEALTH", 12, "MONTHLY", "2250.00", "2000000.00", 18, 60),
            new SampleProduct("HD-HEALTH-004", "HealthDekho Senior Health Guard", "HEALTH", 12, "YEARLY", "36000.00", "1000000.00", 46, 75),

            new SampleProduct("HD-SAV-002", "HealthDekho Future Builder", "SAVINGS", 180, "MONTHLY", "3200.00", "2000000.00", 18, 50),
            new SampleProduct("HD-SAV-003", "HealthDekho Wealth Secure", "SAVINGS", 120, "HALF_YEARLY", "24000.00", "2500000.00", 21, 55),

            new SampleProduct("HD-ACC-001", "HealthDekho Personal Accident Shield", "ACCIDENT", 12, "YEARLY", "4200.00", "1000000.00", 18, 65),
            new SampleProduct("HD-ACC-002", "HealthDekho Accident Protect Plus", "ACCIDENT", 12, "MONTHLY", "650.00", "2000000.00", 18, 60),

            new SampleProduct("HD-TRAVEL-001", "HealthDekho Travel Safe", "TRAVEL", 12, "YEARLY", "5200.00", "2500000.00", 18, 70),
            new SampleProduct("HD-HOME-001", "HealthDekho Home Protect", "HOME", 120, "YEARLY", "4800.00", "5000000.00", 21, 70),
            new SampleProduct("HD-CHILD-001", "HealthDekho Child Future Plan", "SAVINGS", 180, "QUARTERLY", "9000.00", "3000000.00", 21, 50)
    );

    private final InsuranceProductRepository productRepository;

    public AdditionalInsuranceProductDataSeeder(InsuranceProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        List<InsuranceProduct> missing = ADDITIONAL_PRODUCTS.stream()
                .filter(sample -> !productRepository.existsByProductCode(sample.productCode()))
                .map(AdditionalInsuranceProductDataSeeder::toProduct)
                .toList();

        if (!missing.isEmpty()) {
            productRepository.saveAll(missing);
        }

        log.info("Seeded {} additional insurance product(s)", missing.size());
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
