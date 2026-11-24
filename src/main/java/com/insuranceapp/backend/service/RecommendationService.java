package com.insuranceapp.backend.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.insuranceapp.backend.ai.AiRecommendationOutcome;
import com.insuranceapp.backend.ai.AiSuggestion;
import com.insuranceapp.backend.ai.InsuranceRecommendationAiClient;
import com.insuranceapp.backend.ai.ProductCandidate;
import com.insuranceapp.backend.ai.RecommendationPromptInput;
import com.insuranceapp.backend.config.RecommendationProperties;
import com.insuranceapp.backend.dto.RecommendationItemResponse;
import com.insuranceapp.backend.dto.RecommendationRequest;
import com.insuranceapp.backend.dto.RecommendationResponse;
import com.insuranceapp.backend.entity.AiRecommendationItem;
import com.insuranceapp.backend.entity.AiRecommendationRequest;
import com.insuranceapp.backend.entity.Customer;
import com.insuranceapp.backend.entity.InsuranceProduct;
import com.insuranceapp.backend.exception.ResourceNotFoundException;
import com.insuranceapp.backend.repository.AiRecommendationItemRepository;
import com.insuranceapp.backend.repository.AiRecommendationRequestRepository;
import com.insuranceapp.backend.repository.CustomerPolicyRepository;
import com.insuranceapp.backend.repository.CustomerRepository;
import com.insuranceapp.backend.repository.InsuranceProductRepository;

/**
 * FR-2 AI recommendations. Age, product eligibility and duplicate exclusion are deterministic; the AI
 * model only ranks and explains products that the application has already deemed eligible.
 */
@Service
public class RecommendationService {
    static final Set<String> SUITABILITY_LEVELS = Set.of("HIGH", "MEDIUM", "LOW");

    private final CustomerRepository customerRepository;
    private final InsuranceProductRepository productRepository;
    private final CustomerPolicyRepository customerPolicyRepository;
    private final AiRecommendationRequestRepository requestRepository;
    private final AiRecommendationItemRepository itemRepository;
    private final InsuranceRecommendationAiClient aiClient;
    private final RecommendationProperties properties;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public RecommendationService(CustomerRepository customerRepository, InsuranceProductRepository productRepository,
            CustomerPolicyRepository customerPolicyRepository, AiRecommendationRequestRepository requestRepository,
            AiRecommendationItemRepository itemRepository, InsuranceRecommendationAiClient aiClient,
            RecommendationProperties properties, Clock clock, PlatformTransactionManager transactionManager) {
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.customerPolicyRepository = customerPolicyRepository;
        this.requestRepository = requestRepository;
        this.itemRepository = itemRepository;
        this.aiClient = aiClient;
        this.properties = properties;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** Not wrapped in a single transaction so no database connection is held during the AI call. */
    public RecommendationResponse generateRecommendations(Long customerId, RecommendationRequest request) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found."));
        LocalDate dateOfBirth = Optional.ofNullable(request.dateOfBirth()).orElse(customer.getDateOfBirth());
        int age = calculateAge(dateOfBirth);

        Map<String, InsuranceProduct> eligibleByCode = findEligibleProducts(customerId, age).stream()
                .collect(Collectors.toMap(InsuranceProduct::getProductCode, Function.identity(),
                        (first, second) -> first, LinkedHashMap::new));
        if (eligibleByCode.isEmpty()) {
            return new RecommendationResponse(null, age, List.of());
        }

        RecommendationPromptInput promptInput = new RecommendationPromptInput(age, trimToNull(request.insuranceGoals()),
                trimToNull(request.preferredTerm()), trimToNull(request.preferredPolicyType()),
                eligibleByCode.values().stream().map(RecommendationService::toCandidate).toList(),
                properties.maxRecommendations());
        AiRecommendationOutcome outcome = aiClient.recommend(promptInput);
        List<ValidatedSuggestion> validated = validateSuggestions(outcome.suggestions(), eligibleByCode);

        return transactionTemplate.execute(status -> persist(customerId, promptInput, outcome, validated));
    }

    int calculateAge(LocalDate dateOfBirth) {
        return Period.between(dateOfBirth, LocalDate.now(clock)).getYears();
    }

    /** Active products, not already registered by the customer, whose entry-age range includes the customer. */
    private List<InsuranceProduct> findEligibleProducts(Long customerId, int age) {
        Set<Long> registeredProductIds = customerPolicyRepository.findAllWithProductByCustomerId(customerId).stream()
                .map(policy -> policy.getProduct().getId())
                .collect(Collectors.toSet());

        return productRepository.findByActiveTrueOrderByProductCodeAsc().stream()
                .filter(product -> !registeredProductIds.contains(product.getId()))
                .filter(product -> age >= product.getMinEntryAge() && age <= product.getMaxEntryAge())
                .toList();
    }

