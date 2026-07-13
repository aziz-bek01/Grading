package uz.hrlab.grading.methodology.application;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.approval.application.CreateApprovalRequestUseCase;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.methodology.domain.MethodologyStatus;
import uz.hrlab.grading.methodology.domain.MethodologyType;
import uz.hrlab.grading.methodology.domain.MethodologyVersionPrimaryLocaleValidator;
import uz.hrlab.grading.methodology.domain.MethodologyVersionStatus;
import uz.hrlab.grading.methodology.domain.MethodologyVersionStatusTransitionPolicy;
import uz.hrlab.grading.methodology.domain.MethodologyWeightValidationPolicy;
import uz.hrlab.grading.methodology.domain.ScoringMode;
import uz.hrlab.grading.methodology.infrastructure.FactorJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.FactorLevelJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.FactorLevelRepository;
import uz.hrlab.grading.methodology.infrastructure.FactorRepository;
import uz.hrlab.grading.methodology.infrastructure.MethodologyJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyRepository;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * N+1 regression lock for {@link ApproveMethodologyVersionUseCase#approve} (approval
 * hot path). Proves the "≥ 2 levels per factor" check loads every factor's levels
 * with ONE tenant-scoped batch query
 * ({@code findAllByTenantIdAndFactorIdInOrderByLevelOrderAsc}) and NEVER falls back
 * to the per-factor {@code findAllByTenantIdAndFactorIdOrderByLevelOrderAsc} (the
 * former N+1). Seeds 8 factors and asserts the level query count stays 1, i.e. it
 * does not scale with the factor count. Weight/locale validation is mocked away to
 * keep the test focused on the query count. Pure Mockito (no Docker) — mirrors
 * {@code FindDepartmentQueryCountsTest}.
 */
@ExtendWith(MockitoExtension.class)
class ApproveMethodologyVersionUseCaseQueryCountsTest {

    @Mock MethodologyRepository methodologies;
    @Mock MethodologyVersionRepository versions;
    @Mock FactorRepository factors;
    @Mock FactorLevelRepository levels;
    @Mock AbacGate abacGate;
    @Mock MethodologyVersionStatusTransitionPolicy transitionPolicy;
    @Mock MethodologyWeightValidationPolicy weightPolicy;
    @Mock MethodologyVersionPrimaryLocaleValidator localeValidator;
    @Mock AuditService audit;
    @Mock MethodologyAuditSnapshot snapshot;
    @Mock CreateApprovalRequestUseCase createApprovalRequest;

    ApproveMethodologyVersionUseCase useCase;

    UUID tenantId;
    UUID versionId;

    @BeforeEach
    void setUp() {
        useCase = new ApproveMethodologyVersionUseCase(methodologies, versions, factors, levels,
                abacGate, transitionPolicy, weightPolicy, localeValidator, audit, snapshot,
                createApprovalRequest);
        tenantId = UUID.randomUUID();
        versionId = UUID.randomUUID();
        TenantContextHolder.set(new TenantContext(
                UUID.randomUUID(), tenantId, Set.of(), Set.of(),
                Set.of(PermissionCodes.METHODOLOGY_APPROVE), Set.of(), false, "ru-RU"));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void levelLoadIsOneBatchQueryRegardlessOfFactorCount() {
        int factorCount = 8;
        UUID methodologyId = UUID.randomUUID();
        // projectId == null → skips ABAC + system approval-request recording.
        MethodologyJpaEntity m = new MethodologyJpaEntity(methodologyId, tenantId, null, "M-1",
                MethodologyType.EXTENDED_11_CRITERIA, MethodologyStatus.ACTIVE);
        MethodologyVersionJpaEntity v = new MethodologyVersionJpaEntity(versionId, tenantId,
                methodologyId, 1, MethodologyVersionStatus.DRAFT, ScoringMode.WEIGHTED_POINTS,
                new BigDecimal("100.0000"), null);

        List<FactorJpaEntity> factorRows = new ArrayList<>();
        List<FactorLevelJpaEntity> levelRows = new ArrayList<>();
        for (int i = 0; i < factorCount; i++) {
            FactorJpaEntity f = new FactorJpaEntity(UUID.randomUUID(), tenantId, versionId,
                    "F" + i, new BigDecimal("12.5000"), new BigDecimal("100.0000"), i + 1, true);
            factorRows.add(f);
            // two levels per factor → satisfies the ≥ 2 check without rejection.
            levelRows.add(new FactorLevelJpaEntity(UUID.randomUUID(), tenantId, f.getId(),
                    "L1", 1, new BigDecimal("10"), null));
            levelRows.add(new FactorLevelJpaEntity(UUID.randomUUID(), tenantId, f.getId(),
                    "L2", 2, new BigDecimal("20"), null));
        }

        given(versions.findByIdAndTenantId(versionId, tenantId)).willReturn(Optional.of(v));
        given(methodologies.findByIdAndTenantId(methodologyId, tenantId)).willReturn(Optional.of(m));
        given(factors.findAllByTenantIdAndMethodologyVersionIdOrderBySortOrderAsc(tenantId, versionId))
                .willReturn(factorRows);
        given(levels.findAllByTenantIdAndFactorIdInOrderByLevelOrderAsc(eq(tenantId), any()))
                .willReturn(levelRows);
        given(snapshot.of(any(MethodologyVersionJpaEntity.class))).willReturn(mock(JsonNode.class));

        useCase.approve(versionId);

        // Bounded: exactly ONE batch level query, ZERO per-factor level queries —
        // the count does NOT scale with the 8 factors.
        verify(levels, times(1)).findAllByTenantIdAndFactorIdInOrderByLevelOrderAsc(eq(tenantId), any());
        verify(levels, never()).findAllByTenantIdAndFactorIdOrderByLevelOrderAsc(any(), any());
    }
}
