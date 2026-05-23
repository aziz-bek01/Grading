package uz.hrlab.grading.project.api;

import uz.hrlab.grading.project.domain.Project;
import uz.hrlab.grading.project.domain.ProjectStatus;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        String code,
        Map<String, String> nameI18n,
        String description,
        ProjectStatus status,
        UUID methodologyVersionId,
        LocalDate startDate,
        LocalDate endDate
) {
    public static ProjectResponse from(Project p) {
        return new ProjectResponse(p.id(), p.code(), p.nameI18n(), p.description(), p.status(),
                p.methodologyVersionId(), p.startDate(), p.endDate());
    }
}
