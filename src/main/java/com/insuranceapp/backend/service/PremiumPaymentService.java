package com.insuranceapp.backend.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.insuranceapp.backend.dto.PolicyPremiumDetailsResponse;
import com.insuranceapp.backend.dto.PremiumInstallmentResponse;
import com.insuranceapp.backend.dto.PremiumPaymentHistoryResponse;
import com.insuranceapp.backend.dto.PremiumPaymentRequest;
import com.insuranceapp.backend.dto.PremiumPaymentResponse;
import com.insuranceapp.backend.dto.PremiumPolicySummaryResponse;
import com.insuranceapp.backend.entity.CustomerPolicy;
import com.insuranceapp.backend.entity.PremiumInstallment;
import com.insuranceapp.backend.entity.PremiumPayment;
import com.insuranceapp.backend.exception.DuplicatePaymentException;
import com.insuranceapp.backend.exception.ResourceNotFoundException;
import com.insuranceapp.backend.repository.CustomerPolicyRepository;
import com.insuranceapp.backend.repository.PremiumInstallmentRepository;
import com.insuranceapp.backend.repository.PremiumPaymentRepository;

/** FR-4 policy search, premium schedule and premium payment processing. */
@Service
public class PremiumPaymentService {
    static final String POLICY_STATUS_FULLY_PAID = "FULLY_PAID";
    static final String TRANSACTION_PREFIX = "TXN-HD-";
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final CustomerPolicyRepository policyRepository;
    private final PremiumInstallmentRepository installmentRepository;
    private final PremiumPaymentRepository paymentRepository;
    private final PremiumPaymentCalculator calculator;
    private final Clock clock;

