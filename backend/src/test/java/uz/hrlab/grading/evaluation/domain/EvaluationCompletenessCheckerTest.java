package uz.hrlab.grading.evaluation.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uz.hrlab.grading.methodology.domain.Factor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("workflow")
class EvaluationCompletenessCheckerTest {

    private final EvaluationCompletenessChecker checker = new EvaluationCompletenessChecker();
    private final UUID tenant = UUID.randomUUID();
    private final UUID version = UUID.randomUUID();

    @Test
    void noFactorsScoredYieldsAllRequiredMissing() {
        Factor a = factor("A", true);
        Factor b = factor("B", true);
        Factor c = factor("C", false);
        var missing = checker.missingRequiredFactors(List.of(a, b, c), Set.of());
        assertThat(missing).containsExactlyInAnyOrder(a.id(), b.id());
    }

    @Test
    void optionalFactorMissingDoesNotCountAsIncomplete() {
        Factor a = factor("A", true);
        Factor b = factor("B", false);
        var missing = checker.missingRequiredFactors(List.of(a, b), Set.of(a.id()));
        assertThat(missing).isEmpty();
    }

    @Test
    void allRequiredScoredReturnsEmpty() {
        Factor a = factor("A", true);
        Factor b = factor("B", true);
        var missing = checker.missingRequiredFactors(List.of(a, b), Set.of(a.id(), b.id()));
        assertThat(missing).isEmpty();
    }

    @Test
    void recomputeStatusFlow() {
        Factor a = factor("A", true);
        Factor b = factor("B", true);
        List<Factor> factors = List.of(a, b);

        assertThat(checker.recomputeStatus(EvaluationStatus.DRAFT, factors, Set.of()))
                .isEqualTo(EvaluationStatus.DRAFT);
        assertThat(checker.recomputeStatus(EvaluationStatus.DRAFT, factors, Set.of(a.id())))
                .isEqualTo(EvaluationStatus.INCOMPLETE);
        assertThat(checker.recomputeStatus(EvaluationStatus.INCOMPLETE, factors,
                Set.of(a.id(), b.id()))).isEqualTo(EvaluationStatus.COMPLETE);
        assertThat(checker.recomputeStatus(EvaluationStatus.COMPLETE, factors, Set.of(a.id())))
                .isEqualTo(EvaluationStatus.INCOMPLETE);
    }

    @Test
    void recomputeDoesNotMutateCommittedStatuses() {
        Factor a = factor("A", true);
        for (EvaluationStatus s : new EvaluationStatus[]{
                EvaluationStatus.SUBMITTED, EvaluationStatus.APPROVED,
                EvaluationStatus.LOCKED, EvaluationStatus.ARCHIVED}) {
            assertThat(checker.recomputeStatus(s, List.of(a), Set.of(a.id()))).isEqualTo(s);
        }
    }

    @Test
    void hasAllRequiredHelper() {
        Factor a = factor("A", true);
        assertThat(checker.hasAllRequired(List.of(a), Map.of(a.id(), new Object()))).isTrue();
        assertThat(checker.hasAllRequired(List.of(a), Map.of())).isFalse();
    }

    private Factor factor(String code, boolean required) {
        return new Factor(UUID.randomUUID(), tenant, version, code,
                Map.of("ru-RU", code), Map.of(), BigDecimal.ZERO, BigDecimal.ZERO, 1, required);
    }
}