    /** Keeps only AI suggestions that reference an eligible database product, without duplicates. */
    private List<ValidatedSuggestion> validateSuggestions(List<AiSuggestion> suggestions,
            Map<String, InsuranceProduct> eligibleByCode) {
        Set<String> seenCodes = new HashSet<>();
        return suggestions.stream()
                .filter(suggestion -> suggestion.productCode() != null)
                .map(suggestion -> new AiSuggestion(suggestion.productCode().trim(),
                        trimToNull(suggestion.recommendationReason()),
                        suggestion.suitability() == null ? null
                                : suggestion.suitability().trim().toUpperCase(Locale.ROOT)))
                .filter(suggestion -> eligibleByCode.containsKey(suggestion.productCode()))
                .filter(suggestion -> suggestion.recommendationReason() != null)
                .filter(suggestion -> SUITABILITY_LEVELS.contains(suggestion.suitability()))
                .filter(suggestion -> seenCodes.add(suggestion.productCode()))
                .limit(properties.maxRecommendations())
                .map(suggestion -> new ValidatedSuggestion(eligibleByCode.get(suggestion.productCode()).getId(),
                        suggestion.recommendationReason(), suggestion.suitability()))
                .toList();
    }

    private RecommendationResponse persist(Long customerId, RecommendationPromptInput promptInput,
            AiRecommendationOutcome outcome, List<ValidatedSuggestion> suggestions) {
        AiRecommendationRequest recommendationRequest = new AiRecommendationRequest();
        recommendationRequest.setCustomer(customerRepository.getReferenceById(customerId));
        recommendationRequest.setInsuranceGoals(promptInput.insuranceGoals());
        recommendationRequest.setPreferredTerm(promptInput.preferredTerm());
        recommendationRequest.setPreferredPolicyType(promptInput.preferredPolicyType());
        recommendationRequest.setCalculatedAge(promptInput.customerAge());
        recommendationRequest.setModelName(properties.modelName());
        recommendationRequest.setModelVersion(outcome.reportedModel());
        recommendationRequest.setPromptVersion(properties.promptVersion());
        AiRecommendationRequest savedRequest = requestRepository.save(recommendationRequest);

        // Product must exist and still be active at generation time.
        Map<Long, InsuranceProduct> activeProducts = productRepository
                .findAllById(suggestions.stream().map(ValidatedSuggestion::productId).toList()).stream()
                .filter(InsuranceProduct::isActive)
                .collect(Collectors.toMap(InsuranceProduct::getId, Function.identity()));

        List<AiRecommendationItem> items = new ArrayList<>();
        for (ValidatedSuggestion suggestion : suggestions) {
            InsuranceProduct product = activeProducts.get(suggestion.productId());
            if (product == null) {
                continue;
            }
            AiRecommendationItem item = new AiRecommendationItem();
            item.setRecommendationRequest(savedRequest);
            item.setProduct(product);
            item.setRankOrder(items.size() + 1);
            item.setRecommendationReason(suggestion.reason());
            item.setSuitability(suggestion.suitability());
            item.setSelectedForRegistration(false);
            items.add(item);
        }

        List<RecommendationItemResponse> responses = itemRepository.saveAll(items).stream()
                .map(RecommendationService::toResponse)
                .toList();
        return new RecommendationResponse(savedRequest.getId(), promptInput.customerAge(), responses);
    }

    private static ProductCandidate toCandidate(InsuranceProduct product) {
        return new ProductCandidate(product.getProductCode(), product.getPolicyName(), product.getPolicyType(),
                product.getTermMonths(), product.getSumInsured(), product.getPremiumFrequency(),
                product.getPremiumAmount());
    }

    private static RecommendationItemResponse toResponse(AiRecommendationItem item) {
        InsuranceProduct product = item.getProduct();
        return new RecommendationItemResponse(item.getId(), item.getRankOrder(), product.getId(),
                product.getProductCode(), product.getPolicyName(), product.getPolicyType(), product.getTermMonths(),
                product.getSumInsured(), product.getPremiumFrequency(), product.getPremiumAmount(),
                item.getRecommendationReason(), item.getSuitability());
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record ValidatedSuggestion(Long productId, String reason, String suitability) {
    }
}
