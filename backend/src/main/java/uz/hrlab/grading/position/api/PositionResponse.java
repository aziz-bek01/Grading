package uz.hrlab.grading.position.api;

import uz.hrlab.grading.position.domain.Position;
import uz.hrlab.grading.position.domain.PositionStatus;

import java.util.Map;
import java.util.UUID;

public record PositionResponse(
        UUID id,
        UUID projectId,
        UUID departmentId,
        String code,
        Map<String, String> titleI18n,
        String function,
        String category,
        String jobFamily,
        String jobLevel,
        PositionStatus status
) {
    public static PositionResponse from(Position p) {
        return new PositionResponse(p.id(), p.projectId(), p.departmentId(), p.code(),
                p.titleI18n(), p.function(), p.category(), p.jobFamily(), p.jobLevel(), p.status());
    }
}
