package uz.hrlab.grading.tenancy.domain;

/** Lifecycle status of a tenant (control plane). */
public enum TenantStatus {
    PROVISIONING,
    ACTIVE,
    SUSPENDED,
    ARCHIVED
}
