package uz.hrlab.grading.gradestructure.application;

import org.springframework.stereotype.Component;
import uz.hrlab.grading.common.exception.ResourceNotFoundException;
import uz.hrlab.grading.gradestructure.domain.GradeStructureType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory catalog of built-in grade structure templates.
 *
 * <ul>
 *   <li>{@code GRADE_14} — 14 grades, score range 0..1000, evenly distributed,
 *       each band ends exactly one epsilon (0.0001) before the next starts so
 *       the bands are contiguous + non-overlapping.</li>
 *   <li>{@code GRADE_16} — same idea, 16 grades.</li>
 *   <li>{@code CUSTOM} — empty (user populates).</li>
 * </ul>
 *
 * <p>Names are seeded with the full 4-locale set per architecture §15.3.
 */
@Component
public class GradeStructureTemplateRegistry {

    private final Map<String, GradeStructureTemplate> templates;

    public GradeStructureTemplateRegistry() {
        this.templates = Map.of(
                "GRADE_14", grade14(),
                "GRADE_16", grade16(),
                "CUSTOM",   customEmpty()
        );
    }

    public GradeStructureTemplate require(String code) {
        return findByCode(code).orElseThrow(() ->
                new ResourceNotFoundException("Grade structure template not found: " + code));
    }

    public Optional<GradeStructureTemplate> findByCode(String code) {
        return Optional.ofNullable(templates.get(code));
    }

    private static GradeStructureTemplate grade14() {
        return evenlyDistributed("GRADE_14", GradeStructureType.GRADE_14, 14,
                "14-грейдная модель", "14-grade structure",
                "14-грейдли модели", "14-grayd modeli");
    }

    private static GradeStructureTemplate grade16() {
        return evenlyDistributed("GRADE_16", GradeStructureType.GRADE_16, 16,
                "16-грейдная модель", "16-grade structure",
                "16-грейдли модели", "16-grayd modeli");
    }

    private static GradeStructureTemplate customEmpty() {
        return new GradeStructureTemplate(
                "CUSTOM", GradeStructureType.CUSTOM, 0,
                BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP),
                Map.of(
                        "ru-RU",       "Пользовательская грейдинговая структура",
                        "en-US",       "Custom grade structure",
                        "uz-Cyrl-UZ",  "Махсус грейдлаш тузилмаси",
                        "uz-Latn-UZ",  "Maxsus greydlash tuzilmasi"
                ),
                Map.of(
                        "ru-RU",       "Пустой шаблон — добавьте грейды вручную",
                        "en-US",       "Empty template — add grades manually",
                        "uz-Cyrl-UZ",  "Бўш шаблон — грейдларни қўлда қўшинг",
                        "uz-Latn-UZ",  "Bo'sh shablon — greydlarni qo'lda qo'shing"
                ),
                List.of()
        );
    }

    /**
     * Build an evenly-distributed template across 0..1000 score range.
     * Bands are contiguous: band[i].max = floor((i+1) * 1000 / n) - 0.0001 etc.
     */
    private static GradeStructureTemplate evenlyDistributed(String code,
                                                            GradeStructureType type,
                                                            int n,
                                                            String nameRu,
                                                            String nameEn,
                                                            String nameUzCyrl,
                                                            String nameUzLatn) {
        BigDecimal total = new BigDecimal("1000").setScale(4, RoundingMode.HALF_UP);
        BigDecimal step  = total.divide(new BigDecimal(n), 4, RoundingMode.HALF_UP);
        BigDecimal epsilon = new BigDecimal("0.0001").setScale(4, RoundingMode.HALF_UP);

        List<GradeStructureTemplate.GradeRowTemplate> grades = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            BigDecimal lo;
            BigDecimal hi;
            if (i == 1) {
                lo = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
            } else {
                lo = step.multiply(new BigDecimal(i - 1)).setScale(4, RoundingMode.HALF_UP);
            }
            if (i == n) {
                hi = total;
            } else {
                hi = step.multiply(new BigDecimal(i)).subtract(epsilon)
                        .setScale(4, RoundingMode.HALF_UP);
            }
            Map<String, String> nameI18n = new LinkedHashMap<>();
            nameI18n.put("ru-RU",      "Грейд " + i);
            nameI18n.put("en-US",      "Grade " + i);
            nameI18n.put("uz-Cyrl-UZ", "Грейд " + i);
            nameI18n.put("uz-Latn-UZ", "Greyd " + i);
            grades.add(new GradeStructureTemplate.GradeRowTemplate(i, i, nameI18n, lo, hi));
        }

        Map<String, String> nameI18n = new LinkedHashMap<>();
        nameI18n.put("ru-RU", nameRu);
        nameI18n.put("en-US", nameEn);
        nameI18n.put("uz-Cyrl-UZ", nameUzCyrl);
        nameI18n.put("uz-Latn-UZ", nameUzLatn);

        return new GradeStructureTemplate(code, type, n, BigDecimal.ZERO.setScale(4), total,
                nameI18n,
                Map.of("ru-RU",      n + "-грейдная модель, 0–1000 баллов",
                       "en-US",      n + "-grade model, 0–1000 points",
                       "uz-Cyrl-UZ", n + "-грейдли модель, 0–1000 балл",
                       "uz-Latn-UZ", n + "-greydli model, 0–1000 ball"),
                grades);
    }
}
