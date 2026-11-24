package com.insuranceapp.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.insuranceapp.backend.dto.PolicyPremiumDetailsResponse;
import com.insuranceapp.backend.dto.PremiumPaymentRequest;
import com.insuranceapp.backend.dto.PremiumPaymentResponse;
import com.insuranceapp.backend.security.AuthenticatedCustomer;
import com.insuranceapp.backend.service.PremiumPaymentService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/policies/{policyNumber}")
public class PremiumPaymentController {
    private final PremiumPaymentService premiumPaymentService;

    public PremiumPaymentController(PremiumPaymentService premiumPaymentService) {
        this.premiumPaymentService = premiumPaymentService;
    }

    /** Policy details, premium installment schedule and payment history for one of the customer's policies. */
    @GetMapping("/premiums")
    public PolicyPremiumDetailsResponse premiumDetails(@AuthenticationPrincipal Jwt jwt,
            @PathVariable String policyNumber) {
        return premiumPaymentService.getPremiumDetails(AuthenticatedCustomer.customerId(jwt), policyNumber);
    }

    @PostMapping("/premium-payments")
    @ResponseStatus(HttpStatus.CREATED)
    public PremiumPaymentResponse pay(@AuthenticationPrincipal Jwt jwt, @PathVariable String policyNumber,
            @Valid @RequestBody PremiumPaymentRequest request) {
        return premiumPaymentService.pay(AuthenticatedCustomer.customerId(jwt), policyNumber, request);
    }
}