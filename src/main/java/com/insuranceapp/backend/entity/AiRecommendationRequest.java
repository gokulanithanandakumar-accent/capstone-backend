package com.insuranceapp.backend.entity;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "ai_recommendation_request", indexes = {
        @Index(name = "idx_ai_request_customer", columnList = "customer_id"),
        @Index(name = "idx_ai_request_requested_at", columnList = "requested_at")})
public class AiRecommendationRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recommendation_request_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "insurance_goals", columnDefinition = "text")
    private String insuranceGoals;

    @Column(name = "preferred_term")
    private String preferredTerm;

    @Column(name = "preferred_policy_type")
    private String preferredPolicyType;

    @Column(name = "calculated_age", nullable = false)
    private int calculatedAge;

    @Column(name = "model_name", nullable = false)
    private String modelName;

    @Column(name = "model_version")
    private String modelVersion;

    @Column(name = "prompt_version", nullable = false)
    private String promptVersion;

    @CreationTimestamp
    @Column(name = "requested_at", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private Instant requestedAt;

    public Long getId() { return id; }
    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }
    public String getInsuranceGoals() { return insuranceGoals; }
    public void setInsuranceGoals(String insuranceGoals) { this.insuranceGoals = insuranceGoals; }
    public String getPreferredTerm() { return preferredTerm; }
    public void setPreferredTerm(String preferredTerm) { this.preferredTerm = preferredTerm; }
    public String getPreferredPolicyType() { return preferredPolicyType; }
    public void setPreferredPolicyType(String preferredPolicyType) { this.preferredPolicyType = preferredPolicyType; }
    public int getCalculatedAge() { return calculatedAge; }
    public void setCalculatedAge(int calculatedAge) { this.calculatedAge = calculatedAge; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }
    public String getPromptVersion() { return promptVersion; }
    public void setPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }
    public Instant getRequestedAt() { return requestedAt; }
}
