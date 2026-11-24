package com.insuranceapp.backend.ai;

import java.util.List;

/** Customer context and eligible products passed to the AI model. */
public record RecommendationPromptInput(
        int customerAge,
        String insuranceGoals,
        String preferredTerm,
        String preferredPolicyType,
        List<ProductCandidate> availableProducts,
        int maxRecommendations) {
}
