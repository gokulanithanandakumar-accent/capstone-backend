package com.insuranceapp.backend.service;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

@Service
public class AuthService {

    public static final String TOKEN_TYPE = "Bearer";

    private final AuthenticationManager authenticationManager;
    private final JwtTokenService jwtTokenService;
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            AuthenticationManager authenticationManager,
            JwtTokenService jwtTokenService,
            CustomerRepository customerRepository,
            PasswordEncoder passwordEncoder) {

        this.authenticationManager = authenticationManager;
        this.jwtTokenService = jwtTokenService;
        this.customerRepository = customerRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public LoginResponse login(LoginRequest request) {

        Authentication authentication =
                authenticationManager.authenticate(
                        UsernamePasswordAuthenticationToken.unauthenticated(
                                request.email().trim(),
                                request.password()));

        if (!(authentication.getPrincipal() instanceof CustomerPrincipal principal)) {
            throw new IllegalStateException(
                    "Unexpected authentication principal type");
        }

        IssuedToken token =
                jwtTokenService.issue(principal);

        return new LoginResponse(
                token.value(),
                TOKEN_TYPE,
                token.expiresAt(),
                principal.customerId(),
                principal.fullName(),
                principal.email());
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {

        String email =
                request.email().trim().toLowerCase();

        if (customerRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateCustomerException(
                    "An account already exists with this email address.");
        }

        Customer customer = new Customer();

        customer.setFullName(
                request.fullName().trim());

        customer.setEmail(email);

        /*
         * DelegatingPasswordEncoder produces a value such as:
         * {bcrypt}$2a$...
         *
         * This matches the password handling already used
         * by the application.
         */
        customer.setPasswordHash(
                passwordEncoder.encode(
                        request.password()));

        customer.setDateOfBirth(
                request.dateOfBirth());

        customer.setPhone(
                request.phone() == null ||
                request.phone().isBlank()
                        ? null
                        : request.phone().trim());

        customer.setStatus("ACTIVE");

        Customer saved =
                customerRepository.save(customer);

        return new RegisterResponse(
                saved.getId(),
                saved.getFullName(),
                saved.getEmail(),
                "Registration successful. You can now sign in.");
    }
}

