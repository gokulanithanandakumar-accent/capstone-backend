package com.insuranceapp.backend.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.insuranceapp.backend.dto.AvailableProductResponse;
import com.insuranceapp.backend.security.AuthenticatedCustomer;
import com.insuranceapp.backend.service.InsuranceProductService;

@RestController
@RequestMapping("/api/products")
public class InsuranceProductController {
    private final InsuranceProductService productService;

    public InsuranceProductController(InsuranceProductService productService) {
        this.productService = productService;
    }

    /** Active products the authenticated customer has not already registered. */
    @GetMapping("/available")
    public List<AvailableProductResponse> availableProducts(@AuthenticationPrincipal Jwt jwt) {
        return productService.getAvailableProducts(AuthenticatedCustomer.customerId(jwt));
    }
}
