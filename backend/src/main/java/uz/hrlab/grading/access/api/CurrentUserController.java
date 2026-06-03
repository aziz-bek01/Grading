package uz.hrlab.grading.access.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.hrlab.grading.access.application.GetCurrentUserUseCase;

/**
 * BE-TI-005 — exposes {@code GET /api/v1/users/me} to the frontend auth
 * bootstrap (see {@code frontend/src/shared/api/endpoints.ts}).
 *
 * <p>Security:
 * <ul>
 *   <li>No {@code @PreAuthorize} — every authenticated user MUST be able to
 *       fetch their own identity; tenant scope is enforced by the response
 *       payload (only the caller's own user row + the caller's own
 *       memberships).</li>
 *   <li>Anonymous callers are blocked upstream by {@code SecurityConfig}
 *       ({@code anyRequest().authenticated()}) — anonymous reaches here
 *       would yield 401 before this method runs.</li>
 *   <li>{@code tenant_id} is NOT taken from the URL/body — the use case reads
 *       it from {@code TenantContextHolder} which is populated from the JWT
 *       or dev-auth headers (security-blueprint §5.1).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/users")
public class CurrentUserController {

    private final GetCurrentUserUseCase getCurrentUserUseCase;

    public CurrentUserController(GetCurrentUserUseCase getCurrentUserUseCase) {
        this.getCurrentUserUseCase = getCurrentUserUseCase;
    }

    @GetMapping("/me")
    public CurrentUserResponse me() {
        return getCurrentUserUseCase.currentUser();
    }
}
