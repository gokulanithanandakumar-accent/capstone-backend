package com.insuranceapp.backend.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import com.insuranceapp.backend.dto.RegisterResponse;
import com.insuranceapp.backend.exception.DuplicateCustomerException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.insuranceapp.backend.config.ClockConfig;
import com.insuranceapp.backend.config.JwtConfig;
import com.insuranceapp.backend.config.SecurityConfig;
import com.insuranceapp.backend.dto.LoginResponse;
import com.insuranceapp.backend.security.JsonAuthenticationEntryPoint;
import com.insuranceapp.backend.service.AuthService;

@WebMvcTest(AuthController.class)
@Import({ SecurityConfig.class, JwtConfig.class, ClockConfig.class, JsonAuthenticationEntryPoint.class })
class AuthControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    void loginIsPublicAndReturnsToken() throws Exception {
        when(authService.login(any())).thenReturn(new LoginResponse("jwt-token", "Bearer",
                Instant.parse("2026-11-24T10:00:00Z"), 1001L, "Gokul", "gokul@gmail.com"));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"gokul@gmail.com\",\"password\":\"Password@123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.customerId").value(1001))
                .andExpect(jsonPath("$.fullName").value("Gokul"))
                .andExpect(jsonPath("$.email").value("gokul@gmail.com"))
                .andExpect(jsonPath("$.expiresAt").value("2026-11-24T10:00:00Z"));
    }

    @Test
    void loginReturns401WithMessageForInvalidCredentials() throws Exception {
        when(authService.login(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"gokul@gmail.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid email or password."))
                .andExpect(jsonPath("$.path").value("/api/auth/login"));
    }

    @Test
    void loginReturns401WithMessageForInactiveAccount() throws Exception {
        when(authService.login(any())).thenThrow(new DisabledException("disabled"));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"gokul@gmail.com\",\"password\":\"Password@123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(containsString("not active")));
    }

    @Test
    void loginReturns400WithFieldErrorsWhenFieldsMissing() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed."))
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").value("Password is required"));
        verifyNoInteractions(authService);
    }

    @Test
    void loginReturns400ForInvalidEmailFormat() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-an-email\",\"password\":\"Password@123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").value("Email must be a valid email address"));
        verifyNoInteractions(authService);
    }

    @Test
    void loginReturns400ForMalformedBody() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body."));
    }

    @Test
    void corsPreflightAllowsConfiguredFrontendOrigin() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
    }

    @Test
    void corsPreflightRejectsUnknownOrigin() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                .header("Origin", "http://evil.example.com")
                .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void registrationIsPublicAndReturnsCreatedCustomer() throws Exception {

        when(authService.register(any()))
                .thenReturn(
                        new RegisterResponse(
                                1005L,
                                "Gokul",
                                "gokul@example.com",
                                "Registration successful. You can now sign in."));

        mockMvc.perform(
                post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Gokul",
                                  "email": "gokul@example.com",
                                  "dateOfBirth": "1998-04-15",
                                  "phone": "9876543210",
                                  "password": "Password@123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(1005))
                .andExpect(jsonPath("$.fullName").value("Gokul"))
                .andExpect(jsonPath("$.email").value("gokul@example.com"))
                .andExpect(jsonPath("$.message")
                        .value("Registration successful. You can now sign in."));
    }

    @Test
    void registrationReturnsConflictForDuplicateEmail() throws Exception {

        when(authService.register(any()))
                .thenThrow(
                        new DuplicateCustomerException(
                                "An account already exists with this email address."));

        mockMvc.perform(
                post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Gokul",
                                  "email": "gokul@example.com",
                                  "dateOfBirth": "1998-04-15",
                                  "phone": "9876543210",
                                  "password": "Password@123"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("An account already exists with this email address."));
    }

    @Test
    void registrationValidatesRequiredFields() throws Exception {

        mockMvc.perform(
                post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "",
                                  "email": "bad-email",
                                  "dateOfBirth": null,
                                  "password": "short"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.fullName").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.dateOfBirth").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }
}
