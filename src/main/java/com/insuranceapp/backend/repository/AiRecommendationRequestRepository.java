package com.insuranceapp.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.insuranceapp.backend.entity.AiRecommendationRequest;

public interface AiRecommendationRequestRepository extends JpaRepository<AiRecommendationRequest, Long> {
}
