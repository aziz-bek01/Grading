package uz.hrlab.grading.organization.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import uz.hrlab.grading.organization.domain.DepartmentType;

import java.util.Map;
import java.util.UUID;

public record UpdateDepartmentCommand(
        UUID parentId,
        Map<@NotBlank String, @NotBlank @Size(max = 500) String> nameI18n,
        DepartmentType type
) { }
