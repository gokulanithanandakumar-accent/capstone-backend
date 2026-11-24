package com.insuranceapp.backend.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.insuranceapp.backend.config.ClockConfig;
import com.insuranceapp.backend.config.JwtConfig;
import com.insuranceapp.backend.config.SecurityConfig;
import com.insuranceapp.backend.dto.PolicySearchRequest;
import com.insuranceapp.backend.dto.PolicySearchResponse;
import com.insuranceapp.backend.dto.PolicySearchResult;
import com.insuranceapp.backend.exception.InvalidSearchCriteriaException;
import com.insuranceapp.backend.security.JsonAuthenticationEntryPoint;
import com.insuranceapp.backend.service.PolicySearchService;

@WebMvcTest(PolicySearchController.class)
@Import({ SecurityConfig.class, JwtConfig.class, ClockConfig.class, JsonAuthenticationEntryPoint.class })
class PolicySearchControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PolicySearchService policySearchService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    void bindsCriteriaAndReturnsResults() throws Exception {
        when(policySearchService.search(1002L,
                new PolicySearchRequest("HDP-2026-0001", null, LocalDate.of(1992, 6, 10), 0, 5)))
                .thenReturn(new PolicySearchResponse(List.of(new PolicySearchResult("HDP-2026-0001", "Yuvaraj",
                        LocalDate.of(1992, 6, 10), "HealthDekho Secure Term", "TERM", LocalDate.of(2026, 11, 20),
                        LocalDate.of(2046, 11, 19), new BigDecimal("300000.00"), "PARTIALLY_PAID",
                        new BigDecimal("1250.00"), new BigDecimal("298750.00"))), 0, 5, 1, 1));

        mockMvc.perform(get("/api/policies/search").with(jwt().jwt(t -> t.subject("1002")))
                .param("policyNumber", "HDP-2026-0001").param("dateOfBirth", "1992-06-10")
                .param("page", "0").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].policyNumber").value("HDP-2026-0001"))
                .andExpect(jsonPath("$.results[0].dateOfBirth").value("1992-06-10"))
                .andExpect(jsonPath("$.results[0].premiumPaymentStatus").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.results[0].remainingPremium").value(298750.00))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.size").value(5));
    }

    @Test
    void returnsBadRequestForUnsupportedCombination() throws Exception {
        when(policySearchService.search(1002L, new PolicySearchRequest(null, null, null, null, null)))
                .thenThrow(new InvalidSearchCriteriaException("Enter a Policy Identifier or Customer Name to search."));

        mockMvc.perform(get("/api/policies/search").with(jwt().jwt(t -> t.subject("1002"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Enter a Policy Identifier or Customer Name to search."));
    }

    @Test
    void validatesFieldsBeforeCallingService() throws Exception {
        mockMvc.perform(get("/api/policies/search").with(jwt().jwt(t -> t.subject("1002")))
                .param("customerName", "Yuva<script>").param("size", "51")
                .param("dateOfBirth", LocalDate.now().plusDays(2).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.customerName")
                        .value("Customer name may contain only letters, spaces, apostrophes, periods and hyphens"))
                .andExpect(jsonPath("$.fieldErrors.size").value("Page size cannot exceed 50"))
                .andExpect(jsonPath("$.fieldErrors.dateOfBirth").value("Date of birth cannot be in the future"));
        mockMvc.perform(get("/api/policies/search").with(jwt().jwt(t -> t.subject("1002")))
                .param("policyNumber", "HDP 2026 0001%").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.policyNumber")
                        .value("Policy identifier may contain only letters, digits and hyphens"))
                .andExpect(jsonPath("$.fieldErrors.page").value("Page number cannot be negative"));
        mockMvc.perform(get("/api/policies/search").with(jwt().jwt(t -> t.subject("1002")))
                .param("policyNumber", "HDP-2026-0001").param("dateOfBirth", "10-06-1992"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.dateOfBirth").value("Invalid value."));
        verifyNoInteractions(policySearchService);
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/policies/search").param("customerName", "Yuvaraj"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(policySearchService);
    }
}
