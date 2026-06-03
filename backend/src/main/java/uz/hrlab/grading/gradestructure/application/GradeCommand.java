package uz.hrlab.grading.gradestructure.application;

import java.math.BigDecimal;
import java.util.Map;

public final class GradeCommand {

    private GradeCommand() { }

    public record Create(int gradeNumber, int sortOrder,
                          Map<String, String> nameI18n,
                          Map<String, String> descriptionI18n) { }

    public record Update(int gradeNumber, int sortOrder,
                          Map<String, String> nameI18n,
                          Map<String, String> descriptionI18n) { }

    public record UpsertBand(BigDecimal minScore, BigDecimal maxScore) { }
}
