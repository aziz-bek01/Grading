package uz.hrlab.grading.organization.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import uz.hrlab.grading.organization.domain.DepartmentType;

import java.util.Map;
import java.util.UUID;

public record CreateDepartmentCommand(
        @NotNull UUID projectId,
        UUID parentId,
        @NotBlank @Pattern(regexp = "^[A-Z0-9][A-Z0-9_-]{0,63}$") String code,
        @NotNull @NotEmpty
        Map<@NotBlank String, @NotBlank @Size(max = 500) String> nameI18n,
        @NotNull DepartmentType type
) { }
