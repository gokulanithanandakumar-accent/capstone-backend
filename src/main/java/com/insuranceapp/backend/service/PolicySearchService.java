package com.insuranceapp.backend.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.insuranceapp.backend.dto.PolicySearchRequest;
import com.insuranceapp.backend.dto.PolicySearchResponse;
import com.insuranceapp.backend.dto.PolicySearchResult;
import com.insuranceapp.backend.entity.CustomerPolicy;
import com.insuranceapp.backend.entity.PremiumInstallment;
import com.insuranceapp.backend.entity.PremiumPayment;
import com.insuranceapp.backend.exception.InvalidSearchCriteriaException;
import com.insuranceapp.backend.repository.CustomerPolicyRepository;
import com.insuranceapp.backend.repository.PremiumInstallmentRepository;
import com.insuranceapp.backend.repository.PremiumPaymentRepository;
import com.insuranceapp.backend.service.PolicySearchCriteria.ByCustomerName;
import com.insuranceapp.backend.service.PolicySearchCriteria.ByPolicyNumber;
import com.insuranceapp.backend.service.PolicySearchCriteria.ByPolicyNumberAndDateOfBirth;

/** FR-5 customer insurance policy search, restricted to the authenticated customer's own policies. */
@Service
public class PolicySearchService {
    static final int DEFAULT_PAGE_SIZE = 10;
    static final int MIN_NAME_LENGTH = 2;
    static final String NO_CRITERIA = "Enter a Policy Identifier or Customer Name to search.";
    static final String DATE_OF_BIRTH_WITHOUT_POLICY =
            "Date of Birth can only be used together with Policy Identifier.";
    static final String UNSUPPORTED_COMBINATION =
            "Search by Policy Identifier, Customer Name, or Policy Identifier and Date of Birth.";
    static final String NAME_TOO_SHORT = "Customer name must contain at least " + MIN_NAME_LENGTH + " characters.";
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final CustomerPolicyRepository policyRepository;
    private final PremiumInstallmentRepository installmentRepository;
    private final PremiumPaymentRepository paymentRepository;
    private final PremiumPaymentCalculator calculator;
    private final Clock clock;

    public PolicySearchService(CustomerPolicyRepository policyRepository,
            PremiumInstallmentRepository installmentRepository, PremiumPaymentRepository paymentRepository,
            PremiumPaymentCalculator calculator, Clock clock) {
        this.policyRepository = policyRepository;
        this.installmentRepository = installmentRepository;
        this.paymentRepository = paymentRepository;
        this.calculator = calculator;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PolicySearchResponse search(Long customerId, PolicySearchRequest request) {
        PolicySearchCriteria criteria = resolveCriteria(request);
        Pageable pageable = PageRequest.of(request.page() == null ? 0 : request.page(),
                request.size() == null ? DEFAULT_PAGE_SIZE : request.size());

        Page<CustomerPolicy> policies = switch (criteria) {
            case ByPolicyNumber(String policyNumber) ->
                    single(policyRepository.findOwnedPolicy(policyNumber, customerId), pageable);
            case ByPolicyNumberAndDateOfBirth(String policyNumber, LocalDate dateOfBirth) ->
                    single(policyRepository.findOwnedPolicy(policyNumber, customerId)
                            .filter(policy -> dateOfBirth.equals(policy.getCustomer().getDateOfBirth())), pageable);
            case ByCustomerName(String customerName) -> policyRepository.searchOwnedByCustomerName(customerId,
                    "%" + customerName.toLowerCase(Locale.ROOT) + "%", pageable);
        };

        List<Long> policyIds = policies.getContent().stream().map(CustomerPolicy::getId).toList();
        Map<Long, List<PremiumInstallment>> installmentsByPolicy = policyIds.isEmpty() ? Map.of()
                : installmentRepository.findByPolicyIdIn(policyIds).stream()
                        .collect(Collectors.groupingBy(installment -> installment.getPolicy().getId()));
        Map<Long, BigDecimal> penaltiesByInstallment = policyIds.isEmpty() ? Map.of()
                : paymentRepository.findAllByPolicyIdIn(policyIds).stream()
                        .collect(Collectors.groupingBy(payment -> payment.getInstallment().getId(),
                                Collectors.reducing(ZERO, PremiumPayment::getPenaltyAmount, BigDecimal::add)));
        LocalDate today = LocalDate.now(clock);

        List<PolicySearchResult> results = policies.getContent().stream()
                .map(policy -> toResult(policy, installmentsByPolicy.getOrDefault(policy.getId(), List.of()),
                        penaltiesByInstallment, today))
                .toList();
        return new PolicySearchResponse(results, pageable.getPageNumber(), pageable.getPageSize(),
                policies.getTotalElements(), policies.getTotalPages());
    }

    /** Validates the supplied fields and maps them to exactly one supported search combination. */
    PolicySearchCriteria resolveCriteria(PolicySearchRequest request) {
        String policyNumber = normalize(request.policyNumber());
        String customerName = normalize(request.customerName());
        LocalDate dateOfBirth = request.dateOfBirth();
        if (customerName != null && customerName.length() < MIN_NAME_LENGTH) {
            throw new InvalidSearchCriteriaException(NAME_TOO_SHORT);
        }
        if (policyNumber == null && customerName == null) {
            throw new InvalidSearchCriteriaException(dateOfBirth == null ? NO_CRITERIA : DATE_OF_BIRTH_WITHOUT_POLICY);
        }
        if (policyNumber != null && customerName != null) {
            throw new InvalidSearchCriteriaException(UNSUPPORTED_COMBINATION);
        }
        if (customerName != null) {
            if (dateOfBirth != null) {
                throw new InvalidSearchCriteriaException(DATE_OF_BIRTH_WITHOUT_POLICY);
            }
            return new ByCustomerName(customerName);
        }
        String upperPolicyNumber = policyNumber.toUpperCase(Locale.ROOT);
        return dateOfBirth == null ? new ByPolicyNumber(upperPolicyNumber)
                : new ByPolicyNumberAndDateOfBirth(upperPolicyNumber, dateOfBirth);
    }

    private PolicySearchResult toResult(CustomerPolicy policy, List<PremiumInstallment> installments,
            Map<Long, BigDecimal> penaltiesByInstallment, LocalDate today) {
        List<PremiumPaymentCalculator.InstallmentPosition> positions = installments.stream()
                .map(installment -> calculator.position(installment,
                        penaltiesByInstallment.getOrDefault(installment.getId(), ZERO), today))
                .toList();
        BigDecimal premiumPaid = positions.stream().map(PremiumPaymentCalculator.InstallmentPosition::premiumPaid)
                .reduce(ZERO, BigDecimal::add);
        return new PolicySearchResult(policy.getPolicyNumber(), policy.getCustomer().getFullName(),
                policy.getCustomer().getDateOfBirth(), policy.getProduct().getPolicyName(),
                policy.getProduct().getPolicyType(), policy.getStartDate(), policy.getMaturityDate(),
                policy.getTotalPremiumPayable(), calculator.policyPaymentStatus(positions).name(), premiumPaid,
                policy.getTotalPremiumPayable().subtract(premiumPaid).max(ZERO));
    }

    private static Page<CustomerPolicy> single(Optional<CustomerPolicy> policy, Pageable pageable) {
        List<CustomerPolicy> matches = policy.stream().toList();
        return new PageImpl<>(pageable.getOffset() < matches.size() ? matches : List.of(), pageable, matches.size());
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim().replaceAll("\\s+", " ");
        return trimmed.isEmpty() ? null : trimmed;
    }
}