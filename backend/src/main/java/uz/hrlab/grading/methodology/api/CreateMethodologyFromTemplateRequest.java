package uz.hrlab.grading.methodology.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import uz.hrlab.grading.common.validation.SupportedLocaleKeys;

import java.util.Map;
import java.util.UUID;

public record CreateMethodologyFromTemplateRequest(
        @NotBlank String templateCode,
        UUID projectId,
        @NotBlank String code,
        @NotEmpty @SupportedLocaleKeys Map<String, String> nameI18n,
        @SupportedLocaleKeys Map<String, String> descriptionI18n
) { }
