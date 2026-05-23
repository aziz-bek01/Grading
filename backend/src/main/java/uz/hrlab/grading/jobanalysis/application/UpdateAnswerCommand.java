package uz.hrlab.grading.jobanalysis.application;

import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** Idempotent upsert payload — at least one of the three answer fields. */
public record UpdateAnswerCommand(
        @Size(max = 20000) String answerText,
        List<@Size(max = 200) String> answerChoices,
        BigDecimal answerNumber
) { }
