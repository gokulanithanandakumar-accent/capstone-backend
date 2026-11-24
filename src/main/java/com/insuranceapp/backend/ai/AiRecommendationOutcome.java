package com.insuranceapp.backend.ai;

import java.util.List;

/** Parsed AI response plus the model identifier reported by the provider (may be null). */
public record AiRecommendationOutcome(List<AiSuggestion> suggestions, String reportedModel) {
}
