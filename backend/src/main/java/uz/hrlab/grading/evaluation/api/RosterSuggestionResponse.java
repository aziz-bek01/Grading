package uz.hrlab.grading.evaluation.api;

import java.util.List;
import java.util.UUID;

/**
 * BE-5 — ADVISORY roster-suggestion payload for the panel-setup dialog. Lists
 * candidate department directors for {@code departmentId}, resolved from
 * {@code user_department_scopes} ∩ the dept-scoped role
 * ({@code DepartmentScopePolicy.isDepartmentScoped} — single source of truth).
 *
 * <p>ADVISORY ONLY: the server still re-validates membership on the actual
 * {@code AssignEvaluatorUseCase}; this payload is a UI convenience. snake_case on
 * the wire: {@code department_id}, {@code dept_director_candidates},
 * {@code user_id}, {@code full_name}.
 *
 * <p>The HR_DIRECTOR default is NOT here — the FE filters {@code CLIENT_HR_DIRECTOR}
 * from the existing {@code GET /users} (role_codes[] already returned).
 */
public record RosterSuggestionResponse(
        UUID departmentId,
        List<Candidate> deptDirectorCandidates
) {
    public record Candidate(UUID userId, String fullName) {
    }
}
