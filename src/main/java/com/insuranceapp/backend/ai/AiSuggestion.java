package com.insuranceapp.backend.ai;

/** A single recommendation as returned by the AI model, before server-side validation. */
public record AiSuggestion(String productCode, String recommendationReason, String suitability) {
}
