package uz.hrlab.grading.methodology.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.api.GlobalExceptionHandler;
import uz.hrlab.grading.common.api.WebMvcSecurityTestConfig;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.methodology.application.ApproveMethodologyVersionUseCase;
import uz.hrlab.grading.methodology.application.ArchiveMethodologyVersionUseCase;
import uz.hrlab.grading.methodology.application.CreateMethodologyVersionUseCase;
import uz.hrlab.grading.methodology.application.FactorService;
import uz.hrlab.grading.methodology.application.LockMethodologyVersionUseCase;
import uz.hrlab.grading.methodology.application.MethodologyActorNameResolver;
import uz.hrlab.grading.methodology.application.MethodologyQueries;
import uz.hrlab.grading.methodology.application.UpdateMethodologyVersionMetadataUseCase;
import uz.hrlab.grading.methodology.domain.MethodologyVersion;
import uz.hrlab.grading.methodology.domain.MethodologyVersionStatus;
import uz.hrlab.grading.methodology.domain.MethodologyVersionTransitionRejectedException;
import uz.hrlab.grading.methodology.domain.ScoringMode;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BE-7 — WebMvc slice for {@code PATCH /api/v1/methodology-versions/{id}}
 * (version metadata edit: scoring_mode + target_total_points).
 *
 * <p>Asserts the wire contract + status mapping:
 * <ul>
 *   <li>200 + persisted scoring_mode for METHODOLOGY_EDIT on a DRAFT version</li>
 *   <li>403 without METHODOLOGY_EDIT</li>
 *   <li>404 (TenantAccessDenied) on cross-tenant id</li>
 *   <li>409 (MethodologyVersionTransitionRejected) on APPROVED/LOCKED version</li>
 *   <li>400 (SCORING_TARGET_REQUIRED) on WEIGHTED_SCALE with target &lt;= 0</li>
 * </ul>
 */
@Tag("security")
@WebMvcTest(controllers = {MethodologyVersionController.class},
        excludeAutoConfiguration = {
                OAuth2ClientAutoConfiguration.class,
                OAuth2ResourceServerAutoConfiguration.class
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "uz\\.hrlab\\.grading\\.security\\..*"))
@Import({WebMvcSecurityTestConfig.class, GlobalExceptionHandler.class})
class MethodologyVersionControllerSecurityTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @MockBean MethodologyQueries queries;
    @MockBean ApproveMethodologyVersionUseCase approveUseCase;
    @MockBean LockMethodologyVersionUseCase lockUseCase;
    @MockBean ArchiveMethodologyVersionUseCase archiveUseCase;
    @MockBean CreateMethodologyVersionUseCase newVersionUseCase;
    @MockBean UpdateMethodologyVersionMetadataUseCase updateMetadataUseCase;
    @MockBean FactorService factorService;
    @MockBean MethodologyActorNameResolver actorNames;
    @MockBean AuditService auditService;

    private static final String BODY =
            "{\"scoring_mode\":\"WEIGHTED_POINTS\",\"target_total_points\":100}";

    private MethodologyVersion draft(UUID id, ScoringMode mode) {
        return new MethodologyVersion(id, UUID.randomUUID(), UUID.randomUUID(),
                1, MethodologyVersionStatus.DRAFT, mode,
                new BigDecimal("100"), null, null, null, null, null);
    }

    @Test
    void patchRequiresMethodologyEdit() throws Exception {
        mvc.perform(patch("/api/v1/methodology-versions/{id}", UUID.randomUUID())
                        .with(jwt().authorities(() -> "METHODOLOGY_READ"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void patchWithEditPersistsScoringModeAndReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        given(updateMetadataUseCase.update(eq(id), eq(ScoringMode.WEIGHTED_POINTS), any()))
                .willReturn(draft(id, ScoringMode.WEIGHTED_POINTS));
        mvc.perform(patch("/api/v1/methodology-versions/{id}", id)
                        .with(jwt().authorities(() -> "METHODOLOGY_EDIT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scoring_mode").value("WEIGHTED_POINTS"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void patchCrossTenantReturns404() throws Exception {
        given(updateMetadataUseCase.update(any(), any(), any()))
                .willThrow(new TenantAccessDeniedException());
        mvc.perform(patch("/api/v1/methodology-versions/{id}", UUID.randomUUID())
                        .with(jwt().authorities(() -> "METHODOLOGY_EDIT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void patchOnNonDraftReturns409() throws Exception {
        given(updateMetadataUseCase.update(any(), any(), any()))
                .willThrow(new MethodologyVersionTransitionRejectedException(
                        "Cannot modify a methodology version in state APPROVED"));
        mvc.perform(patch("/api/v1/methodology-versions/{id}", UUID.randomUUID())
                        .with(jwt().authorities(() -> "METHODOLOGY_EDIT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void patchWeightedScaleInvalidTargetReturns400() throws Exception {
        given(updateMetadataUseCase.update(any(), any(), any()))
                .willThrow(new ValidationException("SCORING_TARGET_REQUIRED",
                        "target_total_points must be greater than 0 for WEIGHTED_SCALE"));
        mvc.perform(patch("/api/v1/methodology-versions/{id}", UUID.randomUUID())
                        .with(jwt().authorities(() -> "METHODOLOGY_EDIT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scoring_mode\":\"WEIGHTED_SCALE\",\"target_total_points\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SCORING_TARGET_REQUIRED"));
    }

    @Test
    void malformedUuidReturns400() throws Exception {
        mvc.perform(patch("/api/v1/methodology-versions/{id}", "not-a-uuid")
                        .with(jwt().authorities(() -> "METHODOLOGY_EDIT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isBadRequest());
    }

    /**
     * Wire-contract pin for {@code ReorderRequest}: the body is
     * {@code {"ordered_ids":[uuid,...]}} (global SNAKE_CASE). A FE/BE drift here
     * shipped once — the FE sent {@code {"order":[{id,sort_order}]}}, which binds
     * {@code orderedIds=null} and 400s — so this asserts the canonical shape
     * reaches the service and the legacy shape is rejected.
     */
    @Test
    void factorsReorderBindsOrderedIdsContract() throws Exception {
        UUID versionId = UUID.randomUUID();
        UUID f1 = UUID.randomUUID();
        UUID f2 = UUID.randomUUID();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/methodology-versions/{id}/factors/reorder", versionId)
                        .with(jwt().authorities(() -> "METHODOLOGY_EDIT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ordered_ids\":[\"" + f1 + "\",\"" + f2 + "\"]}"))
                .andExpect(status().isNoContent());
        org.mockito.Mockito.verify(factorService)
                .reorder(eq(versionId), eq(java.util.List.of(f1, f2)));
    }

    @Test
    void factorsReorderLegacyOrderShapeReturns400() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/methodology-versions/{id}/factors/reorder", UUID.randomUUID())
                        .with(jwt().authorities(() -> "METHODOLOGY_EDIT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"order\":[{\"id\":\"" + UUID.randomUUID()
                                + "\",\"sort_order\":0}]}"))
                .andExpect(status().isBadRequest());
    }
}
