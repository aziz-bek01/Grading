package uz.hrlab.grading.tenancy.domain;

/**
 * How a tenant's data is physically isolated. See architecture §7 and
 * database-blueprint §2.4 selection rules.
 */
public enum IsolationMode {
    /** Shared database, dedicated schema per tenant. Default for MVP 1. */
    SCHEMA,
    /** Dedicated database per tenant. Premium / enterprise plans. */
    DATABASE
}
