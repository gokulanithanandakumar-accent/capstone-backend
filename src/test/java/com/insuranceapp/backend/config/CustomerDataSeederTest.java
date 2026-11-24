package com.insuranceapp.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.insuranceapp.backend.entity.Customer;
import com.insuranceapp.backend.repository.CustomerRepository;

@ExtendWith(MockitoExtension.class)
class CustomerDataSeederTest {
    @Mock
    private CustomerRepository customerRepository;

    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    @Test
    @SuppressWarnings("unchecked")
    void seedsMissingDemoCustomerWithBcryptHash() {
        when(customerRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);

        new CustomerDataSeeder(customerRepository, passwordEncoder, new SeedProperties(true, "Password@123")).run();

        ArgumentCaptor<List<Customer>> captor = ArgumentCaptor.forClass(List.class);
        verify(customerRepository).saveAll(captor.capture());
        List<Customer> saved = captor.getValue();
        assertThat(saved).extracting(Customer::getEmail)
                .containsExactly("demo@gmail.com");
        assertThat(saved).allSatisfy(customer -> {
            assertThat(customer.getStatus()).isEqualTo("ACTIVE");
            assertThat(customer.getPasswordHash()).startsWith("{bcrypt}");
            assertThat(passwordEncoder.matches("Password@123", customer.getPasswordHash())).isTrue();
        });
    }

    @Test
    void doesNothingWhenAllSampleCustomersExist() {
        when(customerRepository.existsByEmailIgnoreCase(anyString())).thenReturn(true);

        new CustomerDataSeeder(customerRepository, passwordEncoder, new SeedProperties(true, "Password@123")).run();

        verify(customerRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void failsFastWhenSeedPasswordMissing() {
        CustomerDataSeeder seeder = new CustomerDataSeeder(customerRepository, passwordEncoder,
                new SeedProperties(true, " "));

        assertThatThrownBy(seeder::run).isInstanceOf(IllegalStateException.class);
    }
}
