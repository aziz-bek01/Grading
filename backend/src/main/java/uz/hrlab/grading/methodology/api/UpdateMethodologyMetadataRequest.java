package uz.hrlab.grading.methodology.api;

import uz.hrlab.grading.common.validation.SupportedLocaleKeys;

import java.util.Map;

public record UpdateMethodologyMetadataRequest(
        @SupportedLocaleKeys Map<String, String> nameI18n,
        @SupportedLocaleKeys Map<String, String> descriptionI18n
) { }
