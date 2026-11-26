package com.insuranceapp.backend.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 100, message = "Full name must contain between 2 and 100 characters")
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        String email,

        @NotNull(message = "Date of birth is required")
        @PastOrPresent(message = "Date of birth cannot be in the future")
        LocalDate dateOfBirth,

        @Pattern(
                regexp = "^[0-9+() -]{7,20}$",
                message = "Phone number must be valid"
        )
        String phone,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 100, message = "Password must contain at least 8 characters")
        String password) {
}
