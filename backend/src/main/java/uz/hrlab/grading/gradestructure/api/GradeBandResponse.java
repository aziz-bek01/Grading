package uz.hrlab.grading.gradestructure.api;

import uz.hrlab.grading.gradestructure.domain.GradeBand;
import uz.hrlab.grading.gradestructure.infrastructure.GradeBandJpaEntity;

import java.math.BigDecimal;
import java.util.UUID;

public record GradeBandResponse(UUID id, UUID gradeId, BigDecimal minScore, BigDecimal maxScore) {

    public static GradeBandResponse from(GradeBand b) {
        return new GradeBandResponse(b.id(), b.gradeId(), b.minScore(), b.maxScore());
    }

    public static GradeBandResponse from(GradeBandJpaEntity b) {
        return new GradeBandResponse(b.getId(), b.getGradeId(), b.getMinScore(), b.getMaxScore());
    }
}
