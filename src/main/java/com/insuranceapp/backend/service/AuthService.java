package com.insuranceapp.backend.service;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import com.insuranceapp.backend.dto.LoginRequest;
import com.insuranceapp.backend.dto.LoginResponse;
import com.insuranceapp.backend.security.CustomerPrincipal;
import com.insuranceapp.backend.security.JwtTokenService;
import com.insuranceapp.backend.security.JwtTokenService.IssuedToken;

@Service
public class AuthService {
    public static final String TOKEN_TYPE = "Bearer";

    private final AuthenticationManager authenticationManager;
    private final JwtTokenService jwtTokenService;

    public AuthService(AuthenticationManager authenticationManager, JwtTokenService jwtTokenService) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenService = jwtTokenService;
    }

    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.email().trim(), request.password()));

        if (!(authentication.getPrincipal() instanceof CustomerPrincipal principal)) {
            throw new IllegalStateException("Unexpected authentication principal type");
        }

        IssuedToken token = jwtTokenService.issue(principal);
        return new LoginResponse(token.value(), TOKEN_TYPE, token.expiresAt(), principal.customerId(),
                principal.fullName(), principal.email());
    }
}
