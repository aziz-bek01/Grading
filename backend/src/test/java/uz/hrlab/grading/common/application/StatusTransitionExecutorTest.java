package uz.hrlab.grading.common.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.tenancy.application.TenantContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit test for the shared {@link StatusTransitionExecutor} / {@link StatusTransition}
 * skeleton that 17 status-transition use cases delegate to. Placed in the executor's
 * own package so the mandatory-field null-check cases can null a single fluent slot
 * directly (the slots are package-private).
 *
 * <p>Every slot is a recording fake so the test can assert the executor's exact
 * canonical ORDER and its side-effect guarantees, without any real use case.
 */
@ExtendWith(MockitoExtension.class)
class StatusTransitionExecutorTest {

    @Mock AbacGate abacGate;
    @Mock AuditService audit;

    private final TenantContext ctx = new TenantContext(
            UUID.randomUUID(), UUID.randomUUID(), Set.of(), Set.of(), Set.of(),
            Set.of(), false, "ru-RU");
    private final UUID entityId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();

    /** Ordered log of every slot invocation, shared by all recording fakes. */
    private final List<String> steps = new ArrayList<>();

    private StatusTransitionExecutor newExecutor() {
        return new StatusTransitionExecutor(abacGate, audit);
    }

    /** Records "audit" in the same step log whenever the executor records a row. */
    private void recordAuditStep() {
        doAnswer(inv -> {
            steps.add("audit");
            return null;
        }).when(audit).record(any());
    }

    /**
     * A fully-populated transition whose slots each append their name to {@link
     * #steps}. The snapshot supplier returns "before" on its first call and
     * "after" on its second, so the test can prove which snapshot feeds which
     * audit field.
     */
    private StatusTransition fullTransition(StatusTransitionExecutor executor) {
        AtomicInteger snapshotCalls = new AtomicInteger();
        Supplier<JsonNode> snapshot = () -> {
            steps.add("snapshot");
            return TextNode.valueOf(snapshotCalls.getAndIncrement() == 0 ? "before" : "after");
        };
        return executor.transition(ctx)
                .abac(() -> steps.add("abac"))
                .checkTransition(() -> steps.add("transitionCheck"))
                .beforeMutate(() -> steps.add("beforeMutate"))
                .snapshot(snapshot)
                .mutate(() -> steps.add("mutate"))
                .save(() -> steps.add("save"))
                .afterSave(() -> steps.add("afterSave"))
                .audit("STATUS_CHANGED", "Widget", entityId, projectId)
                .reason("because");
    }

    // (a) canonical ordering ------------------------------------------------

