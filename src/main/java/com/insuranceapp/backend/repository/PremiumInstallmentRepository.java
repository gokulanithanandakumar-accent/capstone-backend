package com.insuranceapp.backend.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.insuranceapp.backend.entity.PremiumInstallment;

import jakarta.persistence.LockModeType;

public interface PremiumInstallmentRepository extends JpaRepository<PremiumInstallment, Long> {
    List<PremiumInstallment> findByPolicyIdOrderByInstallmentNumberAsc(Long policyId);

    List<PremiumInstallment> findByPolicyIdIn(Collection<Long> policyIds);

    /** Row-locks the policy's installments so concurrent payments cannot pay the same installment twice. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from PremiumInstallment i where i.policy.id = :policyId order by i.installmentNumber asc")
    List<PremiumInstallment> findByPolicyIdForUpdate(@Param("policyId") Long policyId);
}