package uz.hrlab.grading.methodology.api;

import java.util.UUID;

/**
 * Response for save-as-template (Epic E) — the id of the new tenant CUSTOM
 * template row in {@code methodology_templates}. The FE uses it to refresh the
 * picker / target the manage endpoints. Serializes as {@code template_id}.
 */
public record MethodologyTemplateCreatedResponse(UUID templateId) { }
