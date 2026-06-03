package uz.hrlab.grading.phase4;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uz.hrlab.grading.audit.application.AuditAction;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Phase 4 audit action catalog presence. */
@Tag("audit")
class Phase4AuditActionsTest {

    private static final Set<String> EXPECTED_METHODOLOGY = Set.of(
            "METHODOLOGY_CREATED",
            "METHODOLOGY_UPDATED",
            "METHODOLOGY_ARCHIVED",
            "METHODOLOGY_APPROVED",
            "METHODOLOGY_LOCKED",
            "METHODOLOGY_VERSION_CREATED",
            "METHODOLOGY_VERSION_APPROVED",
            "METHODOLOGY_VERSION_LOCKED",
            "METHODOLOGY_VERSION_ARCHIVED",
            "METHODOLOGY_VERSION_REVISION_CREATED"
    );

    private static final Set<String> EXPECTED_FACTOR = Set.of(
            "FACTOR_CREATED",
            "FACTOR_UPDATED",
            "FACTOR_REMOVED",
            "FACTOR_REORDERED",
            "FACTOR_LEVEL_CREATED",
            "FACTOR_LEVEL_UPDATED",
            "FACTOR_LEVEL_REMOVED",
            "FACTOR_LEVEL_REORDERED"
    );

    @Test
    void allMethodologyActionsArePresent() throws IllegalAccessException {
        assertThat(stringConstants()).containsAll(EXPECTED_METHODOLOGY);
    }

    @Test
    void allFactorActionsArePresent() throws IllegalAccessException {
        assertThat(stringConstants()).containsAll(EXPECTED_FACTOR);
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
