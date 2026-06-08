package uz.hrlab.grading.common.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.BaseDomainException;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.common.exception.ResourceNotFoundException;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.integration.idp.application.IdentityProvisioningException;
import uz.hrlab.grading.approval.domain.ApprovalTransitionRejectedException;
import uz.hrlab.grading.jobanalysis.domain.QuestionnaireTransitionRejectedException;
import uz.hrlab.grading.jobprofile.domain.JobProfileTransitionRejectedException;
import uz.hrlab.grading.methodology.domain.MethodologyVersionTransitionRejectedException;
import uz.hrlab.grading.project.application.ProjectLockedException;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.List;
import java.util.UUID;

/**
 * Single source of truth for API error envelopes. All responses follow the
 * design-foundation §21.6 contract:
 * <pre>
 * { "code": "...", "message": "...", "correlation_id": "..." }
 * </pre>
 *
 * <p>Cross-tenant probing returns {@code 404 NOT_FOUND} — never 403 with
 * details about why — and is logged as a security event AND recorded as a
 * hash-chained {@link AuditEvent} (finding F-05, defect D-002).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final Logger securityLog = LoggerFactory.getLogger("security.audit");

    private final AuditService auditService;

    public GlobalExceptionHandler(AuditService auditService) {
        this.auditService = auditService;
    }

    @ExceptionHandler(TenantAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleTenantAccessDenied(TenantAccessDeniedException ex,
                                                                  HttpServletRequest req) {
        String correlationId = correlationId();
        // SLF4J log line for operational visibility (existing behaviour).
        securityLog.warn("CROSS_TENANT_ACCESS_ATTEMPT path={} cid={}", req.getRequestURI(), correlationId);
        // Tamper-evident audit row — never relies on log scraping.
        recordCrossTenantAttempt(req, correlationId);
        return build(HttpStatus.NOT_FOUND, ex.getCode(), ex.getMessage());
    }

    private void recordCrossTenantAttempt(HttpServletRequest req, String correlationId) {
        try {
            TenantContext ctx = TenantContextHolder.get();
            UUID tenantId = ctx == null ? null : ctx.tenantId();
            UUID actorUserId = ctx == null ? null : ctx.userId();
            // Redacted detail: path + method only, never query string or body.
            String entityType = "HTTP_REQUEST";
            UUID entityId = null; // we do not parse path params here to avoid leaking another tenant's id
            String redactedReason = req.getMethod() + " " + req.getRequestURI();
            auditService.record(AuditEvent.builder()
                    .tenantId(tenantId)
                    .actorUserId(actorUserId)
                    .action(AuditAction.CROSS_TENANT_OR_PROJECT_ACCESS_ATTEMPT)
                    .entityType(entityType)
                    .entityId(entityId)
                    .reason(redactedReason)
                    .ipAddress(clientIp(req))
                    .userAgent(req.getHeader("User-Agent"))
                    .correlationId(correlationId)
                    .traceId(MDC.get("traceId"))
                    .build());
        } catch (Exception persistFailure) {
            // Audit insert failure must never block the 404 response, but it
            // is itself a security-relevant event — surface it.
            log.error("Failed to persist CROSS_TENANT_ACCESS_ATTEMPT audit row cid={}",
                    correlationId, persistFailure);
        }
    }

    private static String clientIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
        }
        return req.getRemoteAddr();
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(PermissionDeniedException.class)
    public ResponseEntity<ErrorResponse> handlePermissionDenied(PermissionDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(ValidationException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(ProjectLockedException.class)
    public ResponseEntity<ErrorResponse> handleProjectLocked(ProjectLockedException ex) {
        return build(HttpStatus.CONFLICT, ex.getCode(), ex.getMessage());
    }

    /**
     * IdP-provisioning gap (decision doc 08): the grading request was valid but
     * the upstream Identity Provider (ZITADEL) failed, so the login account
     * could not be created/linked and the invite was rolled back. Map to
     * {@code 502 BAD_GATEWAY} — it is an upstream dependency failure, not a
     * client error and not an internal bug. The message is already sanitised by
     * the exception (no password/token).
     */
    @ExceptionHandler(IdentityProvisioningException.class)
    public ResponseEntity<ErrorResponse> handleIdpProvisioning(IdentityProvisioningException ex) {
        log.warn("IdP provisioning failed cid={} code={}: {}", correlationId(),
                ex.getCode(), ex.getMessage());
        return build(HttpStatus.BAD_GATEWAY, ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(JobProfileTransitionRejectedException.class)
    public ResponseEntity<ErrorResponse> handleJobProfileTransition(
            JobProfileTransitionRejectedException ex) {
        return build(HttpStatus.CONFLICT, ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(QuestionnaireTransitionRejectedException.class)
    public ResponseEntity<ErrorResponse> handleQuestionnaireTransition(
            QuestionnaireTransitionRejectedException ex) {
        return build(HttpStatus.CONFLICT, ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(MethodologyVersionTransitionRejectedException.class)
    public ResponseEntity<ErrorResponse> handleMethodologyVersionTransition(
            MethodologyVersionTransitionRejectedException ex) {
        return build(HttpStatus.CONFLICT, ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(ApprovalTransitionRejectedException.class)
    public ResponseEntity<ErrorResponse> handleApprovalTransition(
            ApprovalTransitionRejectedException ex) {
        return build(HttpStatus.CONFLICT, ex.getCode(), ex.getMessage());
    }

    /**
     * F-307 / F-408 — unique-index and FK violations (e.g. concurrent reorder
     * collides on {@code uq_factors_version_sort_order}) become 500 by
     * default. They are not server bugs — they are concurrent modifications.
     * Map to 409 CONFLICT with a generic {@code CONSTRAINT_VIOLATION} code.
     * The constraint name and SQL detail are logged internally but NEVER
     * leaked to the client (security: avoid schema disclosure).
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex) {
        log.warn("DataIntegrityViolationException cid={}: {}", correlationId(),
                ex.getMostSpecificCause() != null
                        ? ex.getMostSpecificCause().getMessage()
                        : ex.getMessage());
        return build(HttpStatus.CONFLICT, "CONSTRAINT_VIOLATION",
                "Concurrent modification or constraint violation. Please retry.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBeanValidation(MethodArgumentNotValidException ex) {
        List<ErrorResponse.FieldError> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldError)
                .toList();
        ErrorResponse body = ErrorResponse.withFieldErrors(
                "VALIDATION_FAILED", "Request validation failed",
                correlationId(), traceId(), fields);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed");
    }

    /**
     * Path/query parameter type-conversion failures (e.g. malformed UUID in a
     * {@code @PathVariable UUID id}). Return 400 not 500 — these are
     * client-supplied bad inputs, not server errors. Defect D-203.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "Invalid value for parameter '" + ex.getName() + "'");
    }

    /**
     * Malformed request body (broken JSON, wrong content type). Return 400 not 500.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Malformed request body");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleSpringAccessDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "PERMISSION_DENIED", "Action not permitted");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuth(AuthenticationException ex) {
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication required");
    }

    @ExceptionHandler(BaseDomainException.class)
    public ResponseEntity<ErrorResponse> handleDomain(BaseDomainException ex) {
        // Domain-level fall-through: treat as 400 unless subclass already mapped.
        return build(HttpStatus.BAD_REQUEST, ex.getCode(), ex.getMessage());
    }

    /**
     * No controller matched the request path — Spring MVC fell through to static
     * resource resolution and found nothing. Return 404 NOT_FOUND instead of the
     * catch-all 500 so unimplemented/mistyped routes are reported honestly and
     * are not mistaken for server crashes (e.g. the not-yet-implemented
     * GET /api/v1/methodology-templates). The detail is intentionally generic to
     * avoid disclosing which internal paths do or do not exist.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found");
    }

    /**
     * The path matched a controller but the HTTP method is wrong (e.g. GET on a
     * POST-only {@code /users/{id}/memberships}). Without this handler the
     * request falls through to the catch-all below and is reported as a 500,
     * masking a client mistake as a server crash. Return 405 METHOD_NOT_ALLOWED
     * honestly. The detail is generic to avoid disclosing the allowed verbs.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                "HTTP method not supported for this resource");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        // Never leak stack traces. Log full detail server-side; return generic envelope.
        log.error("Unhandled exception cid={}", correlationId(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred");
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(
                ErrorResponse.of(code, message, correlationId(), traceId()));
    }

    private ErrorResponse.FieldError toFieldError(FieldError fe) {
        return new ErrorResponse.FieldError(fe.getField(), fe.getCode(), fe.getDefaultMessage());
    }

    private String correlationId() {
        String mdc = MDC.get("correlationId");
        return mdc != null ? mdc : UUID.randomUUID().toString();
    }

    private String traceId() {
        return MDC.get("traceId");
    }
}
