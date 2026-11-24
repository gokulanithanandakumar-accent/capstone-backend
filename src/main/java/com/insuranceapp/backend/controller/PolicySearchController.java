package com.insuranceapp.backend.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.insuranceapp.backend.dto.PolicySearchRequest;
import com.insuranceapp.backend.dto.PolicySearchResponse;
import com.insuranceapp.backend.security.AuthenticatedCustomer;
import com.insuranceapp.backend.service.PolicySearchService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/policies")
public class PolicySearchController {
    private final PolicySearchService policySearchService;

    public PolicySearchController(PolicySearchService policySearchService) {
        this.policySearchService = policySearchService;
    }

    @GetMapping("/search")
    public PolicySearchResponse search(@AuthenticationPrincipal Jwt jwt,
            @Valid @ModelAttribute PolicySearchRequest request) {
        return policySearchService.search(AuthenticatedCustomer.customerId(jwt), request);
    }
}