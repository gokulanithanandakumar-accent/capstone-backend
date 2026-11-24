package com.insuranceapp.backend.service;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** Premium payment frequencies used by the insurance product catalog. */
public enum PremiumFrequency {
    MONTHLY,
    QUARTERLY,
    YEARLY;

    public int monthsPerInstallment() {
        return switch (this) {
            case MONTHLY -> 1;
            case QUARTERLY -> 3;
            case YEARLY -> 12;
        };
    }

    public static Optional<PremiumFrequency> from(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(frequency -> frequency.name().equals(normalized)).findFirst();
    }
}
