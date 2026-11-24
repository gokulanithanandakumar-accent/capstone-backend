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
import com.insuranceapp.backend.dto.CustomerPolicyResponse;
import com.insuranceapp.backend.security.JsonAuthenticationEntryPoint;
import com.insuranceapp.backend.service.CustomerPolicyService;

@WebMvcTest(CustomerPolicyController.class)
@Import({ SecurityConfig.class, JwtConfig.class, ClockConfig.class, JsonAuthenticationEntryPoint.class })
class CustomerPolicyControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerPolicyService customerPolicyService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    void returnsPoliciesOfTokenCustomer() throws Exception {
        when(customerPolicyService.getPoliciesForCustomer(1001L)).thenReturn(List.of(new CustomerPolicyResponse(
                "HDP-2026-0001", "HealthDekho Secure Term", "TERM", 240, LocalDate.of(2026, 11, 20),
                LocalDate.of(2046, 11, 19), "MONTHLY", new BigDecimal("1250.00"), new BigDecimal("5000000.00"),
                "ACTIVE")));

        mockMvc.perform(get("/api/policies/me").with(jwt().jwt(token -> token.subject("1001"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].policyNumber").value("HDP-2026-0001"))
                .andExpect(jsonPath("$[0].policyName").value("HealthDekho Secure Term"))
                .andExpect(jsonPath("$[0].policyType").value("TERM"))
                .andExpect(jsonPath("$[0].termMonths").value(240))
                .andExpect(jsonPath("$[0].startDate").value("2026-11-20"))
                .andExpect(jsonPath("$[0].maturityDate").value("2046-11-19"))
                .andExpect(jsonPath("$[0].premiumFrequency").value("MONTHLY"))
                .andExpect(jsonPath("$[0].premiumAmount").value(1250.00))
                .andExpect(jsonPath("$[0].sumInsured").value(5000000.00))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    void returnsEmptyArrayWhenNoPolicies() throws Exception {
        when(customerPolicyService.getPoliciesForCustomer(1001L)).thenReturn(List.of());

        mockMvc.perform(get("/api/policies/me").with(jwt().jwt(token -> token.subject("1001"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/policies/me")).andExpect(status().isUnauthorized());
        verifyNoInteractions(customerPolicyService);
    }
}
