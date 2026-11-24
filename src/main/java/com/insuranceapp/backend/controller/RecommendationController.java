package com.insuranceapp.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.insuranceapp.backend.dto.RecommendationRequest;
import com.insuranceapp.backend.dto.RecommendationResponse;
import com.insuranceapp.backend.security.AuthenticatedCustomer;
import com.insuranceapp.backend.service.RecommendationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {
    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    /** Generates and persists AI recommendations for the authenticated customer. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RecommendationResponse generate(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody RecommendationRequest request) {
        return recommendationService.generateRecommendations(AuthenticatedCustomer.customerId(jwt), request);
    }
}