    public PremiumPaymentService(CustomerPolicyRepository policyRepository,
            PremiumInstallmentRepository installmentRepository, PremiumPaymentRepository paymentRepository,
            PremiumPaymentCalculator calculator, Clock clock) {
        this.policyRepository = policyRepository;
        this.installmentRepository = installmentRepository;
        this.paymentRepository = paymentRepository;
        this.calculator = calculator;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PolicyPremiumDetailsResponse getPremiumDetails(Long customerId, String policyNumber) {
        CustomerPolicy policy = findPolicy(customerId, policyNumber);
        List<PremiumInstallment> installments = installmentRepository
                .findByPolicyIdOrderByInstallmentNumberAsc(policy.getId());
        List<PremiumPayment> payments = paymentRepository.findAllByPolicyId(policy.getId());
        Map<Long, BigDecimal> penalties = penaltiesByInstallment(payments);
        LocalDate today = today();

        List<PremiumInstallmentResponse> schedule = installments.stream()
                .map(installment -> toInstallmentResponse(installment,
                        calculator.position(installment, penalties.getOrDefault(installment.getId(), ZERO), today)))
                .toList();
        BigDecimal premiumPaid = schedule.stream()
                .map(row -> row.paidAmount().subtract(row.penaltyPaid()))
                .reduce(ZERO, BigDecimal::add);
        BigDecimal penaltyPaid = schedule.stream().map(PremiumInstallmentResponse::penaltyPaid)
                .reduce(ZERO, BigDecimal::add);

        PremiumPolicySummaryResponse summary = new PremiumPolicySummaryResponse(policy.getPolicyNumber(),
                policy.getCustomer().getFullName(), policy.getProduct().getPolicyName(),
                policy.getProduct().getTermMonths(), policy.getStartDate(), policy.getMaturityDate(),
                policy.getPremiumFrequency(), policy.getStatus(), policy.getTotalPremiumPayable(), premiumPaid,
                policy.getTotalPremiumPayable().subtract(premiumPaid).max(ZERO), penaltyPaid);
        List<PremiumPaymentHistoryResponse> history = payments.stream().map(PremiumPaymentService::toHistory).toList();
        return new PolicyPremiumDetailsResponse(summary, schedule, history);
    }

    @Transactional
    public PremiumPaymentResponse pay(Long customerId, String policyNumber, PremiumPaymentRequest request) {
        CustomerPolicy policy = findPolicy(customerId, policyNumber);
        
        Set<Long> selected = new HashSet<>();
        request.payments().forEach(payment -> {
            if (!selected.add(payment.installmentId())) {
                throw new DuplicatePaymentException("The same installment cannot be paid more than once in a payment.");
            }
        });

        List<PremiumInstallment> installments = installmentRepository.findByPolicyIdForUpdate(policy.getId());
        boolean allInstallmentsPaid = !installments.isEmpty()
        && installments.stream()
                .allMatch(installment ->
                        InstallmentStatus.PAID.name()
                                .equals(installment.getPaymentStatus()));

        if (allInstallmentsPaid) {
        throw new DuplicatePaymentException(
                "All premium installments for policy "
                        + policy.getPolicyNumber()
                        + " have already been paid.");
        }
        Map<Long, PremiumInstallment> installmentsById = installments.stream()
                .collect(Collectors.toMap(PremiumInstallment::getId, Function.identity()));
        Map<Long, BigDecimal> penalties = penaltiesByInstallment(paymentRepository.findAllByPolicyId(policy.getId()));
        LocalDate paymentDate = today();
        long sequence = paymentRepository.findMaxTransactionSequence();

        Map<Long, Integer> numbers = new HashMap<>();
        request.payments().forEach(payment -> numbers.put(payment.installmentId(),
                installmentNumber(installmentsById, payment, policy)));
        List<PremiumPaymentRequest.InstallmentPayment> ordered = request.payments().stream()
                .sorted(Comparator.comparingInt(payment -> numbers.get(payment.installmentId())))
                .toList();
        List<PremiumPayment> newPayments = new ArrayList<>();
        List<PremiumPaymentResponse.PaymentReceipt> receipts = new ArrayList<>();
        for (PremiumPaymentRequest.InstallmentPayment requested : ordered) {
            PremiumInstallment installment = installmentsById.get(requested.installmentId());
            PremiumPaymentCalculator.PaymentApplication application = calculator.applyPayment(installment,
                    penalties.getOrDefault(installment.getId(), ZERO), requested.amount(), paymentDate);
            installment.setPaidAmount(application.newPaidAmount());
            installment.setPaymentStatus(application.newStatus().name());

            PremiumPayment payment = new PremiumPayment();
            payment.setInstallment(installment);
            payment.setTransactionReference(TRANSACTION_PREFIX + String.format("%06d", ++sequence));
            payment.setPaymentDate(paymentDate);
            payment.setAmountPaid(application.amountPaid());
            payment.setPenaltyAmount(application.penaltyAmount());
            payment.setPaymentMethod(request.paymentMethod().name());
            newPayments.add(payment);
            receipts.add(new PremiumPaymentResponse.PaymentReceipt(payment.getTransactionReference(),
                    installment.getInstallmentNumber(), application.amountPaid(), application.penaltyAmount(),
                    application.remainingOutstanding(), application.newStatus().name()));
        }
        paymentRepository.saveAll(newPayments);

        boolean allPaid = installments.stream()
                .allMatch(installment -> InstallmentStatus.PAID.name().equals(installment.getPaymentStatus()));
        if (allPaid) {
            policy.setStatus(POLICY_STATUS_FULLY_PAID);
        }

        BigDecimal totalPaid = receipts.stream().map(PremiumPaymentResponse.PaymentReceipt::amountPaid)
                .reduce(ZERO, BigDecimal::add);
        BigDecimal totalPenalty = receipts.stream().map(PremiumPaymentResponse.PaymentReceipt::penaltyAmount)
                .reduce(ZERO, BigDecimal::add);
        String message = "Payment successful. " + receipts.size() + " installment(s) updated."
                + (allPaid ? " All premium installments are paid; the policy is now fully paid." : "");
        return new PremiumPaymentResponse(policy.getPolicyNumber(), paymentDate, receipts, totalPaid, totalPenalty,
                policy.getStatus(), message);
    }

    private CustomerPolicy findPolicy(Long customerId, String policyNumber) {
        String normalized = policyNumber == null ? "" : policyNumber.trim().toUpperCase(Locale.ROOT);
        return policyRepository.findOwnedPolicy(normalized, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("No policy found with identifier " + normalized + "."));
    }

    private static int installmentNumber(Map<Long, PremiumInstallment> installmentsById,
            PremiumPaymentRequest.InstallmentPayment payment, CustomerPolicy policy) {
        PremiumInstallment installment = installmentsById.get(payment.installmentId());
        if (installment == null) {
            throw new ResourceNotFoundException(
                    "Installment " + payment.installmentId() + " does not belong to policy " + policy.getPolicyNumber() + ".");
        }
        return installment.getInstallmentNumber();
    }

    private static Map<Long, BigDecimal> penaltiesByInstallment(List<PremiumPayment> payments) {
        return payments.stream().collect(Collectors.groupingBy(payment -> payment.getInstallment().getId(),
                Collectors.reducing(ZERO, PremiumPayment::getPenaltyAmount, BigDecimal::add)));
    }

    private static PremiumInstallmentResponse toInstallmentResponse(PremiumInstallment installment,
            PremiumPaymentCalculator.InstallmentPosition position) {
        return new PremiumInstallmentResponse(installment.getId(), installment.getInstallmentNumber(),
                position.premiumPeriod(), installment.getScheduledAmount(), installment.getDueDate(),
                installment.getGracePeriodEndDate(), installment.getPaidAmount(), position.penaltyPaid(),
                position.outstandingPremium(), position.applicablePenalty(), position.amountDue(),
                position.status().name(), position.status().isPayable());
    }

    private static PremiumPaymentHistoryResponse toHistory(PremiumPayment payment) {
        return new PremiumPaymentHistoryResponse(payment.getTransactionReference(),
                payment.getInstallment().getInstallmentNumber(), payment.getPaymentDate(), payment.getAmountPaid(),
                payment.getPenaltyAmount(), payment.getPaymentMethod());
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}