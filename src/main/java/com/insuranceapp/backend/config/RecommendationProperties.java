package com.insuranceapp.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/** FR-2 recommendation settings; model and prompt identifiers are persisted for auditability. */
@Validated
@ConfigurationProperties(prefix = "app.ai.recommendation")
public record RecommendationProperties(
        @NotBlank String modelName,
        @NotBlank String promptVersion,
        @Min(1) int maxRecommendations) {
}
