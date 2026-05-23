package uz.hrlab.grading.phase3;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uz.hrlab.grading.audit.application.AuditAction;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lightweight static check that every Phase 3 audit action is published from
 * the {@link AuditAction} catalogue. Hash-chain integrity is exercised by the
 * existing {@code AuditAppendOnlyTest} (integration, Docker-gated).
 */
@Tag("audit")
class Phase3AuditActionsTest {

    private static final Set<String> EXPECTED_JOB_PROFILE = Set.of(
            "JOB_PROFILE_CREATED",
            "JOB_PROFILE_UPDATED",
            "JOB_PROFILE_SUBMITTED",
            "JOB_PROFILE_CHANGES_REQUESTED",
            "JOB_PROFILE_APPROVED",
            "JOB_PROFILE_ARCHIVED",
            "JOB_PROFILE_REVISION_CREATED"
    );

    private static final Set<String> EXPECTED_JOB_ANALYSIS = Set.of(
            "JOB_ANALYSIS_QUESTIONNAIRE_CREATED",
            "JOB_ANALYSIS_ANSWER_UPDATED",
            "JOB_ANALYSIS_SUBMITTED",
            "JOB_ANALYSIS_ARCHIVED"
    );

    @Test
    void allPhase3JobProfileActionsArePresent() throws IllegalAccessException {
        assertThat(stringConstants()).containsAll(EXPECTED_JOB_PROFILE);
    }

    @Test
    void allPhase3JobAnalysisActionsArePresent() throws IllegalAccessException {
        assertThat(stringConstants()).containsAll(EXPECTED_JOB_ANALYSIS);
    }

    private static Set<String> stringConstants() throws IllegalAccessException {
        Set<String> values = new HashSet<>();
        for (Field f : AuditAction.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())
                    && Modifier.isPublic(f.getModifiers())
                    && Modifier.isFinal(f.getModifiers())
                    && f.getType() == String.class) {
                values.add((String) f.get(null));
            }
        }
        return values;
    }
}
