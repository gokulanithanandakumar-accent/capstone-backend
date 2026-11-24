package com.insuranceapp.backend.service;

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
import org.springframework.test.util.ReflectionTestUtils;

import com.insuranceapp.backend.dto.CustomerProfileResponse;
import com.insuranceapp.backend.entity.Customer;
import com.insuranceapp.backend.exception.ResourceNotFoundException;
import com.insuranceapp.backend.repository.CustomerRepository;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {
    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private CustomerService customerService;

    @Test
    void getProfileMapsCustomerToDtoWithoutPasswordHash() {
        Customer customer = new Customer();
        ReflectionTestUtils.setField(customer, "id", 1001L);
        customer.setFullName("Gokul");
        customer.setEmail("gokul@gmail.com");
        customer.setDateOfBirth(LocalDate.of(1990, 4, 15));
        customer.setPhone("9876501001");
        customer.setPasswordHash("{bcrypt}secret");
        when(customerRepository.findById(1001L)).thenReturn(Optional.of(customer));

        CustomerProfileResponse profile = customerService.getProfile(1001L);

        assertThat(profile).isEqualTo(new CustomerProfileResponse(1001L, "Gokul", "gokul@gmail.com",
                LocalDate.of(1990, 4, 15), "9876501001"));
    }

    @Test
    void getProfileThrowsWhenCustomerMissing() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.getProfile(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Customer not found.");
    }
}
