package com.insuranceapp.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.insuranceapp.backend.config.ClockConfig;
import com.insuranceapp.backend.config.JwtConfig;
import com.insuranceapp.backend.config.SecurityConfig;
import com.insuranceapp.backend.dto.RecommendationItemResponse;
import com.insuranceapp.backend.dto.RecommendationRequest;
import com.insuranceapp.backend.dto.RecommendationResponse;
import com.insuranceapp.backend.exception.AiServiceUnavailableException;
import com.insuranceapp.backend.security.JsonAuthenticationEntryPoint;
import com.insuranceapp.backend.service.RecommendationService;

@WebMvcTest(RecommendationController.class)
@Import({ SecurityConfig.class, JwtConfig.class, ClockConfig.class, JsonAuthenticationEntryPoint.class })
class RecommendationControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecommendationService recommendationService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    void generatesRecommendationsForTokenCustomer() throws Exception {
        when(recommendationService.generateRecommendations(eq(1001L), any(RecommendationRequest.class)))
                .thenReturn(new RecommendationResponse(6001L, 36, List.of(new RecommendationItemResponse(7001L, 1,
                        201L, "HD-TERM-001", "HealthDekho Secure Term", "TERM", 240, new BigDecimal("5000000.00"),
                        "MONTHLY", new BigDecimal("1250.00"),
                        "Long-term protection aligned with income-replacement goal", "HIGH"))));

        mockMvc.perform(post("/api/recommendations").with(jwt().jwt(token -> token.subject("1001")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"dateOfBirth":"1990-04-15","insuranceGoals":"Family income protection",
                         "preferredTerm":"20 years","preferredPolicyType":"TERM"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recommendationRequestId").value(6001))
                .andExpect(jsonPath("$.calculatedAge").value(36))
                .andExpect(jsonPath("$.recommendations[0].recommendationItemId").value(7001))
                .andExpect(jsonPath("$.recommendations[0].productCode").value("HD-TERM-001"))
                .andExpect(jsonPath("$.recommendations[0].recommendationReason")
                        .value("Long-term protection aligned with income-replacement goal"))
                .andExpect(jsonPath("$.recommendations[0].suitability").value("HIGH"));
    }

    @Test
    void rejectsRequestWithoutAnyInput() throws Exception {
        mockMvc.perform(post("/api/recommendations").with(jwt().jwt(token -> token.subject("1001")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"insuranceGoals\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.anyInputProvided").exists());
        verifyNoInteractions(recommendationService);
    }

    @Test
    void rejectsFutureDateOfBirth() throws Exception {
        mockMvc.perform(post("/api/recommendations").with(jwt().jwt(token -> token.subject("1001")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dateOfBirth\":\"" + LocalDate.now().plusDays(2) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.dateOfBirth").value("Date of birth cannot be in the future"));
        verifyNoInteractions(recommendationService);
    }

    @Test
    void rejectsOverlongGoals() throws Exception {
        mockMvc.perform(post("/api/recommendations").with(jwt().jwt(token -> token.subject("1001")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"insuranceGoals\":\"" + "x".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.insuranceGoals").exists());
    }

    @Test
    void returnsServiceUnavailableWhenAiFails() throws Exception {
        when(recommendationService.generateRecommendations(eq(1001L), any(RecommendationRequest.class)))
                .thenThrow(new AiServiceUnavailableException(
                        "AI recommendations are currently unavailable. Please try again later.", null));

        mockMvc.perform(post("/api/recommendations").with(jwt().jwt(token -> token.subject("1001")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"insuranceGoals\":\"Protection\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message")
                        .value("AI recommendations are currently unavailable. Please try again later."));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/recommendations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"insuranceGoals\":\"Protection\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(recommendationService);
    }
}
