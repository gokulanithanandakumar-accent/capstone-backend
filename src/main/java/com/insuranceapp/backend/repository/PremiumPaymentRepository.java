package com.insuranceapp.backend.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.insuranceapp.backend.entity.PremiumPayment;

public interface PremiumPaymentRepository extends JpaRepository<PremiumPayment, Long> {
    @Query("""
            select p from PremiumPayment p
            join fetch p.installment i
            where i.policy.id = :policyId
            order by p.paymentDate asc, p.id asc
            """)
    List<PremiumPayment> findAllByPolicyId(@Param("policyId") Long policyId);

    @Query("""
            select p from PremiumPayment p
            join fetch p.installment i
            where i.policy.id in :policyIds
            """)
    List<PremiumPayment> findAllByPolicyIdIn(@Param("policyIds") Collection<Long> policyIds);

    /** Highest numeric suffix of transaction references in the TXN-HD-NNNNNN format, or 0. */
    @Query(value = """
            select coalesce(max(cast(substring(transaction_reference from 8) as bigint)), 0)
            from premium_payment
            where transaction_reference ~ '^TXN-HD-[0-9]+$'
            """, nativeQuery = true)
    long findMaxTransactionSequence();
}