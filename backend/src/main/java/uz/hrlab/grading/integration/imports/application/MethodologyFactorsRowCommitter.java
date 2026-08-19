package uz.hrlab.grading.integration.imports.application;

import org.springframework.stereotype.Component;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.integration.imports.domain.ImportTemplateCode;
import uz.hrlab.grading.methodology.domain.MethodologyStatus;
import uz.hrlab.grading.methodology.domain.MethodologyType;
import uz.hrlab.grading.methodology.domain.MethodologyVersionPrimaryLocaleValidator;
import uz.hrlab.grading.methodology.domain.MethodologyVersionStatus;
import uz.hrlab.grading.methodology.domain.ScoringMode;
import uz.hrlab.grading.methodology.infrastructure.FactorJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.FactorLevelJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.FactorLevelRepository;
import uz.hrlab.grading.methodology.infrastructure.FactorRepository;
import uz.hrlab.grading.methodology.infrastructure.MethodologyJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyRepository;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionRepository;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * METHODOLOGY_FACTORS_V1 commit DAO (create-or-upsert). Materializes an imported
 * methodology — its container + DRAFT v1 + factors + levels — from a flat
 * spreadsheet. The file carries ONE methodology (methodology-level metadata is
 * repeated identically on every row) plus one row per {@code factor + level}.
 *
 * <p><b>Per-row behaviour</b> (the committer is called PER ROW; the metadata
 * columns are identical on every row, so the FIRST row for a given
 * {@code methodology_code} find-or-creates the methodology and subsequent rows
 * resolve the same DRAFT within the shared commit transaction):
 * <ol>
 *   <li>NO methodology with {@code methodology_code} in the project → CREATE a
 *       DRAFT methodology + DRAFT v1 from the metadata, then add the factor /
 *       level.</li>
 *   <li>Methodology exists and its latest version is DRAFT → UPSERT the factor /
 *       level into that DRAFT (its identity metadata must MATCH the row, else
 *       {@code METHODOLOGY_METADATA_MISMATCH} — an import never silently
 *       re-brands an existing methodology).</li>
 *   <li>Methodology exists but its latest version is NOT DRAFT
 *       (APPROVED/LOCKED/ARCHIVED) → reject {@code METHODOLOGY_NOT_DRAFT}
 *       (approved methodologies are immutable — domain principle #4).</li>
 * </ol>
 * The result is a REAL, approvable methodology: with ≥2 factors, ≥2 levels each,
 * a primary-locale ({@code ru-RU}) factor name and a weight sum consistent with
 * {@code scoring_mode}, the produced DRAFT version passes
 * {@code ApproveMethodologyVersionUseCase.approve(...)}.
 *
 * <p><b>Reuse / why repositories, not use-cases (STANDING CONSTRAINT "kod
 * takrorlanmasin"):</b> the canonical build path is
 * {@code CreateMethodologyFromScratchUseCase.create} →
 * {@code FactorService.add} → {@code FactorLevelService.add}. Those use-cases
 * re-check {@code METHODOLOGY_CREATE} / {@code METHODOLOGY_EDIT} against the
 * ambient {@link uz.hrlab.grading.tenancy.application.TenantContext}. The import
 * caller is authorized by {@code METHODOLOGY_IMPORT} (re-checked at commit time
 * by {@link CommitImportBatchUseCase}); a role may legitimately hold
 * {@code METHODOLOGY_IMPORT} WITHOUT {@code METHODOLOGY_CREATE/EDIT}, so calling
 * those use-cases would spuriously 403 a valid import. Therefore — exactly like
 * {@link GradeBandsRowCommitter} — this committer writes through the REPOSITORIES
 * directly, faithfully replicating the canonical DRAFT construction (Methodology
 * ACTIVE + Version DRAFT v1; Factor / FactorLevel shape) so the imported
 * methodology is byte-shaped identically to a hand-built one. Tenant is ALWAYS
 * {@code ctx.tenantId()} — never the row.
 *
 * <p><b>Idempotency + correction (last-wins):</b> re-running a file never
 * duplicates rows AND applies corrections. A repeated {@code (factor_code,
 * level_code)} updates the level in place; on an existing factor the mutable
 * fields ({@code weight}, ru-RU {@code name}, inferred {@code maxPoints}) resync
 * last-wins; and the existing DRAFT methodology's ru-RU {@code name_i18n} resyncs
 * last-wins — so re-uploading a corrected file (fixed weight / name typo) takes
 * effect instead of being silently dropped.
 *
 * <p><b>Localization (multi-locale import):</b> the base text columns
 * ({@code methodology_name}, {@code factor_name}, {@code level_name},
 * {@code level_description}) carry the REQUIRED primary locale ({@code ru-RU}).
 * Every one of them additionally accepts OPTIONAL per-locale siblings suffixed
 * with the locale key — {@code _uz_cyrl} ({@code uz-Cyrl-UZ}, alias {@code _uz}),
 * {@code _uz_latn} ({@code uz-Latn-UZ}), {@code _en} ({@code en-US}) and the
 * explicit {@code _ru} alias for the base — so ONE upload populates a
 * methodology in every language instead of leaving translators to fill 4 locales
 * × N levels by hand (the translation matrix UI covers factor NAMES only, never
 * level labels/descriptions).
 *
 * <p>Locale maps are <b>merged, not replaced</b>: a re-import that carries only
 * {@code ru-RU} corrects the Russian text and LEAVES existing Uzbek/English
 * translations intact. Only the locales actually present in the file are
 * overwritten (last-wins per locale). Before this, every write did
 * {@code Map.of(PRIMARY_LOCALE, …)}, so any re-import silently wiped the other
 * three locales.
 *
 * <p><b>Scoring modes:</b> {@code DIRECT_POINTS} / {@code WEIGHTED_POINTS} score
 * off level {@code points}; {@code WEIGHTED_SCALE} scores off a per-level
 * {@code scale_value} (the optional {@code scale_value} column). For
 * {@code WEIGHTED_SCALE} the column is REQUIRED per row — a missing value would
 * make {@code EvaluationScoringEngine.weightedScale} treat the level as 0, so it
 * is rejected ({@code MISSING_SCALE_VALUE}) at commit rather than silently
 * scoring nothing.
 */
@Component
public class MethodologyFactorsRowCommitter implements ImportRowCommitter {

    private static final String PRIMARY_LOCALE =
            MethodologyVersionPrimaryLocaleValidator.PRIMARY_LOCALE; // ru-RU

    private final MethodologyRepository methodologies;
    private final MethodologyVersionRepository versions;
    private final FactorRepository factors;
    private final FactorLevelRepository levels;
    private final AuditService audit;

    public MethodologyFactorsRowCommitter(MethodologyRepository methodologies,
                                          MethodologyVersionRepository versions,
                                          FactorRepository factors,
                                          FactorLevelRepository levels,
                                          AuditService audit) {
        this.methodologies = methodologies;
        this.versions = versions;
        this.factors = factors;
        this.levels = levels;
        this.audit = audit;
    }

    @Override
    public String templateCode() {
        return ImportTemplateCode.METHODOLOGY_FACTORS_V1;
    }

    @Override
    public CommitResult commit(Map<String, String> row, ImportRowCommitContext ctx) {
        if (ctx.projectId() == null) {
            throw new ImportRowCommitException("PROJECT_REQUIRED",
                    "METHODOLOGY_FACTORS_V1 commit requires a project");
        }

        // 1) Methodology-level metadata (identical on every row of the file).
        Metadata meta = parseMetadata(row);

        // 2) Resolve the target DRAFT version (create-or-upsert), enforcing that
        //    an existing methodology's identity metadata matches the row.
        MethodologyVersionJpaEntity version = resolveDraftVersion(meta, ctx);

        // 3) Factor: find-or-create by (versionId, factor_code).
        BigDecimal levelPoints = parsePoints(row);
        FactorJpaEntity factor = resolveFactor(row, version.getId(), levelPoints, ctx);

        // 4) Level: upsert by (factorId, level_code). scale_value is required for
        //    WEIGHTED_SCALE (else the scoring engine silently scores 0).
        BigDecimal scaleValue = parseScaleValue(row, meta.mode());
        return upsertLevel(row, factor, levelPoints, scaleValue, ctx);
    }

    // -----------------------------------------------------------------
    // Step 1 — metadata parsing / validation
    // -----------------------------------------------------------------

    private Metadata parseMetadata(Map<String, String> row) {
        String code = req(row, "methodology_code");
        String name = req(row, "methodology_name");
        Map<String, String> nameI18n = localeColumns(row, "methodology_name");

        String typeRaw = req(row, "methodology_type");
        MethodologyType type;
        try {
            type = MethodologyType.valueOf(typeRaw);
        } catch (IllegalArgumentException e) {
            throw new ImportRowCommitException("INVALID_METHODOLOGY_TYPE",
                    "methodology_type must be one of CLASSIC_8_FACTOR, "
                            + "EXTENDED_11_CRITERIA, CUSTOM — got '" + typeRaw + "'");
        }

        String modeRaw = req(row, "scoring_mode");
        ScoringMode mode;
        try {
            mode = ScoringMode.valueOf(modeRaw);
        } catch (IllegalArgumentException e) {
            throw new ImportRowCommitException("INVALID_SCORING_MODE",
                    "scoring_mode must be one of DIRECT_POINTS, WEIGHTED_POINTS, "
                            + "WEIGHTED_SCALE — got '" + modeRaw + "'");
        }

        BigDecimal totalPoints;
        String totalRaw = req(row, "target_total_points");
        try {
            totalPoints = new BigDecimal(totalRaw);
        } catch (NumberFormatException e) {
            throw new ImportRowCommitException("INVALID_TOTAL_POINTS",
                    "target_total_points is not a valid number: " + totalRaw);
        }
        return new Metadata(code, name, nameI18n, type, mode, totalPoints);
    }

    // -----------------------------------------------------------------
    // Step 2 — resolve (create-or-upsert) the DRAFT version
    // -----------------------------------------------------------------

    private MethodologyVersionJpaEntity resolveDraftVersion(Metadata meta,
                                                            ImportRowCommitContext ctx) {
        Optional<MethodologyJpaEntity> existing = methodologies
                .findByTenantIdAndProjectIdAndCode(ctx.tenantId(), ctx.projectId(), meta.code());

        if (existing.isPresent()) {
            MethodologyJpaEntity m = existing.get();
            MethodologyVersionJpaEntity latest = versions
                    .findFirstByTenantIdAndMethodologyIdOrderByVersionNumberDesc(
                            ctx.tenantId(), m.getId())
                    .orElseThrow(() -> new ImportRowCommitException("METHODOLOGY_NOT_DRAFT",
                            "Methodology '" + meta.code() + "' has no version to import into"));
            if (latest.getStatus() != MethodologyVersionStatus.DRAFT) {
                throw new ImportRowCommitException("METHODOLOGY_NOT_DRAFT",
                        "Methodology '" + meta.code() + "' is " + latest.getStatus()
                                + " — approved/locked methodologies are immutable; "
                                + "create a new version before importing");
            }
            // Guard identity: never silently mutate an existing methodology's brand.
            if (m.getMethodologyType() != meta.type()
                    || latest.getScoringMode() != meta.mode()
                    || !numericEquals(latest.getTargetTotalPoints(), meta.totalPoints())) {
                throw new ImportRowCommitException("METHODOLOGY_METADATA_MISMATCH",
                        "Methodology '" + meta.code() + "' already exists with different "
                                + "type/scoring_mode/target_total_points than the imported file");
            }
            // Resync the mutable name last-wins so a re-import corrects a
            // methodology-name typo (identity fields above stay frozen). Merged
            // per locale: locales absent from the file keep their existing text.
            Map<String, String> merged = mergeI18n(m.getNameI18n(), meta.nameI18n());
            if (!merged.equals(m.getNameI18n())) {
                m.setNameI18n(merged);
                methodologies.save(m);
            }
            return latest;
        }

        // CREATE a fresh DRAFT methodology + DRAFT v1 — mirrors
        // CreateMethodologyFromScratchUseCase (canonical shape).
        UUID methodologyId = UUID.randomUUID();
        MethodologyJpaEntity m = new MethodologyJpaEntity(
                methodologyId, ctx.tenantId(), ctx.projectId(), meta.code(),
                meta.type(), MethodologyStatus.ACTIVE);
        m.setNameI18n(meta.nameI18n());
        methodologies.save(m);

        UUID versionId = UUID.randomUUID();
        MethodologyVersionJpaEntity v = new MethodologyVersionJpaEntity(
                versionId, ctx.tenantId(), methodologyId, 1,
                MethodologyVersionStatus.DRAFT, meta.mode(), meta.totalPoints(), null);
        versions.save(v);

        audit(ctx, AuditAction.METHODOLOGY_CREATED, "Methodology", methodologyId);
        audit(ctx, AuditAction.METHODOLOGY_VERSION_CREATED, "MethodologyVersion", versionId);
        return v;
    }

    // -----------------------------------------------------------------
    // Step 3 — factor find-or-create
    // -----------------------------------------------------------------

    private FactorJpaEntity resolveFactor(Map<String, String> row, UUID versionId,
                                          BigDecimal levelPoints, ImportRowCommitContext ctx) {
        String factorCode = req(row, "factor_code");
        req(row, "factor_name"); // primary-locale name is mandatory
        Map<String, String> nameI18n = localeColumns(row, "factor_name");
        BigDecimal weight = parseWeight(row);

        Optional<FactorJpaEntity> existing = factors
                .findByTenantIdAndMethodologyVersionIdAndCode(
                        ctx.tenantId(), versionId, factorCode);
        if (existing.isPresent()) {
            FactorJpaEntity f = existing.get();
            // Resync mutable fields last-wins so a re-import corrects a weight /
            // name typo. maxPoints is inferred as the max level.points seen for
            // the factor (no max_points column): keep the running max across rows.
            f.setWeight(weight);
            f.setNameI18n(mergeI18n(f.getNameI18n(), nameI18n));
            if (levelPoints.compareTo(f.getMaxPoints()) > 0) {
                f.setMaxPoints(levelPoints);
            }
            factors.save(f);
            return f;
        }

        // sortOrder = order of first appearance = current factor count + 1.
        int sortOrder = (int) factors
                .countByTenantIdAndMethodologyVersionId(ctx.tenantId(), versionId) + 1;
        UUID id = UUID.randomUUID();
        FactorJpaEntity f = new FactorJpaEntity(
                id, ctx.tenantId(), versionId, factorCode, weight, levelPoints, sortOrder, true);
        f.setNameI18n(nameI18n);
        factors.save(f);
        audit(ctx, AuditAction.FACTOR_CREATED, "Factor", id);
        return f;
    }

    // -----------------------------------------------------------------
    // Step 4 — level upsert
    // -----------------------------------------------------------------

    private CommitResult upsertLevel(Map<String, String> row, FactorJpaEntity factor,
                                     BigDecimal levelPoints, BigDecimal scaleValue,
                                     ImportRowCommitContext ctx) {
        String levelCode = req(row, "level_code");
        req(row, "level_name"); // primary-locale label is mandatory
        Map<String, String> labelI18n = localeColumns(row, "level_name");
        // Description is optional overall, but any locale may supply it — an
        // empty map means "no description column in this file" (leave as-is).
        Map<String, String> descriptionI18n = localeColumns(row, "level_description");
        Integer levelOrder = parseOptionalOrder(row);

        Optional<FactorLevelJpaEntity> existing = levels
                .findByTenantIdAndFactorIdAndCode(ctx.tenantId(), factor.getId(), levelCode);
        if (existing.isPresent()) {
            // LAST-WINS upsert — a repeated level_code updates in place.
            FactorLevelJpaEntity l = existing.get();
            l.setPoints(levelPoints);
            l.setScaleValue(scaleValue);
            if (levelOrder != null) {
                l.setLevelOrder(levelOrder);
            }
            l.setLabelI18n(mergeI18n(l.getLabelI18n(), labelI18n));
            if (!descriptionI18n.isEmpty()) {
                l.setDescriptionI18n(mergeI18n(l.getDescriptionI18n(), descriptionI18n));
            }
            levels.save(l);
            audit(ctx, AuditAction.FACTOR_LEVEL_UPDATED, "FactorLevel", l.getId());
            return new CommitResult("FactorLevel", l.getId(), AuditAction.FACTOR_LEVEL_UPDATED);
        }

        int order = levelOrder != null ? levelOrder : nextLevelOrder(ctx.tenantId(), factor.getId());
        UUID id = UUID.randomUUID();
        FactorLevelJpaEntity l = new FactorLevelJpaEntity(
                id, ctx.tenantId(), factor.getId(), levelCode, order, levelPoints, scaleValue);
        l.setLabelI18n(labelI18n);
        if (!descriptionI18n.isEmpty()) {
            l.setDescriptionI18n(descriptionI18n);
        }
        levels.save(l);
        audit(ctx, AuditAction.FACTOR_LEVEL_CREATED, "FactorLevel", id);
        return new CommitResult("FactorLevel", id, AuditAction.FACTOR_LEVEL_CREATED);
    }

    private int nextLevelOrder(UUID tenantId, UUID factorId) {
        Integer max = levels.findMaxLevelOrderByFactorId(tenantId, factorId);
        return max == null ? 1 : max + 1;
    }

    // -----------------------------------------------------------------
    // Parsing helpers
    // -----------------------------------------------------------------

    /** {@code weight} (registry-required) with {@code factor_weight} as fallback. */
    private static BigDecimal parseWeight(Map<String, String> row) {
        String raw = firstNonBlank(row.get("weight"), row.get("factor_weight"));
        if (raw == null) {
            throw new ImportRowCommitException("MISSING_REQUIRED_FIELD",
                    "Required field missing: weight");
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            throw new ImportRowCommitException("INVALID_FACTOR_WEIGHT",
                    "weight is not a valid number: " + raw);
        }
    }

    /** {@code score} (registry-required) with {@code level_points} as fallback. */
    private static BigDecimal parsePoints(Map<String, String> row) {
        String raw = firstNonBlank(row.get("score"), row.get("level_points"));
        if (raw == null) {
            throw new ImportRowCommitException("MISSING_REQUIRED_FIELD",
                    "Required field missing: score");
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            throw new ImportRowCommitException("INVALID_LEVEL_POINTS",
                    "score is not a valid number: " + raw);
        }
    }

    /**
     * Per-level {@code scale_value}. REQUIRED for {@code WEIGHTED_SCALE} (a blank
     * would make the scoring engine treat the level as 0 → silent zero scores);
     * optional otherwise (stored if present, else null — harmless for
     * DIRECT_POINTS / WEIGHTED_POINTS which score off {@code points}).
     */
    private static BigDecimal parseScaleValue(Map<String, String> row, ScoringMode mode) {
        String raw = opt(row, "scale_value");
        if (raw == null) {
            if (mode == ScoringMode.WEIGHTED_SCALE) {
                throw new ImportRowCommitException("MISSING_SCALE_VALUE",
                        "scale_value is required for WEIGHTED_SCALE scoring (a blank "
                                + "value would score 0)");
            }
            return null;
        }
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException e) {
            throw new ImportRowCommitException("INVALID_SCALE_VALUE",
                    "scale_value is not a valid number: " + raw);
        }
    }

    /**
     * Column-suffix → locale key. The suffix-less base column is always the
     * primary locale ({@code ru-RU}); {@code _ru} is accepted as its explicit
     * alias, and {@code _uz} as a friendly alias for Cyrillic Uzbek (the script
     * Uzbek HR documents are written in). Iteration order is deterministic so a
     * file that supplies BOTH {@code _uz} and {@code _uz_cyrl} resolves to the
     * more specific column last.
     */
    private static final Map<String, String> LOCALE_SUFFIXES = new LinkedHashMap<>();

    static {
        LOCALE_SUFFIXES.put("_ru", "ru-RU");
        LOCALE_SUFFIXES.put("_uz", "uz-Cyrl-UZ");
        LOCALE_SUFFIXES.put("_uz_cyrl", "uz-Cyrl-UZ");
        LOCALE_SUFFIXES.put("_uz_latn", "uz-Latn-UZ");
        LOCALE_SUFFIXES.put("_en", "en-US");
    }

    /**
     * Assembles the locale map for one logical text field from its base column
     * ({@code ru-RU}) plus every optional {@code <base>_<suffix>} sibling present
     * in the row. Blank cells are skipped — a locale column left empty on some
     * rows never writes an empty translation.
     *
     * @return a map keyed by supported locale code; empty when the row carries
     *         no non-blank value for this field at all
     */
    private static Map<String, String> localeColumns(Map<String, String> row, String base) {
        Map<String, String> out = new LinkedHashMap<>();
        String primary = opt(row, base);
        if (primary != null) {
            out.put(PRIMARY_LOCALE, primary);
        }
        for (Map.Entry<String, String> e : LOCALE_SUFFIXES.entrySet()) {
            String v = opt(row, base + e.getKey());
            if (v != null) {
                out.put(e.getValue(), v);
            }
        }
        return out;
    }

    /**
     * Merges imported locale text onto whatever the entity already holds:
     * locales present in the file win (last-wins correction), locales absent
     * from the file are preserved. This is what keeps a Russian-only re-import
     * from wiping previously imported Uzbek/English translations.
     */
    private static Map<String, String> mergeI18n(Map<String, String> existing,
                                                 Map<String, String> imported) {
        Map<String, String> out = new LinkedHashMap<>();
        if (existing != null) {
            out.putAll(existing);
        }
        out.putAll(imported);
        return out;
    }

    private static Integer parseOptionalOrder(Map<String, String> row) {
        String raw = opt(row, "level_order");
        if (raw == null) {
            return null;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new ImportRowCommitException("INVALID_LEVEL_ORDER",
                    "level_order is not a valid integer: " + raw);
        }
    }

    private static boolean numericEquals(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.compareTo(b) == 0;
    }

    private static String req(Map<String, String> row, String key) {
        String v = row.get(key);
        if (v == null || v.isBlank()) {
            throw new ImportRowCommitException("MISSING_REQUIRED_FIELD",
                    "Required field missing: " + key);
        }
        return v.trim();
    }

    private static String opt(Map<String, String> row, String key) {
        String v = row.get(key);
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }

    private void audit(ImportRowCommitContext ctx, String action,
                       String entityType, UUID entityId) {
        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(ctx.projectId())
                .actorUserId(ctx.actorUserId())
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .reason("import_batch=" + ctx.importBatchId())
                .build());
    }

    /**
     * Parsed methodology-level metadata carried identically on every file row.
     * {@code name} is the required {@code ru-RU} text; {@code nameI18n} is the
     * full locale map assembled from {@code methodology_name} + its optional
     * per-locale siblings.
     */
    private record Metadata(String code, String name, Map<String, String> nameI18n,
                            MethodologyType type, ScoringMode mode,
                            BigDecimal totalPoints) { }
}
