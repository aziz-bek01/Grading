package uz.hrlab.grading.access.domain;

/** Lifecycle status of a user (database-blueprint §5.1 {@code public.users}). */
public enum UserStatus {
    ACTIVE,
    INVITED,
    DISABLED,
    LOCKED
}
