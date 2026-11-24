package com.insuranceapp.backend.ai;

import java.util.List;

/** Structured output schema the AI model must follow. */
public record AiRecommendationResult(List<AiSuggestion> recommendations) {
}
