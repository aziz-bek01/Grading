package uz.hrlab.grading.gradestructure.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.gradestructure.domain.GradeBandLookupResult;
import uz.hrlab.grading.gradestructure.infrastructure.GradeBandJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeBandRepository;
import uz.hrlab.grading.gradestructure.infrastructure.GradeJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeRepository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * KEY integration component: maps a raw evaluation score onto a
 * {@link GradeBandLookupResult} for a given {@link
 * uz.hrlab.grading.gradestructure.domain.GradeStructure}.
 *
 * <p>Both endpoints inclusive: returns the band where
 * {@code band.minScore &lt;= totalScore &lt;= band.maxScore}. If no band matches
 * (score is below the lowest band, above the highest band, or in a gap when
 * gap_policy = ALLOW_GAPS_WARN), returns {@link Optional#empty()} — callers
 * decide whether to leave grade fields null + log a warning.
 *
 * <p>The lookup is tenant-scoped — callers must pass the active tenant id.
 */
@Service
public class GradeBandLookupService {

    private final GradeBandRepository bands;
    private final GradeRepository grades;

    public GradeBandLookupService(GradeBandRepository bands, GradeRepository grades) {
        this.bands = bands;
        this.grades = grades;
    }

    @Transactional(readOnly = true)
    public Optional<GradeBandLookupResult> lookup(UUID tenantId, UUID gradeStructureId,
                                                  BigDecimal totalScore) {
        if (tenantId == null || gradeStructureId == null || totalScore == null) {
            return Optional.empty();
        }
        var allBands = bands.findAllByTenantIdAndGradeStructureIdOrderByMinScoreAsc(
                tenantId, gradeStructureId);
        for (GradeBandJpaEntity b : allBands) {
            if (b.getMinScore().compareTo(totalScore) <= 0
                    && totalScore.compareTo(b.getMaxScore()) <= 0) {
                GradeJpaEntity grade = grades
                        .findByIdAndTenantId(b.getGradeId(), tenantId)
                        .orElse(null);
                if (grade == null) {
                    return Optional.empty();
                }
                return Optional.of(new GradeBandLookupResult(
                        b.getId(), grade.getId(), grade.getGradeNumber()));
            }
        }
        return Optional.empty();
    }
}
