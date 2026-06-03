package uz.hrlab.grading.methodology.application;

import org.springframework.stereotype.Component;
import uz.hrlab.grading.common.exception.ResourceNotFoundException;
import uz.hrlab.grading.methodology.domain.MethodologyType;
import uz.hrlab.grading.methodology.domain.ScoringMode;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory catalog of built-in methodology templates (architecture §9 Domain
 * Model — CLASSIC_8_FACTOR, EXTENDED_11_CRITERIA, CUSTOM).
 *
 * <p>Each template is a (factors, levels) skeleton that
 * {@code CreateMethodologyFromTemplateUseCase} deep-copies into a new DRAFT
 * version. Translations include the primary locale ({@code ru-RU}); MVP 1
 * ships a single locale per factor name — frontend can edit them after
 * instantiation. Tested by Phase 4 integration test
 * {@code TemplateInstantiationIntegrationTest}.
 */
@Component
public class MethodologyTemplateRegistry {

    private final Map<String, MethodologyTemplate> templates;

    public MethodologyTemplateRegistry() {
        this.templates = Map.of(
                "CLASSIC_8_FACTOR",      classic8(),
                "EXTENDED_11_CRITERIA",  extended11(),
                "CUSTOM",                customEmpty()
        );
    }

    public MethodologyTemplate require(String code) {
        return findByCode(code).orElseThrow(() ->
                new ResourceNotFoundException("Methodology template not found: " + code));
    }

    public Optional<MethodologyTemplate> findByCode(String code) {
        return Optional.ofNullable(templates.get(code));
    }

    private static MethodologyTemplate classic8() {
        // CLASSIC_8_FACTOR — total 1000 points distributed across 8 factors,
        // each with 5 levels.
        BigDecimal[] weights = {
                bd(180), bd(150), bd(140), bd(130),
                bd(120), bd(110), bd(90),  bd(80)
        };
        String[] codes = {
                "EDUCATION", "EXPERIENCE", "MANAGEMENT", "PROBLEM_SOLVING",
                "RESPONSIBILITY", "COMMUNICATION", "WORKING_CONDITIONS", "IMPACT_ZONE"
        };
        String[] names = {
                "Образование", "Опыт работы", "Управление",
                "Решение проблем", "Ответственность", "Коммуникации",
                "Условия труда", "Зона влияния"
        };
        List<MethodologyTemplate.FactorTemplate> factors = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) {
            factors.add(new MethodologyTemplate.FactorTemplate(
                    codes[i],
                    Map.of("ru-RU", names[i]),
                    weights[i],
                    weights[i],   // maxPoints == weight in DIRECT_POINTS
                    i + 1,
                    true,
                    fiveLevels(weights[i])
            ));
        }
        return new MethodologyTemplate(
                "CLASSIC_8_FACTOR",
                MethodologyType.CLASSIC_8_FACTOR,
                ScoringMode.DIRECT_POINTS,
                bd(1000),
                Map.of("ru-RU", "Классическая 8-факторная методика"),
                Map.of("ru-RU", "Базовая 8-факторная модель грейдинга (1000 баллов)"),
                factors
        );
    }

    private static List<MethodologyTemplate.LevelTemplate> fiveLevels(BigDecimal max) {
        // Levels 1..5 distributing max points evenly.
        BigDecimal step = max.divide(bd(5), 4, java.math.RoundingMode.HALF_UP);
        List<MethodologyTemplate.LevelTemplate> levels = new java.util.ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            BigDecimal pts = step.multiply(bd(i));
            levels.add(new MethodologyTemplate.LevelTemplate(
                    "L" + i, i, pts, null,
                    Map.of("ru-RU", "Уровень " + i)
            ));
        }
        return levels;
    }

    private static MethodologyTemplate extended11() {
        // EXTENDED_11_CRITERIA — weights are percentages summing to 100.
        BigDecimal[] weights = {
                bd("12.00"), bd("10.00"), bd("8.00"), bd("8.00"),
                bd("8.00"),  bd("10.00"), bd("8.00"), bd("12.00"),
                bd("8.00"),  bd("8.00"),  bd("8.00")
        };
        String[] codes = {
                "EDU_EXP", "UNIQUE_QUAL", "FUNCTIONAL_SUB", "ADMIN_SUB",
                "INTERNAL_COMM", "EXTERNAL_COMM", "INNOVATION",
                "ANALYSIS_COMPLEXITY", "METHODOLOGY_ROLE", "DECISION_FREEDOM",
                "FINANCIAL_IMPACT"
        };
        String[] names = {
                "Образование и опыт", "Уникальная квалификация",
                "Функциональное подчинение", "Административное подчинение",
                "Внутренние коммуникации", "Внешние коммуникации",
                "Инновации", "Сложность анализа", "Роль в методологии",
                "Свобода принятия решений", "Финансовое влияние"
        };
        List<MethodologyTemplate.FactorTemplate> factors = new java.util.ArrayList<>();
        for (int i = 0; i < 11; i++) {
            factors.add(new MethodologyTemplate.FactorTemplate(
                    codes[i],
                    Map.of("ru-RU", names[i]),
                    weights[i],
                    weights[i],
                    i + 1,
                    true,
                    fiveScaleLevels()
            ));
        }
        // Verify weight sum == 100 to fail loudly on accidental edits.
        BigDecimal sum = java.util.Arrays.stream(weights).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(new BigDecimal("100.0000")) != 0) {
            throw new IllegalStateException("EXTENDED_11_CRITERIA template weight sum != 100: " + sum);
        }
        return new MethodologyTemplate(
                "EXTENDED_11_CRITERIA",
                MethodologyType.EXTENDED_11_CRITERIA,
                ScoringMode.WEIGHTED_POINTS,
                null,
                Map.of("ru-RU", "Расширенная 11-критериальная методика"),
                Map.of("ru-RU", "Расширенная 11-критериальная модель HRLab"),
                factors
        );
    }

    private static List<MethodologyTemplate.LevelTemplate> fiveScaleLevels() {
        List<MethodologyTemplate.LevelTemplate> levels = new java.util.ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            levels.add(new MethodologyTemplate.LevelTemplate(
                    "L" + i, i,
                    bd(i * 20),       // points (used for DIRECT mode fallback)
                    bd(i),            // scaleValue 1..5
                    Map.of("ru-RU", "Уровень " + i)
            ));
        }
        return levels;
    }

    private static MethodologyTemplate customEmpty() {
        return new MethodologyTemplate(
                "CUSTOM",
                MethodologyType.CUSTOM,
                ScoringMode.DIRECT_POINTS,
                null,
                Map.of("ru-RU", "Пользовательская методика"),
                Map.of("ru-RU", "Пустой шаблон — настройте факторы вручную"),
                List.of()
        );
    }

    private static BigDecimal bd(long v) { return new BigDecimal(v).setScale(4); }
    private static BigDecimal bd(String v) { return new BigDecimal(v).setScale(4); }
}
