package com.insuranceapp.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.insuranceapp.backend.dto.LoginRequest;
import com.insuranceapp.backend.dto.LoginResponse;
import com.insuranceapp.backend.security.CustomerPrincipal;
import com.insuranceapp.backend.security.JwtTokenService;
import com.insuranceapp.backend.security.JwtTokenService.IssuedToken;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenService jwtTokenService;

    @InjectMocks
    private AuthService authService;

    private final CustomerPrincipal principal =
            new CustomerPrincipal(1001L, "gokul@gmail.com", "{bcrypt}hash", "Gokul", true);

    @Test
    void loginReturnsTokenAndCustomerDetailsOnValidCredentials() {
        Instant expiresAt = Instant.parse("2026-11-24T10:00:00Z");
        when(authenticationManager.authenticate(any()))
                .thenReturn(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        when(jwtTokenService.issue(principal)).thenReturn(new IssuedToken("jwt-token", expiresAt));

        LoginResponse response = authService.login(new LoginRequest("gokul@gmail.com", "Password@123"));

        assertThat(response).isEqualTo(new LoginResponse("jwt-token", "Bearer", expiresAt, 1001L, "Gokul",
                "gokul@gmail.com"));
    }

    @Test
    void loginTrimsEmailBeforeAuthenticating() {
        when(authenticationManager.authenticate(any()))
                .thenReturn(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        when(jwtTokenService.issue(principal)).thenReturn(new IssuedToken("jwt-token", Instant.now()));

        authService.login(new LoginRequest("  gokul@gmail.com  ", "Password@123"));

        ArgumentCaptor<Authentication> captor = ArgumentCaptor.forClass(Authentication.class);
        verify(authenticationManager).authenticate(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("gokul@gmail.com");
        assertThat(captor.getValue().getCredentials()).isEqualTo("Password@123");
        assertThat(captor.getValue().isAuthenticated()).isFalse();
    }

    @Test
    void loginPropagatesBadCredentialsWithoutIssuingToken() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        assertThatThrownBy(() -> authService.login(new LoginRequest("gokul@gmail.com", "wrong")))
                .isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(jwtTokenService);
    }

    @Test
    void loginPropagatesDisabledAccountWithoutIssuingToken() {
        when(authenticationManager.authenticate(any())).thenThrow(new DisabledException("disabled"));

        assertThatThrownBy(() -> authService.login(new LoginRequest("gokul@gmail.com", "Password@123")))
                .isInstanceOf(DisabledException.class);
        verifyNoInteractions(jwtTokenService);
    }

    @Test
    void loginRejectsUnexpectedPrincipalType() {
        when(authenticationManager.authenticate(any()))
                .thenReturn(UsernamePasswordAuthenticationToken.authenticated("someone", null, java.util.List.of()));

        assertThatThrownBy(() -> authService.login(new LoginRequest("gokul@gmail.com", "Password@123")))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(jwtTokenService);
    }
}
