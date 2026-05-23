package uz.hrlab.grading.common.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-level verification of {@link SupportedLocaleKeys} (F-308). Drives Bean
 * Validation directly so the test does not need the Spring context.
 */
@Tag("validation")
class SupportedLocaleKeysValidatorTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setup() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        if (factory != null) factory.close();
    }

    record Probe(@SupportedLocaleKeys Map<String, String> i18n) { }

    @Test
    void acceptsRuRU() {
        var violations = validator.validate(new Probe(Map.of("ru-RU", "hello")));
        assertThat(violations).isEmpty();
    }

    @Test
    void acceptsAllFourSupportedLocales() {
        var violations = validator.validate(new Probe(Map.of(
                "ru-RU", "Цель",
                "uz-Cyrl-UZ", "Мақсад",
                "uz-Latn-UZ", "Maqsad",
                "en-US", "Purpose")));
        assertThat(violations).isEmpty();
    }

    @Test
    void acceptsNullMap() {
        var violations = validator.validate(new Probe(null));
        assertThat(violations).isEmpty();
    }

    @Test
    void acceptsEmptyMap() {
        var violations = validator.validate(new Probe(Map.of()));
        assertThat(violations).isEmpty();
    }

    @Test
    void rejectsUnknownLocaleKey() {
        Set<ConstraintViolation<Probe>> violations =
                validator.validate(new Probe(Map.of("xx-XX", "hello")));
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .contains("INVALID_LOCALE_KEY");
    }

    @Test
    void rejectsMixOfSupportedAndUnknown() {
        var violations = validator.validate(new Probe(Map.of(
                "ru-RU", "Цель",
                "fr-FR", "Objectif")));
        assertThat(violations).hasSize(1);
    }
}
