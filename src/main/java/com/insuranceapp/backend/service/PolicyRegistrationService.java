package com.insuranceapp.backend.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.insuranceapp.backend.dto.PolicyRegistrationDetailsResponse;
import com.insuranceapp.backend.dto.PolicyRegistrationRequest;
import com.insuranceapp.backend.dto.PolicyRegistrationResponse;
import com.insuranceapp.backend.entity.AiRecommendationItem;
import com.insuranceapp.backend.entity.Customer;
import com.insuranceapp.backend.entity.CustomerPolicy;
import com.insuranceapp.backend.entity.InsuranceProduct;
import com.insuranceapp.backend.entity.PremiumInstallment;
import com.insuranceapp.backend.exception.BusinessRuleException;
import com.insuranceapp.backend.exception.DuplicatePolicyException;
import com.insuranceapp.backend.exception.ResourceNotFoundException;
import com.insuranceapp.backend.repository.AiRecommendationItemRepository;
import com.insuranceapp.backend.repository.CustomerPolicyRepository;
import com.insuranceapp.backend.repository.CustomerRepository;
import com.insuranceapp.backend.repository.InsuranceProductRepository;
import com.insuranceapp.backend.repository.PremiumInstallmentRepository;

/** FR-3 policy registration: validation, calculations, unique policy number and installment schedule. */
@Service
public class PolicyRegistrationService {
    static final String POLICY_NUMBER_PREFIX = "HDP-";
    static final String POLICY_STATUS_ACTIVE = "ACTIVE";
    static final String INSTALLMENT_STATUS_PENDING = "PENDING";

    private final CustomerRepository customerRepository;
    private final InsuranceProductRepository productRepository;
    private final CustomerPolicyRepository policyRepository;
    private final PremiumInstallmentRepository installmentRepository;
    private final AiRecommendationItemRepository recommendationItemRepository;
    private final PolicyCalculator calculator;
    private final Clock clock;

    public PolicyRegistrationService(CustomerRepository customerRepository,
            InsuranceProductRepository productRepository, CustomerPolicyRepository policyRepository,
            PremiumInstallmentRepository installmentRepository,
            AiRecommendationItemRepository recommendationItemRepository, PolicyCalculator calculator, Clock clock) {
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.policyRepository = policyRepository;
        this.installmentRepository = installmentRepository;
        this.recommendationItemRepository = recommendationItemRepository;
        this.calculator = calculator;
        this.clock = clock;
    }

    /** Values for the registration form; the start date defaults to the current date. */
    @Transactional(readOnly = true)
    public PolicyRegistrationDetailsResponse getRegistrationDetails(Long customerId, Long productId,
            LocalDate requestedStartDate) {
        Customer customer = findCustomer(customerId);
        InsuranceProduct product = findActiveProduct(productId);
        LocalDate startDate = Optional.ofNullable(requestedStartDate).orElseGet(this::today);
        validateStartDate(startDate);
        PolicyCalculator.PolicyTerms terms = calculateTerms(product, startDate);

        return new PolicyRegistrationDetailsResponse(customer.getFullName(), customer.getDateOfBirth(),
                customer.getEmail(), customer.getPhone(), product.getId(), product.getProductCode(),
                product.getPolicyName(), product.getPolicyType(), product.getTermMonths(), terms.startDate(),
                terms.maturityDate(), product.getPremiumFrequency(), terms.premiumAmount(), product.getSumInsured(),
                terms.totalPremiumPayable(), terms.numberOfInstallments());
    }

