package uz.hrlab.grading.jobanalysis.api;

import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record UpdateAnswerRequest(
        @Size(max = 20000) String answerText,
        List<@Size(max = 200) String> answerChoices,
        BigDecimal answerNumber
) { }
