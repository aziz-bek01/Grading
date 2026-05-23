package uz.hrlab.grading.common.persistence;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA auditing — populates {@code created_by} / {@code updated_by}
 * from the active {@link TenantContext}.
 *
 * <p>Returns empty when no context is active (e.g. background jobs not yet
 * wired) so the columns stay null instead of failing the request.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "tenantAuditorAware")
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<UUID> tenantAuditorAware() {
        return () -> {
            TenantContext ctx = TenantContextHolder.get();
            if (ctx == null || ctx.userId() == null) return Optional.empty();
            return Optional.of(ctx.userId());
        };
    }
}
