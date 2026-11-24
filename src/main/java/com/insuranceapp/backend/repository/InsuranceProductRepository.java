package com.insuranceapp.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.insuranceapp.backend.entity.InsuranceProduct;

public interface InsuranceProductRepository extends JpaRepository<InsuranceProduct, Long> {
    List<InsuranceProduct> findByActiveTrueOrderByProductCodeAsc();

    boolean existsByProductCode(String productCode);
}
