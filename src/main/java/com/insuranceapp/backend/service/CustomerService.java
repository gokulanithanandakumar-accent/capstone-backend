package com.insuranceapp.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.insuranceapp.backend.dto.CustomerProfileResponse;
import com.insuranceapp.backend.exception.ResourceNotFoundException;
import com.insuranceapp.backend.repository.CustomerRepository;

@Service
public class CustomerService {
    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Transactional(readOnly = true)
    public CustomerProfileResponse getProfile(Long customerId) {
        return customerRepository.findById(customerId)
                .map(customer -> new CustomerProfileResponse(customer.getId(), customer.getFullName(),
                        customer.getEmail(), customer.getDateOfBirth(), customer.getPhone()))
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found."));
    }
}
