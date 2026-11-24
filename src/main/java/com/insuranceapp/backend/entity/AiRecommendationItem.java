package com.insuranceapp.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "ai_recommendation_item",
        uniqueConstraints = @UniqueConstraint(name = "uk_ai_item_request_product", columnNames = {"recommendation_request_id", "product_id"}))
public class AiRecommendationItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recommendation_item_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recommendation_request_id", nullable = false)
    private AiRecommendationRequest recommendationRequest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private InsuranceProduct product;

    @Column(name = "rank_order", nullable = false)
    private int rankOrder;

    @Column(name = "recommendation_reason", nullable = false, columnDefinition = "text")
    private String recommendationReason;

    @Column(nullable = false)
    private String suitability;

    @Column(name = "selected_for_registration", nullable = false)
    private boolean selectedForRegistration;

    public Long getId() { return id; }
    public AiRecommendationRequest getRecommendationRequest() { return recommendationRequest; }
    public void setRecommendationRequest(AiRecommendationRequest recommendationRequest) { this.recommendationRequest = recommendationRequest; }
    public InsuranceProduct getProduct() { return product; }
    public void setProduct(InsuranceProduct product) { this.product = product; }
    public int getRankOrder() { return rankOrder; }
    public void setRankOrder(int rankOrder) { this.rankOrder = rankOrder; }
    public String getRecommendationReason() { return recommendationReason; }
    public void setRecommendationReason(String recommendationReason) { this.recommendationReason = recommendationReason; }
    public String getSuitability() { return suitability; }
    public void setSuitability(String suitability) { this.suitability = suitability; }
    public boolean isSelectedForRegistration() { return selectedForRegistration; }
    public void setSelectedForRegistration(boolean selectedForRegistration) { this.selectedForRegistration = selectedForRegistration; }
}
