package uz.hrlab.grading.methodology.api;

import uz.hrlab.grading.common.validation.SupportedLocaleKeys;
import uz.hrlab.grading.methodology.domain.MethodologyType;

import java.util.Map;

/**
 * Partial-update body for {@code PATCH /api/v1/methodologies/{id}}. All fields
 * nullable. {@code methodology_type} is only honoured while the methodology has
 * no APPROVED/LOCKED version (otherwise the use case rejects with
 * {@code METHODOLOGY_TYPE_LOCKED}).
 */
public record UpdateMethodologyMetadataRequest(
        @SupportedLocaleKeys Map<String, String> nameI18n,
        @SupportedLocaleKeys Map<String, String> descriptionI18n,
        MethodologyType methodologyType
) { }
