package com.insuranceapp.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.insuranceapp.backend.entity.CustomerPolicy;

public interface CustomerPolicyRepository extends JpaRepository<CustomerPolicy, Long> {
    @Query("""
            select p from CustomerPolicy p
            join fetch p.product
            where p.customer.id = :customerId
            order by p.startDate desc, p.id desc
            """)
    List<CustomerPolicy> findAllWithProductByCustomerId(@Param("customerId") Long customerId);

    boolean existsByCustomerIdAndProductId(Long customerId, Long productId);

    /** Policy lookup restricted to its owner, so customers cannot view or pay other customers' policies. */
    @Query("""
            select p from CustomerPolicy p
            join fetch p.product
            join fetch p.customer c
            where p.policyNumber = :policyNumber and c.id = :customerId
            """)
    Optional<CustomerPolicy> findOwnedPolicy(@Param("policyNumber") String policyNumber,
            @Param("customerId") Long customerId);

    /** FR-5 customer-name search (case-insensitive LIKE pattern), restricted to the owner and paginated. */
    @Query(value = """
            select p from CustomerPolicy p
            join fetch p.product
            join fetch p.customer c
            where c.id = :customerId and lower(c.fullName) like :namePattern
            order by p.startDate desc, p.id desc
            """, countQuery = """
            select count(p) from CustomerPolicy p
            where p.customer.id = :customerId and lower(p.customer.fullName) like :namePattern
            """)
    Page<CustomerPolicy> searchOwnedByCustomerName(@Param("customerId") Long customerId,
            @Param("namePattern") String namePattern, Pageable pageable);

    /** Highest numeric suffix among policy numbers with the given prefix (e.g. "HDP-2026-"), or 0. */
    @Query(value = """
            select coalesce(max(cast(substring(policy_number from length(:prefix) + 1) as integer)), 0)
            from customer_policy
            where policy_number like :prefix || '%'
              and substring(policy_number from length(:prefix) + 1) ~ '^[0-9]+$'
            """, nativeQuery = true)
    int findMaxPolicySequence(@Param("prefix") String prefix);
}
