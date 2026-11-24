package com.insuranceapp.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
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
import com.insuranceapp.backend.dto.PolicyPremiumDetailsResponse;
import com.insuranceapp.backend.dto.PremiumInstallmentResponse;
import com.insuranceapp.backend.dto.PremiumPaymentHistoryResponse;
import com.insuranceapp.backend.dto.PremiumPaymentRequest;
import com.insuranceapp.backend.dto.PremiumPaymentResponse;
import com.insuranceapp.backend.dto.PremiumPolicySummaryResponse;
import com.insuranceapp.backend.exception.BusinessRuleException;
import com.insuranceapp.backend.exception.DuplicatePaymentException;
import com.insuranceapp.backend.exception.ResourceNotFoundException;
import com.insuranceapp.backend.security.JsonAuthenticationEntryPoint;
import com.insuranceapp.backend.service.PaymentMethod;
import com.insuranceapp.backend.service.PremiumPaymentService;

@WebMvcTest(PremiumPaymentController.class)
@Import({ SecurityConfig.class, JwtConfig.class, ClockConfig.class, JsonAuthenticationEntryPoint.class })
class PremiumPaymentControllerTest {
    private static final String PAYMENT_BODY = """
            {"paymentMethod":"UPI","payments":[{"installmentId":4003,"amount":1250.00}]}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PremiumPaymentService premiumPaymentService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    void returnsPolicyScheduleAndHistory() throws Exception {
        when(premiumPaymentService.getPremiumDetails(1001L, "HDP-2026-0001")).thenReturn(new PolicyPremiumDetailsResponse(
                new PremiumPolicySummaryResponse("HDP-2026-0001", "Gokul", "HealthDekho Secure Term", 240,
                        LocalDate.of(2026, 11, 20), LocalDate.of(2046, 11, 19), "MONTHLY", "ACTIVE",
                        new BigDecimal("300000.00"), new BigDecimal("1750.00"), new BigDecimal("298250.00"),
                        new BigDecimal("0.00")),
                List.of(new PremiumInstallmentResponse(4002L, 2, YearMonth.of(2026, 12), new BigDecimal("1250.00"),
                        LocalDate.of(2026, 12, 20), LocalDate.of(2027, 1, 20), new BigDecimal("500.00"),
                        new BigDecimal("0.00"), new BigDecimal("750.00"), new BigDecimal("0.00"),
                        new BigDecimal("750.00"), "PARTIALLY_PAID", true)),
                List.of(new PremiumPaymentHistoryResponse("TXN-HD-000002", 2, LocalDate.of(2026, 12, 20),
                        new BigDecimal("500.00"), new BigDecimal("0.00"), "NET_BANKING"))));

        mockMvc.perform(get("/api/policies/HDP-2026-0001/premiums").with(jwt().jwt(t -> t.subject("1001"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policy.customerName").value("Gokul"))
                .andExpect(jsonPath("$.policy.totalPremiumPaid").value(1750.00))
                .andExpect(jsonPath("$.policy.totalRemainingPremium").value(298250.00))
                .andExpect(jsonPath("$.installments[0].premiumPeriod").value("2026-12"))
                .andExpect(jsonPath("$.installments[0].gracePeriodEndDate").value("2027-01-20"))
                .andExpect(jsonPath("$.installments[0].paymentStatus").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.installments[0].selectable").value(true))
                .andExpect(jsonPath("$.payments[0].transactionReference").value("TXN-HD-000002"));
    }

    @Test
    void returnsNotFoundForUnknownPolicy() throws Exception {
        when(premiumPaymentService.getPremiumDetails(1001L, "HDP-0000-0000"))
                .thenThrow(new ResourceNotFoundException("No policy found with identifier HDP-0000-0000."));

        mockMvc.perform(get("/api/policies/HDP-0000-0000/premiums").with(jwt().jwt(t -> t.subject("1001"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No policy found with identifier HDP-0000-0000."));
    }

    @Test
    void recordsPayment() throws Exception {
        when(premiumPaymentService.pay(1001L, "HDP-2026-0001", new PremiumPaymentRequest(PaymentMethod.UPI,
                List.of(new PremiumPaymentRequest.InstallmentPayment(4003L, new BigDecimal("1250.00"))))))
                .thenReturn(new PremiumPaymentResponse("HDP-2026-0001", LocalDate.of(2027, 1, 5),
                        List.of(new PremiumPaymentResponse.PaymentReceipt("TXN-HD-000004", 3,
                                new BigDecimal("1250.00"), new BigDecimal("0.00"), new BigDecimal("0.00"), "PAID")),
                        new BigDecimal("1250.00"), new BigDecimal("0.00"), "ACTIVE",
                        "Payment successful. 1 installment(s) updated."));

        mockMvc.perform(post("/api/policies/HDP-2026-0001/premium-payments").with(jwt().jwt(t -> t.subject("1001")))
                .contentType(MediaType.APPLICATION_JSON).content(PAYMENT_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.receipts[0].transactionReference").value("TXN-HD-000004"))
                .andExpect(jsonPath("$.receipts[0].paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.message").value("Payment successful. 1 installment(s) updated."));
    }

    @Test
    void validatesPaymentRequest() throws Exception {
        mockMvc.perform(post("/api/policies/HDP-2026-0001/premium-payments").with(jwt().jwt(t -> t.subject("1001")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"payments\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.paymentMethod").value("Payment method is required"))
                .andExpect(jsonPath("$.fieldErrors.payments").value("Select at least one installment to pay"));
        mockMvc.perform(post("/api/policies/HDP-2026-0001/premium-payments").with(jwt().jwt(t -> t.subject("1001")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentMethod\":\"UPI\",\"payments\":[{\"installmentId\":4003,\"amount\":-5}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['payments[0].amount']").value("Amount paid must be greater than zero"));
        mockMvc.perform(post("/api/policies/HDP-2026-0001/premium-payments").with(jwt().jwt(t -> t.subject("1001")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentMethod\":\"UPI\",\"payments\":[{\"installmentId\":4003,\"amount\":10.005}]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/policies/HDP-2026-0001/premium-payments").with(jwt().jwt(t -> t.subject("1001")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentMethod\":\"CASH\",\"payments\":[{\"installmentId\":4003,\"amount\":10}]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(premiumPaymentService);
    }

    @Test
    void returnsConflictForDuplicatePayment() throws Exception {
        when(premiumPaymentService.pay(eq(1001L), eq("HDP-2026-0001"), any()))
                .thenThrow(new DuplicatePaymentException("Installment 3 has already been paid."));

        mockMvc.perform(post("/api/policies/HDP-2026-0001/premium-payments").with(jwt().jwt(t -> t.subject("1001")))
                .contentType(MediaType.APPLICATION_JSON).content(PAYMENT_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Installment 3 has already been paid."));
    }

    @Test
    void returnsUnprocessableEntityForOverpayment() throws Exception {
        when(premiumPaymentService.pay(eq(1001L), eq("HDP-2026-0001"), any()))
                .thenThrow(new BusinessRuleException("Payment of 1300.00 for installment 3 exceeds the outstanding amount of 1250.00."));

        mockMvc.perform(post("/api/policies/HDP-2026-0001/premium-payments").with(jwt().jwt(t -> t.subject("1001")))
                .contentType(MediaType.APPLICATION_JSON).content(PAYMENT_BODY))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/policies/HDP-2026-0001/premiums")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/policies/HDP-2026-0001/premium-payments")
                .contentType(MediaType.APPLICATION_JSON).content(PAYMENT_BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(premiumPaymentService);
    }
}