    @Test
    void runsAllStepsInCanonicalOrderWithOptionalHooks() {
        recordAuditStep();
        StatusTransitionExecutor executor = newExecutor();

        fullTransition(executor).execute();

        assertThat(steps).containsExactly(
                "abac", "transitionCheck", "beforeMutate",
                "snapshot", "mutate", "save", "snapshot",
                "audit", "afterSave");

        // The two snapshots feed beforeJson / afterJson respectively; the audit
        // carries the action / entity / reason wired on the transition.
        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).record(event.capture());
        assertThat(event.getValue().action()).isEqualTo("STATUS_CHANGED");
        assertThat(event.getValue().entityType()).isEqualTo("Widget");
        assertThat(event.getValue().entityId()).isEqualTo(entityId);
        assertThat(event.getValue().projectId()).isEqualTo(projectId);
        assertThat(event.getValue().reason()).isEqualTo("because");
        assertThat(event.getValue().beforeJson()).isEqualTo(TextNode.valueOf("before"));
        assertThat(event.getValue().afterJson()).isEqualTo(TextNode.valueOf("after"));
        assertThat(event.getValue().tenantId()).isEqualTo(ctx.tenantId());
    }

    @Test
    void skipsAbsentOptionalHooksButKeepsMandatoryOrder() {
        recordAuditStep();
        StatusTransitionExecutor executor = newExecutor();

        executor.transition(ctx)
                .abac(() -> steps.add("abac"))
                .checkTransition(() -> steps.add("transitionCheck"))
                .snapshot(() -> {
                    steps.add("snapshot");
                    return TextNode.valueOf("s");
                })
                .mutate(() -> steps.add("mutate"))
                .save(() -> steps.add("save"))
                .audit("STATUS_CHANGED", "Widget", entityId, projectId)
                .execute();

        // No beforeMutate / afterSave slots → those steps are simply absent.
        assertThat(steps).containsExactly(
                "abac", "transitionCheck", "snapshot", "mutate", "save",
                "snapshot", "audit");
    }

    // (b) mandatory-field null checks fail before ANY side effect -----------

    @Test
    void everyMandatoryFieldIsRequiredAndFailsBeforeAnySideEffect() {
        record Case(String message, Consumer<StatusTransition> nuller) { }
        List<Case> cases = List.of(
                new Case("abac scope is required", t -> t.abac = null),
                new Case("transition check is required", t -> t.transitionCheck = null),
                new Case("audit snapshot supplier is required", t -> t.snapshot = null),
                new Case("mutate step is required", t -> t.mutate = null),
                new Case("save step is required", t -> t.save = null),
                new Case("audit action is required", t -> t.action = null),
                new Case("audit entityType is required", t -> t.entityType = null),
                new Case("audit entityId is required", t -> t.entityId = null));

        for (Case c : cases) {
            steps.clear();
            StatusTransitionExecutor executor = new StatusTransitionExecutor(abacGate, audit);
            StatusTransition t = fullTransition(executor);
            c.nuller().accept(t);

            assertThatThrownBy(t::execute)
                    .as("missing %s must fail fast", c.message())
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage(c.message());

            // Fail-fast: no slot ran, no audit row written.
            assertThat(steps)
                    .as("no side effect when %s is missing", c.message())
                    .isEmpty();
        }
        // The requireNonNull guards run before the executor ever touches its
        // collaborators, so the audit service is never invoked in any case.
        verifyNoInteractions(audit);
    }

    // (c) beforeMutate failure short-circuits mutate / save / audit --------

    @Test
    void beforeMutateExceptionPreventsMutateSaveAndAudit() {
        StatusTransitionExecutor executor = newExecutor();
        RuntimeException boom = new IllegalStateException("validation failed");

        StatusTransition t = executor.transition(ctx)
                .abac(() -> steps.add("abac"))
                .checkTransition(() -> steps.add("transitionCheck"))
                .beforeMutate(() -> {
                    throw boom;
                })
                .snapshot(() -> {
                    steps.add("snapshot");
                    return TextNode.valueOf("s");
                })
                .mutate(() -> steps.add("mutate"))
                .save(() -> steps.add("save"))
                .audit("STATUS_CHANGED", "Widget", entityId, projectId);

        assertThatThrownBy(t::execute).isSameAs(boom);

        // Nothing past the failed beforeMutate ran — no snapshot, mutate, save.
        assertThat(steps).containsExactly("abac", "transitionCheck");
        verify(audit, never()).record(any());
    }

    // (d) afterSave failure still leaves the mutation saved + audit recorded

    @Test
    void afterSaveExceptionLeavesMutationSavedAndAuditRecorded() {
        recordAuditStep();
        StatusTransitionExecutor executor = newExecutor();
        RuntimeException boom = new IllegalStateException("cascade failed");

        StatusTransition t = executor.transition(ctx)
                .abac(() -> steps.add("abac"))
                .checkTransition(() -> steps.add("transitionCheck"))
                .snapshot(() -> {
                    steps.add("snapshot");
                    return TextNode.valueOf("s");
                })
                .mutate(() -> steps.add("mutate"))
                .save(() -> steps.add("save"))
                .afterSave(() -> {
                    throw boom;
                })
                .audit("STATUS_CHANGED", "Widget", entityId, projectId);

        assertThatThrownBy(t::execute).isSameAs(boom);

        // afterSave runs LAST — the mutation + save + audit already happened and
        // are durable even though the post-audit continuation blew up (matching
        // SubmitEvaluationUseCase's try/catch-around-afterSave contract).
        assertThat(steps).containsExactly(
                "abac", "transitionCheck", "snapshot", "mutate", "save",
                "snapshot", "audit");
        verify(audit).record(any());
    }
}
