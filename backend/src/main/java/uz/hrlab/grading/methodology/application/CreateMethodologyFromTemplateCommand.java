package uz.hrlab.grading.methodology.application;

import java.util.Map;
import java.util.UUID;

/** Command for {@code CreateMethodologyFromTemplateUseCase}. */
public record CreateMethodologyFromTemplateCommand(
        String templateCode,
        UUID projectId,
        String code,
        Map<String, String> nameI18n,
        Map<String, String> descriptionI18n
) { }
