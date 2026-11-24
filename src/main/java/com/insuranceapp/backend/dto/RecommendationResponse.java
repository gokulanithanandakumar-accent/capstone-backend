package com.insuranceapp.backend.dto;

import java.util.List;

/** recommendationRequestId is null when no eligible products existed and the AI model was not called. */
public record RecommendationResponse(
        Long recommendationRequestId,
        int calculatedAge,
        List<RecommendationItemResponse> recommendations) {
}
