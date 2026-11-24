package com.insuranceapp.backend.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.insuranceapp.backend.entity.CustomerStatus;
import com.insuranceapp.backend.repository.CustomerRepository;

@Service
public class CustomerUserDetailsService implements UserDetailsService {
    private final CustomerRepository customerRepository;

    public CustomerUserDetailsService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        return customerRepository.findByEmailIgnoreCase(email == null ? "" : email.trim())
                .map(customer -> new CustomerPrincipal(
                        customer.getId(),
                        customer.getEmail(),
                        customer.getPasswordHash(),
                        customer.getFullName(),
                        CustomerStatus.ACTIVE.equals(customer.getStatus())))
                .orElseThrow(() -> new UsernameNotFoundException("Customer not found"));
    }
}
