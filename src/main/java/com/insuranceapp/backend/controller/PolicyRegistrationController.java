package com.insuranceapp.backend.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.insuranceapp.backend.dto.PolicyRegistrationDetailsResponse;
import com.insuranceapp.backend.dto.PolicyRegistrationRequest;
import com.insuranceapp.backend.dto.PolicyRegistrationResponse;
import com.insuranceapp.backend.security.AuthenticatedCustomer;
import com.insuranceapp.backend.service.PolicyRegistrationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/policies")
public class PolicyRegistrationController {
    private final PolicyRegistrationService registrationService;

    public PolicyRegistrationController(PolicyRegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    /** Pre-populated customer/product values and calculated maturity date and total premium. */
    @GetMapping("/registration-details")
    public PolicyRegistrationDetailsResponse registrationDetails(@AuthenticationPrincipal Jwt jwt,
            @RequestParam Long productId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate) {
        return registrationService.getRegistrationDetails(AuthenticatedCustomer.customerId(jwt), productId, startDate);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PolicyRegistrationResponse register(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PolicyRegistrationRequest request) {
        return registrationService.register(AuthenticatedCustomer.customerId(jwt), request);
    }
}
