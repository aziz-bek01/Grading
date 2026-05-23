package uz.hrlab.grading.organization.api;

import uz.hrlab.grading.organization.domain.Department;
import uz.hrlab.grading.organization.domain.DepartmentStatus;
import uz.hrlab.grading.organization.domain.DepartmentType;

import java.util.Map;
import java.util.UUID;

public record DepartmentResponse(
        UUID id,
        UUID projectId,
        UUID parentId,
        String code,
        Map<String, String> nameI18n,
        DepartmentType type,
        DepartmentStatus status
) {
    public static DepartmentResponse from(Department d) {
        return new DepartmentResponse(d.id(), d.projectId(), d.parentId(), d.code(),
                d.nameI18n(), d.type(), d.status());
    }
}
