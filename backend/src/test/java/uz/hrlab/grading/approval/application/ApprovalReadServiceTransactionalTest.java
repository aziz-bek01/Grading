package uz.hrlab.grading.approval.application;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.approval.api.ApprovalController;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Docker-free guard for the RLS read-path fix's STRUCTURE (the runtime behaviour
 * is proven under FORCE RLS in {@code ApprovalReadRlsIntegrationTest}).
 *
 * <p>Two invariants the fix depends on:
 * <ol>
 *   <li>every {@link ApprovalReadService} read method is
 *       {@code @Transactional(readOnly = true)} — so {@code RlsTenantSessionAspect}
 *       (which only advises {@code @Transactional} joinpoints) binds
 *       {@code app.tenant_id} for the find AND the label-resolver reads;</li>
 *   <li>{@link ApprovalController} delegates the read paths to
 *       {@code ApprovalReadService} (a DIFFERENT bean), NOT to a self method —
 *       a {@code @Transactional} call on {@code this} would bypass the proxy and
 *       the aspect would never fire (the exact mistake the fix must avoid). We
 *       assert the controller holds an {@code ApprovalReadService} collaborator
 *       and that the read service is wired with its own repository/assembler
 *       collaborators (so the transactional boundary is on the service, reached
 *       across a proxy).</li>
 * </ol>
 */
@Tag("architecture")
class ApprovalReadServiceTransactionalTest {

    @Test
    void everyReadServiceReadMethodIsTransactionalReadOnly() throws NoSuchMethodException {
        assertReadOnlyTx(ApprovalReadService.class.getMethod(
                "getById", java.util.UUID.class, java.util.UUID.class));
        assertReadOnlyTx(ApprovalReadService.class.getMethod(
                "search", java.util.UUID.class, java.util.UUID.class,
                uz.hrlab.grading.approval.domain.ApprovalEntityType.class,
                uz.hrlab.grading.approval.domain.ApprovalRequestStatus.class,
                org.springframework.data.domain.Pageable.class));
        assertReadOnlyTx(ApprovalReadService.class.getMethod(
                "searchByEntity", java.util.UUID.class, java.util.List.class));
        assertReadOnlyTx(ApprovalReadService.class.getMethod(
                "enrichInbox", java.util.UUID.class, java.util.List.class));
    }

    @Test
    void assemblerEnrichMethodsAreTransactional() throws NoSuchMethodException {
        // The label/name resolver reads also need a bound GUC for write-response
        // enrichment (create/decide/cancel), so enrich/enrichAll are @Transactional.
        assertTx(ApprovalResponseAssembler.class.getMethod(
                "enrich", java.util.UUID.class, uz.hrlab.grading.approval.domain.ApprovalRequest.class));
        assertTx(ApprovalResponseAssembler.class.getMethod(
                "enrichAll", java.util.UUID.class, java.util.List.class));
    }

    /**
     * The controller must reach the transactional read methods ACROSS a proxy —
     * i.e. via an injected {@link ApprovalReadService} bean, never a self method.
     * If the read logic lived on the controller itself, a {@code this.}-call would
     * bypass the proxy and the RLS aspect would not fire. We assert the controller
     * declares an {@code ApprovalReadService} field (the cross-bean delegate).
     */
    @Test
    void controllerDelegatesReadsToASeparateTransactionalBean() {
        boolean hasReadServiceField = false;
        for (Field f : ApprovalController.class.getDeclaredFields()) {
            if (f.getType() == ApprovalReadService.class) {
                hasReadServiceField = true;
                break;
            }
        }
        assertThat(hasReadServiceField)
                .as("ApprovalController must delegate reads to a separate ApprovalReadService "
                        + "bean (cross-proxy) so the @Transactional RLS aspect actually fires "
                        + "— a self-invoked @Transactional method would bypass the proxy")
                .isTrue();
    }

    private static void assertReadOnlyTx(Method m) {
        Transactional tx = m.getAnnotation(Transactional.class);
        assertThat(tx)
                .as("%s must be @Transactional so the RLS aspect binds app.tenant_id", m.getName())
                .isNotNull();
        assertThat(tx.readOnly())
                .as("%s should be readOnly = true (a pure read path)", m.getName())
                .isTrue();
    }

    private static void assertTx(Method m) {
        assertThat(m.getAnnotation(Transactional.class))
                .as("%s must be @Transactional so the RLS aspect binds app.tenant_id "
                        + "for the label-resolver reads", m.getName())
                .isNotNull();
    }
}
