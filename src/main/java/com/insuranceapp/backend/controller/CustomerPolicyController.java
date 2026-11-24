package com.insuranceapp.backend.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.insuranceapp.backend.dto.CustomerPolicyResponse;
import com.insuranceapp.backend.security.AuthenticatedCustomer;
import com.insuranceapp.backend.service.CustomerPolicyService;

@RestController
@RequestMapping("/api/policies")
public class CustomerPolicyController {
    private final CustomerPolicyService customerPolicyService;

    public CustomerPolicyController(CustomerPolicyService customerPolicyService) {
        this.customerPolicyService = customerPolicyService;
    }

    /** Policies registered by the authenticated customer only; the customer id comes from the token. */
    @GetMapping("/me")
    public List<CustomerPolicyResponse> myPolicies(@AuthenticationPrincipal Jwt jwt) {
        return customerPolicyService.getPoliciesForCustomer(AuthenticatedCustomer.customerId(jwt));
    }
}
