package uz.hrlab.grading.methodology.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("workflow")
class MethodologyVersionPrimaryLocaleValidatorTest {

    private final MethodologyVersionPrimaryLocaleValidator validator =
            new MethodologyVersionPrimaryLocaleValidator();

    @Test
    void primaryLocalePresentPasses() {
        assertThatCode(() -> validator.validate(List.of(
                factor("EDU", Map.of("ru-RU", "Образование")),
                factor("EXP", Map.of("ru-RU", "Опыт", "en-US", "Experience")))))
                .doesNotThrowAnyException();
    }

    @Test
    void missingPrimaryLocaleFails() {
        assertThatThrownBy(() -> validator.validate(List.of(
                factor("EDU", Map.of("en-US", "Education")))))
                .isInstanceOf(MethodologyVersionTransitionRejectedException.class)
                .hasMessageContaining("EDU");
    }

    @Test
    void blankPrimaryLocaleFails() {
        assertThatThrownBy(() -> validator.validate(List.of(
                factor("EDU", Map.of("ru-RU", "   ")))))
                .isInstanceOf(MethodologyVersionTransitionRejectedException.class);
    }

    @Test
    void nullMapFails() {
        assertThatThrownBy(() -> validator.validate(List.of(
                factor("EDU", null))))
                .isInstanceOf(MethodologyVersionTransitionRejectedException.class);
    }

    private static MethodologyVersionPrimaryLocaleValidator.FactorNameView factor(
            String code, Map<String, String> name) {
        return new MethodologyVersionPrimaryLocaleValidator.FactorNameView() {
            @Override public String code() { return code; }
            @Override public Map<String, String> nameI18n() { return name; }
        };
    }
}
