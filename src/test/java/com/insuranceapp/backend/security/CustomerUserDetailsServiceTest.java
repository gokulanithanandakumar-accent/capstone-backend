package com.insuranceapp.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

import com.insuranceapp.backend.entity.Customer;
import com.insuranceapp.backend.repository.CustomerRepository;

@ExtendWith(MockitoExtension.class)
class CustomerUserDetailsServiceTest {
    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private CustomerUserDetailsService service;

    @Test
    void loadsActiveCustomerAsEnabledPrincipal() {
        when(customerRepository.findByEmailIgnoreCase("gokul@gmail.com"))
                .thenReturn(Optional.of(customer("ACTIVE")));

        UserDetails details = service.loadUserByUsername("  gokul@gmail.com ");

        assertThat(details).isInstanceOf(CustomerPrincipal.class);
        CustomerPrincipal principal = (CustomerPrincipal) details;
        assertThat(principal.customerId()).isEqualTo(1001L);
        assertThat(principal.getUsername()).isEqualTo("gokul@gmail.com");
        assertThat(principal.getPassword()).isEqualTo("{bcrypt}hash");
        assertThat(principal.fullName()).isEqualTo("Gokul");
        assertThat(principal.isEnabled()).isTrue();
        assertThat(principal.getAuthorities()).extracting("authority").containsExactly("ROLE_CUSTOMER");
        assertThat(principal.toString()).doesNotContain("{bcrypt}hash");
    }

    @Test
    void loadsNonActiveCustomerAsDisabledPrincipal() {
        when(customerRepository.findByEmailIgnoreCase("gokul@gmail.com"))
                .thenReturn(Optional.of(customer("SUSPENDED")));

        assertThat(service.loadUserByUsername("gokul@gmail.com").isEnabled()).isFalse();
    }

    @Test
    void throwsWhenCustomerNotFound() {
        when(customerRepository.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("missing@example.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    private Customer customer(String status) {
        Customer customer = new Customer();
        ReflectionTestUtils.setField(customer, "id", 1001L);
        customer.setEmail("gokul@gmail.com");
        customer.setPasswordHash("{bcrypt}hash");
        customer.setFullName("Gokul");
        customer.setDateOfBirth(LocalDate.of(1990, 4, 15));
        customer.setStatus(status);
        return customer;
    }
}
