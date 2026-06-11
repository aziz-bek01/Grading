package uz.hrlab.grading.gradestructure.api;

import java.util.UUID;

/**
 * Response for save-as-template (BE-9) — the id of the new tenant CUSTOM grade
 * template row in {@code grade_templates}. The FE uses it to refresh the picker
 * / target the manage endpoints. Serializes as {@code template_id}.
 */
public record GradeStructureTemplateCreatedResponse(UUID templateId) { }