    @Transactional
    public PolicyRegistrationResponse register(Long customerId, PolicyRegistrationRequest request) {
        Customer customer = findCustomer(customerId);
        InsuranceProduct product = findActiveProduct(request.productId());
        validateStartDate(request.startDate());
        validateEntryAge(customer.getDateOfBirth(), product, request.startDate());
        PolicyCalculator.PolicyTerms terms = calculateTerms(product, request.startDate());

        if (policyRepository.existsByCustomerIdAndProductId(customerId, product.getId())) {
            throw new DuplicatePolicyException("You have already registered a policy for " + product.getPolicyName() + ".");
        }
        Optional.ofNullable(request.recommendationItemId())
                .map(itemId -> findRecommendationItem(itemId, customerId, product))
                .ifPresent(item -> item.setSelectedForRegistration(true));

        CustomerPolicy policy = new CustomerPolicy();
        policy.setPolicyNumber(nextPolicyNumber());
        policy.setCustomer(customer);
        policy.setProduct(product);
        policy.setStartDate(terms.startDate());
        policy.setMaturityDate(terms.maturityDate());
        policy.setPremiumFrequency(terms.frequency().name());
        policy.setPremiumAmount(terms.premiumAmount());
        policy.setSumInsured(product.getSumInsured());
        policy.setTotalPremiumPayable(terms.totalPremiumPayable());
        policy.setStatus(POLICY_STATUS_ACTIVE);
        CustomerPolicy savedPolicy = policyRepository.save(policy);

        List<PremiumInstallment> installments = calculator.buildSchedule(terms).stream()
                .map(scheduled -> toInstallment(savedPolicy, scheduled))
                .toList();
        installmentRepository.saveAll(installments);

        return new PolicyRegistrationResponse(savedPolicy.getPolicyNumber(), product.getPolicyName(),
                savedPolicy.getStartDate(), savedPolicy.getMaturityDate(), savedPolicy.getTotalPremiumPayable(),
                installments.size(), savedPolicy.getStatus(),
                "Policy registered successfully. Your policy identifier is " + savedPolicy.getPolicyNumber() + ".");
    }

    private Customer findCustomer(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found."));
    }

    private InsuranceProduct findActiveProduct(Long productId) {
        InsuranceProduct product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Insurance product not found."));
        if (!product.isActive()) {
            throw new BusinessRuleException("The selected insurance product is no longer available.");
        }
        return product;
    }

    private void validateStartDate(LocalDate startDate) {
        if (startDate.isBefore(today())) {
            throw new BusinessRuleException("Policy start date cannot be in the past.");
        }
    }

    /** Entry age is evaluated on the policy start date. */
    private void validateEntryAge(LocalDate dateOfBirth, InsuranceProduct product, LocalDate startDate) {
        if (dateOfBirth == null) {
            throw new BusinessRuleException("Date of birth is required to register a policy.");
        }
        if (dateOfBirth.isAfter(today())) {
            throw new BusinessRuleException("Date of birth cannot be in the future.");
        }
        int age = Period.between(dateOfBirth, startDate).getYears();
        if (age < product.getMinEntryAge() || age > product.getMaxEntryAge()) {
            throw new BusinessRuleException("You are not eligible for " + product.getPolicyName()
                    + ". Entry age must be between " + product.getMinEntryAge() + " and " + product.getMaxEntryAge()
                    + " years (your age on the start date: " + age + ").");
        }
    }

    private PolicyCalculator.PolicyTerms calculateTerms(InsuranceProduct product, LocalDate startDate) {
        return calculator.calculateTerms(startDate, product.getTermMonths(), product.getPremiumFrequency(),
                product.getPremiumAmount(), product.getSumInsured());
    }

    private AiRecommendationItem findRecommendationItem(Long itemId, Long customerId, InsuranceProduct product) {
        return recommendationItemRepository.findByIdAndRecommendationRequestCustomerId(itemId, customerId)
                .filter(item -> item.getProduct().getId().equals(product.getId()))
                .orElseThrow(() -> new BusinessRuleException("The selected recommendation is not valid for this product."));
    }

    /** Format HDP-YYYY-NNNN, sequential within the registration year. */
    private String nextPolicyNumber() {
        String prefix = POLICY_NUMBER_PREFIX + today().getYear() + "-";
        int next = policyRepository.findMaxPolicySequence(prefix) + 1;
        return prefix + String.format("%04d", next);
    }

    private static PremiumInstallment toInstallment(CustomerPolicy policy,
            PolicyCalculator.ScheduledInstallment scheduled) {
        PremiumInstallment installment = new PremiumInstallment();
        installment.setPolicy(policy);
        installment.setInstallmentNumber(scheduled.installmentNumber());
        installment.setDueDate(scheduled.dueDate());
        installment.setGracePeriodEndDate(scheduled.gracePeriodEndDate());
        installment.setScheduledAmount(scheduled.scheduledAmount());
        installment.setPaidAmount(BigDecimal.ZERO.setScale(2));
        installment.setPaymentStatus(INSTALLMENT_STATUS_PENDING);
        return installment;
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
