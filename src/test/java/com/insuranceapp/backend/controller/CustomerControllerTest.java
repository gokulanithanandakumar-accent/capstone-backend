package com.insuranceapp.backend.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

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
import com.insuranceapp.backend.dto.CustomerProfileResponse;
import com.insuranceapp.backend.exception.ResourceNotFoundException;
import com.insuranceapp.backend.security.JsonAuthenticationEntryPoint;
import com.insuranceapp.backend.service.CustomerService;

@WebMvcTest(CustomerController.class)
@Import({ SecurityConfig.class, JwtConfig.class, ClockConfig.class, JsonAuthenticationEntryPoint.class })
class CustomerControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerService customerService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    void currentCustomerReturnsProfileOfTokenSubject() throws Exception {
        when(customerService.getProfile(1001L)).thenReturn(new CustomerProfileResponse(1001L, "Gokul",
                "gokul@gmail.com", LocalDate.of(1990, 4, 15), "9876501001"));

        mockMvc.perform(get("/api/customers/me").with(jwt().jwt(token -> token.subject("1001"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(1001))
                .andExpect(jsonPath("$.fullName").value("Gokul"))
                .andExpect(jsonPath("$.dateOfBirth").value("1990-04-15"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        verify(customerService).getProfile(1001L);
    }

    @Test
    void currentCustomerRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/customers/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication is required to access this resource."));
        verifyNoInteractions(customerService);
    }

    @Test
    void currentCustomerRejectsInvalidBearerToken() throws Exception {
        mockMvc.perform(get("/api/customers/me").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(customerService);
    }

    @Test
    void currentCustomerReturns404WhenTokenCustomerNoLongerExists() throws Exception {
        when(customerService.getProfile(42L)).thenThrow(new ResourceNotFoundException("Customer not found."));

        mockMvc.perform(get("/api/customers/me").with(jwt().jwt(token -> token.subject("42"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Customer not found."));
    }

    @Test
    void currentCustomerReturns401ForNonNumericSubject() throws Exception {
        mockMvc.perform(get("/api/customers/me").with(jwt().jwt(token -> token.subject("abc"))))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(customerService);
    }
}
