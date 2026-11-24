package com.insuranceapp.backend.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.insuranceapp.backend.dto.CustomerPolicyResponse;
import com.insuranceapp.backend.entity.CustomerPolicy;
import com.insuranceapp.backend.repository.CustomerPolicyRepository;

@Service
public class CustomerPolicyService {
    private final CustomerPolicyRepository customerPolicyRepository;

    public CustomerPolicyService(CustomerPolicyRepository customerPolicyRepository) {
        this.customerPolicyRepository = customerPolicyRepository;
    }

    @Transactional(readOnly = true)
    public List<CustomerPolicyResponse> getPoliciesForCustomer(Long customerId) {
        return customerPolicyRepository.findAllWithProductByCustomerId(customerId).stream()
                .map(CustomerPolicyService::toResponse)
                .toList();
    }

    private static CustomerPolicyResponse toResponse(CustomerPolicy policy) {
        var product = policy.getProduct();
        return new CustomerPolicyResponse(
                policy.getPolicyNumber(),
                product.getPolicyName(),
                product.getPolicyType(),
                product.getTermMonths(),
                policy.getStartDate(),
                policy.getMaturityDate(),
                policy.getPremiumFrequency(),
                policy.getPremiumAmount(),
                policy.getSumInsured(),
                policy.getStatus());
    }
}
