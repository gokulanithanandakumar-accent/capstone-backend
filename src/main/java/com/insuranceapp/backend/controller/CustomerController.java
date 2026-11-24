package com.insuranceapp.backend.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.insuranceapp.backend.dto.CustomerProfileResponse;
import com.insuranceapp.backend.security.AuthenticatedCustomer;
import com.insuranceapp.backend.service.CustomerService;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {
    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    /** Returns only the authenticated customer's profile; the id comes from the token, never the request. */
    @GetMapping("/me")
    public CustomerProfileResponse currentCustomer(@AuthenticationPrincipal Jwt jwt) {
        return customerService.getProfile(AuthenticatedCustomer.customerId(jwt));
    }
}
