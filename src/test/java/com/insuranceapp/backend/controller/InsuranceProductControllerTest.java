package com.insuranceapp.backend.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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
import com.insuranceapp.backend.dto.AvailableProductResponse;
import com.insuranceapp.backend.security.JsonAuthenticationEntryPoint;
import com.insuranceapp.backend.service.InsuranceProductService;

@WebMvcTest(InsuranceProductController.class)
@Import({ SecurityConfig.class, JwtConfig.class, ClockConfig.class, JsonAuthenticationEntryPoint.class })
class InsuranceProductControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InsuranceProductService productService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    void returnsAvailableProductsForTokenCustomer() throws Exception {
        when(productService.getAvailableProducts(1001L)).thenReturn(List.of(new AvailableProductResponse(202L,
                "HD-HEALTH-001", "HealthDekho Family Health", "HEALTH", 12, new BigDecimal("1000000.00"), "YEARLY",
                new BigDecimal("18000.00"))));

        mockMvc.perform(get("/api/products/available").with(jwt().jwt(token -> token.subject("1001"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productId").value(202))
                .andExpect(jsonPath("$[0].productCode").value("HD-HEALTH-001"))
                .andExpect(jsonPath("$[0].policyName").value("HealthDekho Family Health"))
                .andExpect(jsonPath("$[0].policyType").value("HEALTH"))
                .andExpect(jsonPath("$[0].termMonths").value(12))
                .andExpect(jsonPath("$[0].sumInsured").value(1000000.00))
                .andExpect(jsonPath("$[0].premiumFrequency").value("YEARLY"))
                .andExpect(jsonPath("$[0].premiumAmount").value(18000.00))
                .andExpect(jsonPath("$[0].active").doesNotExist());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/products/available")).andExpect(status().isUnauthorized());
        verifyNoInteractions(productService);
    }
}
