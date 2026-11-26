package com.insuranceapp.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.insuranceapp.backend.dto.LoginRequest;
import com.insuranceapp.backend.dto.LoginResponse;
import com.insuranceapp.backend.dto.RegisterRequest;
import com.insuranceapp.backend.dto.RegisterResponse;
import com.insuranceapp.backend.entity.Customer;
import com.insuranceapp.backend.exception.DuplicateCustomerException;
import com.insuranceapp.backend.repository.CustomerRepository;
import com.insuranceapp.backend.security.CustomerPrincipal;
import com.insuranceapp.backend.security.JwtTokenService;
import com.insuranceapp.backend.security.JwtTokenService.IssuedToken;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AuthService authService;

    private final CustomerPrincipal principal =
            new CustomerPrincipal(
                    1001L,
                    "gokul@gmail.com",
                    "{bcrypt}hash",
                    "Gokul",
                    true);

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                authenticationManager,
                jwtTokenService,
                customerRepository,
                passwordEncoder);
    }

    @Test
    void loginReturnsTokenAndCustomerDetailsOnValidCredentials() {

        Instant expiresAt =
                Instant.parse("2026-11-24T10:00:00Z");

        when(authenticationManager.authenticate(any()))
                .thenReturn(
                        UsernamePasswordAuthenticationToken.authenticated(
                                principal,
                                null,
                                principal.getAuthorities()));

        when(jwtTokenService.issue(principal))
                .thenReturn(
                        new IssuedToken(
                                "jwt-token",
                                expiresAt));

        LoginResponse response =
                authService.login(
                        new LoginRequest(
                                "gokul@gmail.com",
                                "Password@123"));

        assertThat(response).isEqualTo(
                new LoginResponse(
                        "jwt-token",
                        "Bearer",
                        expiresAt,
                        1001L,
                        "Gokul",
                        "gokul@gmail.com"));
    }

    @Test
    void loginTrimsEmailBeforeAuthenticating() {

        when(authenticationManager.authenticate(any()))
                .thenReturn(
                        UsernamePasswordAuthenticationToken.authenticated(
                                principal,
                                null,
                                principal.getAuthorities()));

        when(jwtTokenService.issue(principal))
                .thenReturn(
                        new IssuedToken(
                                "jwt-token",
                                Instant.now()));

        authService.login(
                new LoginRequest(
                        "  gokul@gmail.com  ",
                        "Password@123"));

        ArgumentCaptor<Authentication> captor =
                ArgumentCaptor.forClass(Authentication.class);

        verify(authenticationManager)
                .authenticate(captor.capture());

        assertThat(captor.getValue().getName())
                .isEqualTo("gokul@gmail.com");

        assertThat(captor.getValue().getCredentials())
                .isEqualTo("Password@123");

        assertThat(captor.getValue().isAuthenticated())
                .isFalse();
    }

    @Test
    void loginPropagatesBadCredentialsWithoutIssuingToken() {

        when(authenticationManager.authenticate(any()))
                .thenThrow(
                        new BadCredentialsException("bad"));

        assertThatThrownBy(() ->
                authService.login(
                        new LoginRequest(
                                "gokul@gmail.com",
                                "wrong")))
                .isInstanceOf(
                        BadCredentialsException.class);

        verifyNoInteractions(jwtTokenService);
    }

    @Test
    void loginPropagatesDisabledAccountWithoutIssuingToken() {

        when(authenticationManager.authenticate(any()))
                .thenThrow(
                        new DisabledException("disabled"));

        assertThatThrownBy(() ->
                authService.login(
                        new LoginRequest(
                                "gokul@gmail.com",
                                "Password@123")))
                .isInstanceOf(
                        DisabledException.class);

        verifyNoInteractions(jwtTokenService);
    }

    @Test
    void loginRejectsUnexpectedPrincipalType() {

        when(authenticationManager.authenticate(any()))
                .thenReturn(
                        UsernamePasswordAuthenticationToken.authenticated(
                                "someone",
                                null,
                                java.util.List.of()));

        assertThatThrownBy(() ->
                authService.login(
                        new LoginRequest(
                                "gokul@gmail.com",
                                "Password@123")))
                .isInstanceOf(
                        IllegalStateException.class);

        verifyNoInteractions(jwtTokenService);
    }

    @Test
    void registerCreatesActiveCustomerWithEncodedPassword() {

        RegisterRequest request =
                new RegisterRequest(
                        "  Gokul Nandakumar  ",
                        "  GOKUL@Example.com  ",
                        LocalDate.of(1998, 4, 15),
                        " 9876543210 ",
                        "Password@123");

        when(customerRepository.existsByEmailIgnoreCase(
                "gokul@example.com"))
                .thenReturn(false);

        when(passwordEncoder.encode("Password@123"))
                .thenReturn("{bcrypt}encoded-password");

        when(customerRepository.save(any(Customer.class)))
                .thenAnswer(invocation -> {
                    Customer customer =
                            invocation.getArgument(0);

                    ReflectionTestUtils.setField(
                            customer,
                            "id",
                            1005L);

                    return customer;
                });

        RegisterResponse response =
                authService.register(request);

        ArgumentCaptor<Customer> captor =
                ArgumentCaptor.forClass(Customer.class);

        verify(customerRepository)
                .save(captor.capture());

        Customer saved = captor.getValue();

        assertThat(saved.getFullName())
                .isEqualTo("Gokul Nandakumar");

        assertThat(saved.getEmail())
                .isEqualTo("gokul@example.com");

        assertThat(saved.getPasswordHash())
                .isEqualTo(
                        "{bcrypt}encoded-password");

        assertThat(saved.getDateOfBirth())
                .isEqualTo(
                        LocalDate.of(1998, 4, 15));

        assertThat(saved.getPhone())
                .isEqualTo("9876543210");

        assertThat(saved.getStatus())
                .isEqualTo("ACTIVE");

        assertThat(response.customerId())
                .isEqualTo(1005L);

        assertThat(response.email())
                .isEqualTo("gokul@example.com");

        assertThat(response.message())
                .contains("Registration successful");
    }

    @Test
    void registerStoresNullForBlankOptionalPhone() {

        RegisterRequest request =
                new RegisterRequest(
                        "Gokul",
                        "gokul@example.com",
                        LocalDate.of(1998, 4, 15),
                        " ",
                        "Password@123");

        when(customerRepository.existsByEmailIgnoreCase(
                "gokul@example.com"))
                .thenReturn(false);

        when(passwordEncoder.encode(any()))
                .thenReturn("{bcrypt}encoded");

        when(customerRepository.save(any(Customer.class)))
                .thenAnswer(invocation -> {
                    Customer customer =
                            invocation.getArgument(0);

                    ReflectionTestUtils.setField(
                            customer,
                            "id",
                            1005L);

                    return customer;
                });

        authService.register(request);

        ArgumentCaptor<Customer> captor =
                ArgumentCaptor.forClass(Customer.class);

        verify(customerRepository)
                .save(captor.capture());

        assertThat(captor.getValue().getPhone())
                .isNull();
    }

    @Test
    void registerRejectsDuplicateEmailWithoutEncodingOrSaving() {

        when(customerRepository.existsByEmailIgnoreCase(
                "gokul@example.com"))
                .thenReturn(true);

        RegisterRequest request =
                new RegisterRequest(
                        "Gokul",
                        "GOKUL@example.com",
                        LocalDate.of(1998, 4, 15),
                        "9876543210",
                        "Password@123");

        assertThatThrownBy(() ->
                authService.register(request))
                .isInstanceOf(
                        DuplicateCustomerException.class)
                .hasMessage(
                        "An account already exists with this email address.");

        verifyNoInteractions(passwordEncoder);
    }
}
