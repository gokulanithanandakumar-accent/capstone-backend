package com.insuranceapp.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.insuranceapp.backend.config.ClockConfig;
import com.insuranceapp.backend.config.JwtConfig;
import com.insuranceapp.backend.config.SecurityConfig;
import com.insuranceapp.backend.dto.PolicyRegistrationDetailsResponse;
import com.insuranceapp.backend.dto.PolicyRegistrationRequest;
import com.insuranceapp.backend.dto.PolicyRegistrationResponse;
import com.insuranceapp.backend.exception.BusinessRuleException;
import com.insuranceapp.backend.exception.DuplicatePolicyException;
import com.insuranceapp.backend.exception.GlobalExceptionHandler;
import com.insuranceapp.backend.security.JsonAuthenticationEntryPoint;
import com.insuranceapp.backend.service.PolicyRegistrationService;

@WebMvcTest(PolicyRegistrationController.class)
@Import({ SecurityConfig.class, JwtConfig.class, ClockConfig.class, JsonAuthenticationEntryPoint.class })
class PolicyRegistrationControllerTest {
    private static final String VALID_BODY = """
            {"productId":201,"startDate":"2026-11-20","recommendationItemId":7001}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PolicyRegistrationService registrationService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    void returnsPrePopulatedRegistrationDetails() throws Exception {
        when(registrationService.getRegistrationDetails(1001L, 201L, LocalDate.of(2026, 11, 20)))
                .thenReturn(new PolicyRegistrationDetailsResponse("Gokul", LocalDate.of(1990, 4, 15),
                        "gokul@gmail.com", "9000000002", 201L, "HD-TERM-001", "HealthDekho Secure Term", "TERM", 240,
                        LocalDate.of(2026, 11, 20), LocalDate.of(2046, 11, 19), "MONTHLY", new BigDecimal("1250.00"),
                        new BigDecimal("5000000.00"), new BigDecimal("300000.00"), 240));

        mockMvc.perform(get("/api/policies/registration-details").param("productId", "201")
                .param("startDate", "2026-11-20").with(jwt().jwt(token -> token.subject("1001"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerName").value("Gokul"))
                .andExpect(jsonPath("$.policyName").value("HealthDekho Secure Term"))
                .andExpect(jsonPath("$.maturityDate").value("2046-11-19"))
                .andExpect(jsonPath("$.totalPremiumPayable").value(300000.00))
                .andExpect(jsonPath("$.numberOfInstallments").value(240));
    }

    @Test
    void registrationDetailsStartDateIsOptional() throws Exception {
        mockMvc.perform(get("/api/policies/registration-details").param("productId", "201")
                .with(jwt().jwt(token -> token.subject("1001"))))
                .andExpect(status().isOk());
        verify(registrationService).getRegistrationDetails(eq(1001L), eq(201L), isNull());
    }

    @Test
    void registrationDetailsRejectMissingOrInvalidParameters() throws Exception {
        mockMvc.perform(get("/api/policies/registration-details").with(jwt().jwt(token -> token.subject("1001"))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/policies/registration-details").param("productId", "201")
                .param("startDate", "20-11-2026").with(jwt().jwt(token -> token.subject("1001"))))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(registrationService);
    }

    @Test
    void registersPolicyForTokenCustomer() throws Exception {
        when(registrationService.register(1001L,
                new PolicyRegistrationRequest(201L, LocalDate.of(2026, 11, 20), 7001L)))
                .thenReturn(new PolicyRegistrationResponse("HDP-2026-0001", "HealthDekho Secure Term",
                        LocalDate.of(2026, 11, 20), LocalDate.of(2046, 11, 19), new BigDecimal("300000.00"), 240,
                        "ACTIVE", "Policy registered successfully. Your policy identifier is HDP-2026-0001."));

        mockMvc.perform(post("/api/policies").with(jwt().jwt(token -> token.subject("1001")))
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.policyNumber").value("HDP-2026-0001"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.message")
                        .value("Policy registered successfully. Your policy identifier is HDP-2026-0001."));
    }

    @Test
    void rejectsMissingMandatoryFields() throws Exception {
        mockMvc.perform(post("/api/policies").with(jwt().jwt(token -> token.subject("1001")))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.productId").exists())
                .andExpect(jsonPath("$.fieldErrors.startDate").exists());
        verifyNoInteractions(registrationService);
    }

    @Test
    void returnsConflictForDuplicateRegistration() throws Exception {
        when(registrationService.register(eq(1001L), any()))
                .thenThrow(new DuplicatePolicyException("You have already registered a policy for HealthDekho Secure Term."));

        mockMvc.perform(post("/api/policies").with(jwt().jwt(token -> token.subject("1001")))
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("You have already registered a policy for HealthDekho Secure Term."));
    }

    @Test
    void returnsConflictWhenUniqueConstraintIsViolated() throws Exception {
        when(registrationService.register(eq(1001L), any()))
                .thenThrow(new DataIntegrityViolationException("uq_customer_policy_customer_product"));

        mockMvc.perform(post("/api/policies").with(jwt().jwt(token -> token.subject("1001")))
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(GlobalExceptionHandler.DATA_CONFLICT));
    }

    @Test
    void returnsUnprocessableEntityForBusinessRuleViolation() throws Exception {
        when(registrationService.register(eq(1001L), any()))
                .thenThrow(new BusinessRuleException("You are not eligible for HealthDekho Secure Term."));

        mockMvc.perform(post("/api/policies").with(jwt().jwt(token -> token.subject("1001")))
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("You are not eligible for HealthDekho Secure Term."));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/policies").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/policies/registration-details").param("productId", "201"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(registrationService);
    }
}
