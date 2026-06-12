package uz.hrlab.grading.approval.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.approval.domain.ApprovalEntityType;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationPanelJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationRepository;
import uz.hrlab.grading.evaluation.infrastructure.PanelRepository;
import uz.hrlab.grading.gradestructure.infrastructure.GradeStructureJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeStructureRepository;
import uz.hrlab.grading.jobprofile.infrastructure.JobProfileJpaEntity;
import uz.hrlab.grading.jobprofile.infrastructure.JobProfileRepository;
import uz.hrlab.grading.methodology.infrastructure.MethodologyJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyRepository;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionRepository;
import uz.hrlab.grading.position.infrastructure.PositionJpaEntity;
import uz.hrlab.grading.position.infrastructure.PositionRepository;
import uz.hrlab.grading.project.infrastructure.ProjectJpaEntity;
import uz.hrlab.grading.project.infrastructure.ProjectRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;
import uz.hrlab.grading.tenancy.domain.Locale;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Resolves a human, i18n entity label for the entity an approval request wraps
 * (BE-1). Mirrors {@code MethodologyActorNameResolver} as an enrichment helper:
 * it turns the raw {@code entity_type + entity_id} of an already-tenant-loaded
 * approval row into a label the inbox / detail card can show instead of a UUID.
 *
 * <h2>Security (review R5–R7, R10)</h2>
 * <ul>
 *   <li><b>Tenant-scoped, every lookup.</b> Every entity is loaded via
 *       {@code findByIdAndTenantId(entityId, tenantId)} so a cross-tenant /
 *       stale id resolves to {@code null} (never another tenant's name). The
 *       {@code tenantId} is server-derived from {@code TenantContext}; the
 *       {@code entityId} comes off the tenant-loaded approval row, never from
 *       the client.</li>
 *   <li><b>Fail-soft (R10).</b> A missing / forbidden referent yields a
 *       {@code null} map — the FE degrades that one card to a short-id / generic
 *       type label. Resolution NEVER throws, so one bad referent cannot 500 the
 *       whole inbox.</li>
 *   <li><b>i18n (R6).</b> The label is the entity's own {@code *_i18n} map
 *       (4 locales: ru-RU, uz-Cyrl-UZ, uz-Latn-UZ, en-US), version suffix added
 *       per-locale for versioned entities.</li>
 *   <li><b>No sensitive data (R7).</b> Labels are titles / names / version
 *       numbers only — never scores, grades, salary, or comments.</li>
 * </ul>
 */
@Component
public class ApprovalEntityLabelResolver {

    private static final Logger log = LoggerFactory.getLogger(ApprovalEntityLabelResolver.class);

    private final EvaluationRepository evaluations;
    private final PanelRepository panels;
    private final PositionRepository positions;
    private final MethodologyVersionRepository methodologyVersions;
    private final MethodologyRepository methodologies;
    private final GradeStructureRepository gradeStructures;
    private final JobProfileRepository jobProfiles;
    private final ProjectRepository projects;
    private final AbacGate abacGate;

    public ApprovalEntityLabelResolver(EvaluationRepository evaluations,
                                       PanelRepository panels,
                                       PositionRepository positions,
                                       MethodologyVersionRepository methodologyVersions,
                                       MethodologyRepository methodologies,
                                       GradeStructureRepository gradeStructures,
                                       JobProfileRepository jobProfiles,
                                       ProjectRepository projects,
                                       AbacGate abacGate) {
        this.evaluations = evaluations;
        this.panels = panels;
        this.positions = positions;
        this.methodologyVersions = methodologyVersions;
        this.methodologies = methodologies;
        this.gradeStructures = gradeStructures;
        this.jobProfiles = jobProfiles;
        this.projects = projects;
        this.abacGate = abacGate;
    }

    /**
     * AC-20 / R5 — the position TITLE is department-subtree-protected. Before
     * leaking it into a label, verify the CALLER (active request context) may
     * read the position via the SAME ABAC gate the position APIs use. Fail
     * CLOSED: no active context or a gate denial drops the title (the rest of
     * the label still composes); never throws (R10).
     */
    private boolean positionTitleReadable(PositionJpaEntity p) {
        if (p == null) {
            return false;
        }
        try {
            TenantContext ctx = TenantContextHolder.requireActive();
            abacGate.enforceCanReadPosition(
                    ctx, p.getId(), p.getProjectId(), p.getDepartmentId(), p.getStatus());
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /**
     * @param tenantId active tenant (server-derived, never client-supplied)
     * @param type     entity type from the tenant-loaded approval row
     * @param entityId entity id from the tenant-loaded approval row
     * @return an i18n label map (4 locales), or {@code null} when the entity is
     *         missing / cross-tenant / cannot be labelled (FE falls back to id).
     */
    public Map<String, String> resolve(UUID tenantId, ApprovalEntityType type, UUID entityId) {
        if (tenantId == null || type == null || entityId == null) {
            return null;
        }
        try {
            return switch (type) {
                case EVALUATION -> evaluationLabel(tenantId, entityId);
                case EVALUATION_PANEL -> panelLabel(tenantId, entityId);
                case METHODOLOGY_VERSION -> methodologyVersionLabel(tenantId, entityId);
                case GRADE_STRUCTURE -> gradeStructureLabel(tenantId, entityId);
                case JOB_PROFILE -> jobProfileLabel(tenantId, entityId);
            };
        } catch (Exception ex) {
            // R10 — never let a bad referent bubble a 500 into the inbox/detail.
            log.warn("Entity label resolution failed type={} (referent unresolved); degrading to id", type);
            return null;
        }
    }

    private Map<String, String> evaluationLabel(UUID tenantId, UUID evaluationId) {
        EvaluationJpaEntity e = evaluations.findByIdAndTenantId(evaluationId, tenantId).orElse(null);
        if (e == null) {
            return null;
        }
        PositionJpaEntity p = positions.findByIdAndTenantId(e.getPositionId(), tenantId).orElse(null);
        // AC-20: the title only appears when the CALLER passes the position
        // ABAC read gate; otherwise the label composes without it.
        Map<String, String> positionTitle = positionTitleReadable(p) ? p.getTitleI18n() : null;
        MethodologyVersionJpaEntity v = methodologyVersions
                .findByIdAndTenantId(e.getMethodologyVersionId(), tenantId).orElse(null);
        Map<String, String> methodologyName = v == null ? null
                : methodologyNameForVersion(tenantId, v);
        // Label = "<position title> · <methodology name> v<n>". Compose per-locale
        // so each locale gets its own title/name; fall back to whichever part is
        // present so a partially-resolved label is still useful.
        Map<String, String> out = new HashMap<>();
        for (String locale : Locale.SUPPORTED) {
            String title = pick(positionTitle, locale);
            String method = pick(methodologyName, locale);
            String suffix = v == null ? null : "v" + v.getVersionNumber();
            String label = compose(title, method, suffix);
            if (label != null) {
                out.put(locale, label);
            }
        }
        return out.isEmpty() ? null : out;
    }

    /**
     * BE-14 — label for an {@code EVALUATION_PANEL} approval (CEO inbox). Same
     * composition as {@link #evaluationLabel} (position title + methodology name
     * + version) plus a localized "average" marker. The position TITLE is gated
     * by {@link #positionTitleReadable} (AC-20/R5): a department-scoped caller
     * outside the position's subtree gets the label WITHOUT the title —
     * "&lt;methodology&gt; vN · &lt;average&gt;" (or just the marker) — rather
     * than 404-ing the inbox (R10). NO scores or grade value in the label (R7).
     */
    private Map<String, String> panelLabel(UUID tenantId, UUID panelId) {
        EvaluationPanelJpaEntity panel = panels.findByIdAndTenantId(panelId, tenantId).orElse(null);
        if (panel == null) {
            return null;
        }
        PositionJpaEntity p = positions.findByIdAndTenantId(panel.getPositionId(), tenantId)
                .orElse(null);
        // AC-20: same caller-side ABAC read gate as evaluationLabel — outside
        // the approver's subtree the title is dropped and the label degrades
        // to "<methodology> vN · <average>" (or just the marker).
        Map<String, String> positionTitle = positionTitleReadable(p) ? p.getTitleI18n() : null;
        MethodologyVersionJpaEntity v = methodologyVersions
                .findByIdAndTenantId(panel.getMethodologyVersionId(), tenantId).orElse(null);
        Map<String, String> methodologyName = v == null ? null
                : methodologyNameForVersion(tenantId, v);

        Map<String, String> out = new HashMap<>();
        for (String locale : Locale.SUPPORTED) {
            String title = pick(positionTitle, locale);
            String method = pick(methodologyName, locale);
            String suffix = v == null ? null : "v" + v.getVersionNumber();
            String base = compose(title, method, suffix);
            String marker = averageMarker(locale);
            String label = base == null ? marker : base + " · " + marker;
            out.put(locale, label);
        }
        return out.isEmpty() ? null : out;
    }

    /** Localized "average" marker for the panel label (4 locales, R6). */
    private static String averageMarker(String locale) {
        return switch (locale) {
            case Locale.UZ_CYRL_UZ -> "ўртача";
            case Locale.UZ_LATN_UZ -> "o'rtacha";
            case Locale.EN_US -> "average";
            default -> "среднее"; // ru-RU
        };
    }

    private Map<String, String> methodologyVersionLabel(UUID tenantId, UUID versionId) {
        MethodologyVersionJpaEntity v = methodologyVersions
                .findByIdAndTenantId(versionId, tenantId).orElse(null);
        if (v == null) {
            return null;
        }
        Map<String, String> name = methodologyNameForVersion(tenantId, v);
        if (name == null || name.isEmpty()) {
            return null;
        }
        Map<String, String> out = new HashMap<>();
        for (String locale : Locale.SUPPORTED) {
            String n = pick(name, locale);
            if (n != null) {
                out.put(locale, n + " v" + v.getVersionNumber());
            }
        }
        return out.isEmpty() ? null : out;
    }

    private Map<String, String> gradeStructureLabel(UUID tenantId, UUID structureId) {
        GradeStructureJpaEntity g = gradeStructures.findByIdAndTenantId(structureId, tenantId).orElse(null);
        if (g == null) {
            return null;
        }
        return nonEmptyCopy(g.getNameI18n());
    }

    private Map<String, String> jobProfileLabel(UUID tenantId, UUID profileId) {
        JobProfileJpaEntity jp = jobProfiles.findByIdAndTenantId(profileId, tenantId).orElse(null);
        if (jp == null) {
            return null;
        }
        // A job profile has no name of its own — it labels via its Position title.
        PositionJpaEntity p = positions.findByIdAndTenantId(jp.getPositionId(), tenantId).orElse(null);
        return p == null ? null : nonEmptyCopy(p.getTitleI18n());
    }

    private Map<String, String> methodologyNameForVersion(UUID tenantId, MethodologyVersionJpaEntity v) {
        MethodologyJpaEntity m = methodologies
                .findByIdAndTenantId(v.getMethodologyId(), tenantId).orElse(null);
        return m == null ? null : m.getNameI18n();
    }

    /** Compose "title · method suffix" tolerating missing parts; null if all empty. */
    private static String compose(String title, String method, String suffix) {
        StringBuilder methodPart = new StringBuilder();
        if (method != null && !method.isBlank()) {
            methodPart.append(method);
            if (suffix != null) {
                methodPart.append(' ').append(suffix);
            }
        }
        boolean hasTitle = title != null && !title.isBlank();
        boolean hasMethod = methodPart.length() > 0;
        if (hasTitle && hasMethod) {
            return title + " · " + methodPart;
        }
        if (hasTitle) {
            return title;
        }
        if (hasMethod) {
            return methodPart.toString();
        }
        return null;
    }

    private static String pick(Map<String, String> i18n, String locale) {
        if (i18n == null) {
            return null;
        }
        return i18n.get(locale);
    }

    private static Map<String, String> nonEmptyCopy(Map<String, String> i18n) {
        if (i18n == null || i18n.isEmpty()) {
            return null;
        }
        return new HashMap<>(i18n);
    }
}
