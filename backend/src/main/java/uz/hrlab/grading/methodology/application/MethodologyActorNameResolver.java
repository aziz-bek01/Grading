package uz.hrlab.grading.methodology.application;

import org.springframework.stereotype.Component;
import uz.hrlab.grading.access.infrastructure.UserRepository;

import java.util.UUID;

/**
 * Resolves user display names from their UUID for audit-friendly UX
 * (defect D-407 / F-411). The methodology read paths inject this to
 * populate {@code lockedByName} / {@code approvedByName} on
 * {@link uz.hrlab.grading.methodology.api.MethodologyVersionResponse}.
 *
 * <p>Lookup uses {@code public.users} which is the control-plane PII table
 * (no tenant_id). Returns {@code null} when the user is unknown — the
 * frontend falls back to an "Unknown actor" label.
 */
@Component
public class MethodologyActorNameResolver {

    private final UserRepository users;

    public MethodologyActorNameResolver(UserRepository users) {
        this.users = users;
    }

    /** @return the user's full name, or {@code null} if id is null or unknown. */
    public String resolve(UUID userId) {
        if (userId == null) {
            return null;
        }
        return users.findById(userId)
                .map(u -> u.getFullName())
                .orElse(null);
    }
}
