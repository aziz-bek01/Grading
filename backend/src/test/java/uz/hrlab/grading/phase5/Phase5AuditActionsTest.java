package uz.hrlab.grading.phase5;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.audit.application.AuditAction;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 5 catalog presence — audit action codes + permission codes referenced
 * by use cases must exist as constants.
 */
@Tag("audit")
class Phase5AuditActionsTest {

    private static final Set<String> EXPECTED_EVALUATION_AUDIT = Set.of(
            "EVALUATION_CREATED",
            "EVALUATION_UPDATED",
            "EVALUATION_SCORE_UPSERTED",
            "EVALUATION_SCORE_DELETED",
            "EVALUATION_SUBMITTED",
            "EVALUATION_CHANGES_REQUESTED",
            "EVALUATION_APPROVED",
            "EVALUATION_LOCKED",
            "EVALUATION_ARCHIVED",
            "EVALUATION_DELETED",
            "EVALUATION_SCORE_CALIBRATED"
    );

    private static final Set<String> EXPECTED_EVALUATION_PERMISSIONS = Set.of(
            "EVALUATION_READ",
            "EVALUATION_EDIT",
            "EVALUATION_APPROVE",
            "EVALUATION_LOCK",
            "CALIBRATION_EDIT"
    );

    @Test
    void allEvaluationAuditActionsArePresent() throws IllegalAccessException {
        assertThat(stringConstants(AuditAction.class)).containsAll(EXPECTED_EVALUATION_AUDIT);
    }

    @Test
    void allEvaluationPermissionsArePresent() throws IllegalAccessException {
        assertThat(stringConstants(PermissionCodes.class)).containsAll(EXPECTED_EVALUATION_PERMISSIONS);
    }

    private static Set<String> stringConstants(Class<?> type) throws IllegalAccessException {
        Set<String> out = new HashSet<>();
        for (Field f : type.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())
                    && Modifier.isFinal(f.getModifiers())
                    && f.getType().equals(String.class)) {
                f.setAccessible(true);
                out.add((String) f.get(null));
            }
        }
        return out;
    }
}
