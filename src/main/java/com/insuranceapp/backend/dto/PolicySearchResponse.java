package com.insuranceapp.backend.dto;

import java.util.List;

/** A bounded page of FR-5 search results. */
public record PolicySearchResponse(
        List<PolicySearchResult> results,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}