package uz.hrlab.grading.common.persistence;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA auditing — populates {@code created_by} / {@code updated_by}
 * from the active {@link TenantContext} and {@code created_at} /
 * {@code updated_at} from {@link #auditingDateTimeProvider()}.
 *
 * <p>Returns empty when no context is active (e.g. background jobs not yet
 * wired) so the {@code *_by} columns stay null instead of failing the request.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "tenantAuditorAware",
        dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<UUID> tenantAuditorAware() {
        return () -> {
            TenantContext ctx = TenantContextHolder.get();
            if (ctx == null || ctx.userId() == null) return Optional.empty();
            return Optional.of(ctx.userId());
        };
    }

    /**
     * Supplies {@link OffsetDateTime} for the {@code @CreatedDate} /
     * {@code @LastModifiedDate} fields on {@link AuditedJpaEntity}.
     *
     * <p>Spring Data's default {@code CurrentDateTimeProvider} returns
     * {@link java.time.LocalDateTime}, which the auditing handler cannot coerce
     * into our {@code OffsetDateTime} audit columns — every {@code save()} of an
     * {@code AuditedJpaEntity} then fails at flush time with
     * <em>"Cannot convert unsupported date type java.time.LocalDateTime to
     * java.time.OffsetDateTime"</em>. Returning an {@code OffsetDateTime} here
     * makes the provider type match the entity field type so inserts/updates
     * across methodology, factor, evaluation, grade and workflow entities all
     * persist correctly.
     */
    @Bean
    public DateTimeProvider auditingDateTimeProvider() {
        return () -> Optional.of(OffsetDateTime.now());
    }
}
