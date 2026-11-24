package com.insuranceapp.backend.config;

import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.insuranceapp.backend.entity.Customer;
import com.insuranceapp.backend.entity.CustomerStatus;
import com.insuranceapp.backend.repository.CustomerRepository;

/**
 * Seeds the sample customers from section 5.5.1 of the specification, storing BCrypt password hashes.
 */
@Component
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(SeedProperties.class)
public class CustomerDataSeeder implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(CustomerDataSeeder.class);

    record SampleCustomer(String email, String fullName, LocalDate dateOfBirth, String phone) {
    }

    static final List<SampleCustomer> SAMPLE_CUSTOMERS = List.of(
            new SampleCustomer("demo@gmail.com", "Demo", LocalDate.of(1990, 4, 15), "9876501001")
    );


    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final SeedProperties properties;

    public CustomerDataSeeder(CustomerRepository customerRepository, PasswordEncoder passwordEncoder,
            SeedProperties properties) {
        this.customerRepository = customerRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (properties.customerPassword() == null || properties.customerPassword().isBlank()) {
            throw new IllegalStateException("app.seed.customer-password must be set when app.seed.enabled=true");
        }
        List<Customer> missing = SAMPLE_CUSTOMERS.stream()
                .filter(sample -> !customerRepository.existsByEmailIgnoreCase(sample.email()))
                .map(this::toCustomer)
                .toList();
        if (!missing.isEmpty()) {
            customerRepository.saveAll(missing);
        }
        log.info("Seeded {} sample customer(s)", missing.size());
    }

    private Customer toCustomer(SampleCustomer sample) {
        Customer customer = new Customer();
        customer.setEmail(sample.email());
        customer.setFullName(sample.fullName());
        customer.setDateOfBirth(sample.dateOfBirth());
        customer.setPhone(sample.phone());
        customer.setStatus(CustomerStatus.ACTIVE);
        customer.setPasswordHash(passwordEncoder.encode(properties.customerPassword()));
        return customer;
    }
}
