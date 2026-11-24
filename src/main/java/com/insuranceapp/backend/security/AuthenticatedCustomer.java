package com.insuranceapp.backend.security;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;

public final class AuthenticatedCustomer {
    private AuthenticatedCustomer() {
    }

    /** Resolves the customer id from the authenticated JWT subject. */
    public static Long customerId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new BadCredentialsException("Authentication token has no subject");
        }
        try {
            return Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException ex) {
            throw new BadCredentialsException("Authentication token has an invalid subject", ex);
        }
    }
}
