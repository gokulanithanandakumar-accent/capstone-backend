package com.insuranceapp.backend.service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.insuranceapp.backend.dto.AvailableProductResponse;
import com.insuranceapp.backend.entity.InsuranceProduct;
import com.insuranceapp.backend.repository.CustomerPolicyRepository;
import com.insuranceapp.backend.repository.InsuranceProductRepository;

@Service
public class InsuranceProductService {
    private final InsuranceProductRepository productRepository;
    private final CustomerPolicyRepository customerPolicyRepository;

    public InsuranceProductService(InsuranceProductRepository productRepository,
            CustomerPolicyRepository customerPolicyRepository) {
        this.productRepository = productRepository;
        this.customerPolicyRepository = customerPolicyRepository;
    }

    /**
     * Active products the customer has not yet registered. Duplicate rule: one policy per
     * (customer, product), regardless of policy status, matching UQ(customer_id, product_id).
     */
    @Transactional(readOnly = true)
    public List<AvailableProductResponse> getAvailableProducts(Long customerId) {
        Set<Long> registeredProductIds = customerPolicyRepository.findAllWithProductByCustomerId(customerId).stream()
                .map(policy -> policy.getProduct().getId())
                .collect(Collectors.toSet());

        return productRepository.findByActiveTrueOrderByProductCodeAsc().stream()
                .filter(product -> !registeredProductIds.contains(product.getId()))
                .map(InsuranceProductService::toResponse)
                .toList();
    }

    private static AvailableProductResponse toResponse(InsuranceProduct product) {
        return new AvailableProductResponse(
                product.getId(),
                product.getProductCode(),
                product.getPolicyName(),
                product.getPolicyType(),
                product.getTermMonths(),
                product.getSumInsured(),
                product.getPremiumFrequency(),
                product.getPremiumAmount());
    }
}
