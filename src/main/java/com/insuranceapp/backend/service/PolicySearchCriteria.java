package com.insuranceapp.backend.service;

import java.time.LocalDate;

/** The three FR-5 search combinations: Policy Identifier, Customer Name, or Policy Identifier with Date of Birth. */
public sealed interface PolicySearchCriteria {
    record ByPolicyNumber(String policyNumber) implements PolicySearchCriteria {
    }

    record ByCustomerName(String customerName) implements PolicySearchCriteria {
    }

    record ByPolicyNumberAndDateOfBirth(String policyNumber, LocalDate dateOfBirth) implements PolicySearchCriteria {
    }
}