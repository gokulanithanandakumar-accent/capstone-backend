package com.insuranceapp.backend.service;

/** Premium installment payment states defined by FR-4. */
public enum InstallmentStatus {
    PAID, PENDING, PARTIALLY_PAID, OVERDUE;

    /** Only installments with an outstanding amount can be selected for payment. */
    public boolean isPayable() {
        return switch (this) {
            case PAID -> false;
            case PENDING, PARTIALLY_PAID, OVERDUE -> true;
        };
    }
}