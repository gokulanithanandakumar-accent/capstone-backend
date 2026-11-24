package com.insuranceapp.backend.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.hibernate.annotations.Check;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "premium_installment",
        uniqueConstraints = @UniqueConstraint(name = "uk_installment_policy_number", columnNames = {"policy_id", "installment_number"}))
@Check(constraints = "paid_amount >= 0")
public class PremiumInstallment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "installment_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id", nullable = false)
    private CustomerPolicy policy;

    @Column(name = "installment_number", nullable = false)
    private int installmentNumber;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "grace_period_end_date", nullable = false)
    private LocalDate gracePeriodEndDate;

    @Column(name = "scheduled_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal scheduledAmount;

    @Column(name = "paid_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal paidAmount;

    @Column(name = "payment_status", nullable = false)
    private String paymentStatus;

    public Long getId() { return id; }
    public CustomerPolicy getPolicy() { return policy; }
    public void setPolicy(CustomerPolicy policy) { this.policy = policy; }
    public int getInstallmentNumber() { return installmentNumber; }
    public void setInstallmentNumber(int installmentNumber) { this.installmentNumber = installmentNumber; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public LocalDate getGracePeriodEndDate() { return gracePeriodEndDate; }
    public void setGracePeriodEndDate(LocalDate gracePeriodEndDate) { this.gracePeriodEndDate = gracePeriodEndDate; }
    public BigDecimal getScheduledAmount() { return scheduledAmount; }
    public void setScheduledAmount(BigDecimal scheduledAmount) { this.scheduledAmount = scheduledAmount; }
    public BigDecimal getPaidAmount() { return paidAmount; }
    public void setPaidAmount(BigDecimal paidAmount) { this.paidAmount = paidAmount; }
    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }
}
