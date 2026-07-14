package uz.hrlab.grading.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaParameter;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.dependencies.Slice;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.hrlab.grading.common.infrastructure.TenantAwareRepository;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * ArchUnit rules from master plan §21 + security-blueprint findings F-01 /
 * D-001 / D-003.
 *
 * <p>These rules are enforced at build time so a single PR cannot silently
 * introduce a BOLA, leak a JPA entity from a controller, or break the
 * api-package contract.
 */
@Tag("architecture")
class ArchitectureTest {

    private static final String BASE_PACKAGE = "uz.hrlab.grading";

    /** Loaded once — ignore Liquibase changesets and test sources. */
    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
            .importPackages(BASE_PACKAGE);

    /**
     * Rule 1 — tenant-scoped repositories must extend {@link TenantAwareRepository},
     * never {@link JpaRepository}. Control-plane repositories (Tenant, User,
     * UserTenantMembership, Role, Permission, SystemAuditLog) are exempted by
     * name because they are not tenant-scoped business data.
     */
    /** Control-plane repositories explicitly allowed to extend JpaRepository / Repository. */
    private static final Set<String> CONTROL_PLANE_REPOSITORIES = Set.of(
            "TenantRepository",
            "UserRepository",
            "UserTenantMembershipRepository",
            "UserRoleRepository",
            "RolePermissionRepository",
            "RoleRepository",
            "PermissionRepository",
            "SystemAuditLogRepository",
            "UserProjectAssignmentRepository",
            "UserDepartmentScopeRepository"
    );

    /**
     * Request DTOs allowed to carry an explicit {@code tenantId} field.
     *
     * <p>Normally tenant id MUST come from {@link uz.hrlab.grading.tenancy.application.TenantContext}
     * (security-blueprint §13). The user-management module is the documented
     * exception: HRLab Super Admin invites people INTO a specific client
     * tenant they don't currently have active, and {@link
     * uz.hrlab.grading.access.application.UserManagementPolicy} re-verifies
     * the value against the caller's membership set on every call.
     * Non-super-admins cannot reach into a tenant they don't belong to even
     * if they spoof the URL.
     */
    private static final Set<String> TENANT_ID_REQUEST_WHITELIST = Set.of(
            "InviteUserRequest",
            "AddMembershipRequest",
            "SetDepartmentScopesRequest",
            "SetProjectAssignmentsRequest"
    );

    @Test
    void tenantScopedRepositoriesMustExtendTenantAwareRepository() {
        ArchCondition<JavaClass> beTenantAwareOrControlPlane =
                new ArchCondition<>("extend TenantAwareRepository (or be a whitelisted " +
                        "control-plane repo: " + CONTROL_PLANE_REPOSITORIES + ")") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        if (CONTROL_PLANE_REPOSITORIES.contains(item.getSimpleName())) {
                            return; // whitelisted control-plane repo
                        }
                        if (item.getName().equals(TenantAwareRepository.class.getName())) {
                            return; // the base interface itself
                        }
                        boolean extendsTenantAware = item.getAllRawInterfaces().stream()
                                .anyMatch(c -> c.getName()
                                        .equals(TenantAwareRepository.class.getName()));
                        boolean extendsJpaRepository = item.getAllRawInterfaces().stream()
                                .anyMatch(c -> c.getName()
                                        .equals(JpaRepository.class.getName()));
                        if (extendsJpaRepository && !extendsTenantAware) {
                            events.add(SimpleConditionEvent.violated(item,
                                    item.getName() + " extends JpaRepository directly; " +
                                            "tenant-scoped repos must extend TenantAwareRepository " +
                                            "or be added to the control-plane whitelist"));
                        }
                    }
                };

        ArchRule rule = classes()
                .that().resideInAPackage("..infrastructure..")
                .and().haveSimpleNameEndingWith("Repository")
                .and().areInterfaces()
                .should(beTenantAwareOrControlPlane)
                .because("security-blueprint §5.2 / finding F-01 — bare JpaRepository " +
                        "exposes a no-tenant findById which is a BOLA hazard");
        rule.check(CLASSES);
    }

    /**
     * Rule 2 — controllers must not return JPA entities directly. Returning
     * an entity exposes the persistence model and skips DTO field-stripping
     * (master plan §21 rule 1).
     */
    @Test
    void controllersMustNotReturnJpaEntitiesDirectly() {
        ArchRule rule = classes()
                .that().areAnnotatedWith(RestController.class)
                .or().areAnnotatedWith(Controller.class)
                .should(new ArchCondition<>("not return classes whose name ends with 'JpaEntity'") {
                    @Override
                    public void check(com.tngtech.archunit.core.domain.JavaClass item, ConditionEvents events) {
                        item.getMethods().forEach(method -> {
                            String returnTypeName = method.getRawReturnType().getName();
                            if (returnTypeName.endsWith("JpaEntity")) {
                                events.add(SimpleConditionEvent.violated(method,
                                        method.getFullName() + " returns JPA entity " + returnTypeName));
                            }
                        });
                    }
                })
                .because("controllers must return DTOs, not JPA entities (master plan §21 rule 1)");
        rule.check(CLASSES);
    }

    /**
     * Rule 3 — domain layer must not import from infrastructure. Domain code
     * (entities, value objects, domain services) is the pure layer.
     */
    @Test
    void domainMustNotDependOnInfrastructure() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                .because("domain layer is pure — infrastructure depends on domain, not the inverse");
        rule.check(CLASSES);
    }

    /**
     * Rule 4 — controllers (classes annotated with {@code @RestController} or
     * {@code @Controller}) must live under an {@code .api} package.
     */
    @Test
    void controllersMustResideInApiPackage() {
        ArchRule rule = classes()
                .that().areAnnotatedWith(RestController.class)
                .or().areAnnotatedWith(Controller.class)
                .should().resideInAPackage("..api..")
                .because("all controllers must live under the module's .api package " +
                        "(architecture §8.2)");
        rule.check(CLASSES);
    }

    /**
     * Rule 5 — every Spring Data repository (anything extending the
     * Spring Data {@code Repository} marker interface) must have a name
     * ending in {@code Repository}.
     */
    @Test
    void allRepositoriesMustEndWithRepository() {
        ArchRule rule = classes()
                .that().areAssignableTo(org.springframework.data.repository.Repository.class)
                .and().areInterfaces()
                .and().resideInAPackage(BASE_PACKAGE + "..")
                .should().haveSimpleNameEndingWith("Repository")
                .because("Spring Data repository naming convention (master plan §21)");
        rule.check(CLASSES);
    }

    // ---------------------------------------------------------------------
    //  Rules 6-9 — D-201: forbid client-controlled tenantId on the API edge.
    //
    //  Master plan §21 + security-blueprint §13/§20.1 finding 2 mandate that
    //  tenantId is sourced ONLY from the authenticated JWT/TenantContext.
    //  These rules block a regression where a future developer accepts a
    //  tenantId from a @RequestParam, an @RequestBody DTO field, or a
    //  @PathVariable on a non-admin controller.
    // ---------------------------------------------------------------------

    /** Forbidden tenant-id identifier variants (case-insensitive on Spring annotation values). */
    private static final Set<String> FORBIDDEN_TENANT_NAMES =
            Set.of("tenantid", "tenant_id", "tenant-id");

    private static boolean isForbiddenTenantName(String name) {
        if (name == null) return false;
        return FORBIDDEN_TENANT_NAMES.contains(name.toLowerCase().trim());
    }

    /**
     * Rule 6 — no controller method may bind a {@code @RequestParam} named
     * {@code tenantId} / {@code tenant_id} / {@code tenant-id}. Frontends
     * MUST NOT pass a tenant identifier on the wire; it comes from the JWT.
     */
    @Test
    void controllersMustNotBindTenantIdAsRequestParam() {
        ArchRule rule = classes()
                .that().areAnnotatedWith(RestController.class)
                .or().areAnnotatedWith(Controller.class)
                .should(new ArchCondition<>("not declare a @RequestParam named " +
                        FORBIDDEN_TENANT_NAMES) {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        for (JavaMethod method : item.getMethods()) {
                            for (JavaParameter param : method.getParameters()) {
                                param.getAnnotations().stream()
                                        .filter(a -> a.getRawType().getName()
                                                .equals(RequestParam.class.getName()))
                                        .forEach(a -> {
                                            Object value = a.getProperties().get("value");
                                            Object name  = a.getProperties().get("name");
                                            if (isForbiddenTenantName(value == null ? null : value.toString())
                                                    || isForbiddenTenantName(name == null ? null : name.toString())) {
                                                events.add(SimpleConditionEvent.violated(method,
                                                        method.getFullName() + " accepts forbidden " +
                                                                "tenantId @RequestParam — must come from " +
                                                                "TenantContext (security-blueprint §13)"));
                                            }
                                        });
                            }
                        }
                    }
                })
                .because("security-blueprint §13 / §20.1 finding 2 — tenant id is server-derived from JWT");
        rule.check(CLASSES);
    }

    /**
     * Rule 7 — no {@code @RequestBody} DTO under {@code ..api..} may declare a
     * field named {@code tenantId} / {@code tenant_id} / {@code tenant-id}.
     * Mass-assignment of tenantId would let the caller spoof a cross-tenant
     * write.
     */
    @Test
    void requestDtosMustNotDeclareTenantIdField() {
        ArchRule rule = classes()
                .that().resideInAPackage("..api..")
                .and().haveSimpleNameEndingWith("Request")
                .should(new ArchCondition<>("not declare a field named " + FORBIDDEN_TENANT_NAMES
                        + " (whitelist: " + TENANT_ID_REQUEST_WHITELIST + ")") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        if (TENANT_ID_REQUEST_WHITELIST.contains(item.getSimpleName())) {
                            return; // documented user-management exception
                        }
                        for (JavaField field : item.getAllFields()) {
                            if (isForbiddenTenantName(field.getName())) {
                                events.add(SimpleConditionEvent.violated(field,
                                        item.getName() + "." + field.getName() +
                                                " is a forbidden tenantId field on a request DTO " +
                                                "(security-blueprint §13)"));
                            }
                        }
                    }
                })
                .because("security-blueprint §13 — DTOs must never carry tenantId; it comes from JWT");
        rule.check(CLASSES);
    }

    /**
     * Rule 8 — no controller method may bind a {@code @PathVariable} named
     * {@code tenantId} / {@code tenant_id} / {@code tenant-id} UNLESS the
     * controller lives under an {@code ..admin..} package (control-plane
     * tenant management is the documented exception, see
     * {@code GET /api/v1/admin/tenants/{tenantId}}).
     */
    // ---------------------------------------------------------------------
    //  Rule 9 — integration-review F2: Excel string cell writes must go
    //  through SafeCellWriter, never call Cell.setCellValue(String) directly
    //  from outside the integration.excel package.
    //
    //  Prevents formula-injection regressions when new exports ship in Phase 3:
    //  a developer adding `cell.setCellValue(userInput)` anywhere in the
    //  codebase (e.g. a report adapter, a future template) will fail the build
    //  unless the call lives next to SafeCellWriter.
    // ---------------------------------------------------------------------
    @Test
    void excelCellWritesMustGoThroughSafeCellWriter() {
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackage("..integration.excel..")
                .should(new ArchCondition<>("not call org.apache.poi.ss.usermodel.Cell" +
                        ".setCellValue(String) — must route through SafeCellWriter") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        for (JavaMethodCall call : item.getMethodCallsFromSelf()) {
                            if (isPoiSetCellValueString(call)) {
                                events.add(SimpleConditionEvent.violated(call,
                                        call.getOriginOwner().getName() + " calls " +
                                                call.getTargetOwner().getName() + ".setCellValue(String) " +
                                                "directly; use SafeCellWriter.writeString(...) " +
                                                "to neutralize formula-injection payloads " +
                                                "(integration-review F2)"));
                            }
                        }
                    }
                })
                .because("integration-blueprint §13.1 + integration-review F2 — every user-" +
                        "originated string written into an Excel cell must be sanitized by " +
                        "SafeCellWriter to prevent formula-injection (=, +, -, @, \\t, \\r, \\n).");
        rule.check(CLASSES);
    }

    /**
     * Matches any call to {@code setCellValue(String)} on any Apache POI Cell
     * type — covers {@code Cell}, {@code XSSFCell}, {@code SXSSFCell},
     * {@code HSSFCell}. We assert by target type prefix (org.apache.poi.) +
     * method name + single String parameter so we don't need to import POI
     * concrete classes in the test (keeps the test classpath light).
     */
    private static boolean isPoiSetCellValueString(JavaCall<?> call) {
        String targetOwner = call.getTargetOwner().getName();
        if (!targetOwner.startsWith("org.apache.poi.")) return false;
        if (!"setCellValue".equals(call.getTarget().getName())) return false;
        return call.getTarget().getRawParameterTypes().size() == 1
                && "java.lang.String".equals(
                        call.getTarget().getRawParameterTypes().get(0).getName());
    }

    // ---------------------------------------------------------------------
    //  Rule 10 (TI-Reg-08.a) — tenant-aware repositories must not declare a
    //  single-arg `findById(ID)` / `existsById(ID)` / `deleteById(ID)` that
    //  bypasses the tenant filter. The base interface intentionally omits
    //  these BOLA-prone signatures; a subtype re-declaring one would re-open
    //  the hole (security-blueprint §5.2, finding F-01, defect D-001).
    // ---------------------------------------------------------------------
    @Test
    void tenantAwareRepositoriesMustNotDeclareSingleArgFindById() {
        ArchRule rule = classes()
                .that().areInterfaces()
                .and().areAssignableTo(TenantAwareRepository.class)
                .and().doNotBelongToAnyOf(TenantAwareRepository.class)
                .should(new ArchCondition<>("not declare findById(ID) / existsById(ID) / " +
                        "deleteById(ID) — those bypass the tenant filter (BOLA hazard)") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        for (JavaMethod method : item.getMethods()) {
                            String n = method.getName();
                            int paramCount = method.getRawParameterTypes().size();
                            boolean isForbiddenName =
                                    "findById".equals(n)
                                    || "existsById".equals(n)
                                    || "deleteById".equals(n)
                                    || "getById".equals(n)
                                    || "getOne".equals(n)
                                    || "getReferenceById".equals(n);
                            if (isForbiddenName && paramCount == 1) {
                                events.add(SimpleConditionEvent.violated(method,
                                        item.getName() + "." + n + "(...) re-declares a single-arg "
                                                + "lookup on a tenant-scoped repository — use "
                                                + "findByIdAndTenantId(id, tenantId) instead "
                                                + "(security-blueprint §5.2 / F-01)"));
                            }
                        }
                    }
                })
                .because("security-blueprint §5.2 / F-01 — tenant-scoped repositories must " +
                        "NEVER expose a single-arg id lookup; the canonical path is " +
                        "findByIdAndTenantId(id, tenantId).");
        rule.check(CLASSES);
    }

    // ---------------------------------------------------------------------
    //  Rule 11 (TI-Reg-08.b) — query-string DTOs (any class under ..api..
    //  whose name ends with `Filter`, `Query`, `Criteria` or `Search`) must
    //  not declare a tenantId-shaped field either. The existing Rule 7
    //  covers `*Request` DTOs (mass-assignment via @RequestBody); this edge
    //  case closes the gap for paged-list query DTOs that bind via
    //  @ModelAttribute / @RequestParam projection
    //  (security-blueprint §13 — single source of truth = JWT).
    // ---------------------------------------------------------------------
    @Test
    void queryFilterDtosMustNotDeclareTenantIdField() {
        ArchRule rule = classes()
                .that().resideInAPackage("..api..")
                .and().haveSimpleNameEndingWith("Filter")
                .or().resideInAPackage("..api..")
                .and().haveSimpleNameEndingWith("Query")
                .or().resideInAPackage("..api..")
                .and().haveSimpleNameEndingWith("Criteria")
                .or().resideInAPackage("..api..")
                .and().haveSimpleNameEndingWith("Search")
                .should(new ArchCondition<>("not declare a field named " + FORBIDDEN_TENANT_NAMES) {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        for (JavaField field : item.getAllFields()) {
                            if (isForbiddenTenantName(field.getName())) {
                                events.add(SimpleConditionEvent.violated(field,
                                        item.getName() + "." + field.getName() +
                                                " is a forbidden tenantId field on a query/filter DTO "
                                                + "(security-blueprint §13)"));
                            }
                        }
                    }
                })
                .because("security-blueprint §13 — list/filter DTOs MUST source tenantId from " +
                        "the authenticated TenantContext, never from a client-supplied query.")
                // No Filter/Query/Criteria/Search DTOs exist today — the rule is a guard
                // for future modules. Allow empty match so the build is not red until
                // the first such DTO ships.
                .allowEmptyShould(true);
        rule.check(CLASSES);
    }

    // ---------------------------------------------------------------------
    //  Rule 12 (MVP2 multi-evaluator, BE-17) — PanelAveragingService is a PURE
    //  domain service: it must NOT depend on any infrastructure/repository,
    //  Spring data, clock, or IO. The whole point is golden-file reproducibility
    //  (same inputs -> byte-identical output), exactly like EvaluationScoringEngine.
    // ---------------------------------------------------------------------
    @Test
    void panelAveragingServiceMustBePure() {
        ArchRule rule = noClasses()
                .that().haveSimpleName("PanelAveragingService")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..infrastructure..",
                        "org.springframework.data..",
                        "java.time..",
                        "java.io..",
                        "java.sql..")
                .because("PanelAveragingService is the single pure averaging function "
                        + "(MVP2 BE-3) — no IO, no clock, no repositories — so it is "
                        + "golden-file reproducible like EvaluationScoringEngine.");
        rule.check(CLASSES);
    }

    // ---------------------------------------------------------------------
    //  Rule 13 (BE-4, 2026-06-12 — approval tenant-scoping defense-in-depth)
    //  Every query method declared on an approval repository MUST be tenant-
    //  scoped: either its derived name contains `TenantId`, or it accepts a
    //  parameter named `tenantId` (the @Query path). This pins the invariant
    //  "no query may read approval_* without a tenant_id predicate" so a future
    //  developer cannot add a BOLA-prone finder on approval_requests /
    //  approval_steps / approval_decisions. Inherited base methods
    //  (findByIdAndTenantId, findAllByTenantId, existsByIdAndTenantId, save,
    //  count) are not re-declared on the subtypes, so they are out of scope.
    // ---------------------------------------------------------------------
    @Test
    void approvalRepositoryQueryMethodsMustBeTenantScoped() {
        ArchRule rule = classes()
                .that().areInterfaces()
                .and().resideInAPackage("..approval.infrastructure..")
                .and().haveSimpleNameEndingWith("Repository")
                .should(new ArchCondition<>("declare only tenant-scoped query methods "
                        + "(name contains 'TenantId' OR a 'tenantId' parameter)") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        for (JavaMethod method : item.getMethods()) {
                            // Non-read mutators / inherited overloads are not the concern;
                            // we only audit finder/search/count style reads.
                            if (!isReadMethod(method.getName())) {
                                continue;
                            }
                            boolean nameScoped = method.getName().contains("TenantId");
                            boolean paramScoped = method.getParameters().stream()
                                    .anyMatch(p -> hasTenantIdParamName(p));
                            if (!nameScoped && !paramScoped) {
                                events.add(SimpleConditionEvent.violated(method,
                                        item.getName() + "." + method.getName()
                                                + "(...) reads approval data without a tenant_id "
                                                + "predicate — every approval query MUST be tenant-"
                                                + "scoped (BE-4 / security-blueprint §5.2)."));
                            }
                        }
                    }
                })
                .because("BE-4 (2026-06-12) — the approvals cross-tenant inbox leak. No query "
                        + "may read approval_requests/approval_steps/approval_decisions without "
                        + "an explicit tenant_id predicate, even though FORCE RLS is a second line "
                        + "of defense.");
        rule.check(CLASSES);
    }

    private static boolean isReadMethod(String name) {
        return name.startsWith("find")
                || name.startsWith("get")
                || name.startsWith("search")
                || name.startsWith("count")
                || name.startsWith("exists");
    }

    /**
     * True when an {@code @Query} method parameter is the tenant id, identified
     * by a {@code @Param("tenantId")} binding (case-insensitive). Derived
     * (non-{@code @Query}) finders are covered by the name-contains-'TenantId'
     * check instead, so this only needs to recognise the explicit JPQL path.
     */
    private static boolean hasTenantIdParamName(JavaParameter param) {
        return param.getAnnotations().stream()
                .filter(a -> a.getRawType().getName()
                        .equals(org.springframework.data.repository.query.Param.class.getName()))
                .anyMatch(a -> {
                    Object value = a.getProperties().get("value");
                    return value != null && "tenantId".equalsIgnoreCase(value.toString().trim());
                });
    }

    // ---------------------------------------------------------------------
    //  Rule 14 (arch-hardening 2026-07-13) — package-cycle regression guard.
    //  The evaluation ↔ methodology cycle was broken by routing the two
    //  MethodologyQueries reverse-edge reads (findMyMethodologiesInProject,
    //  countInProgressEvaluations) through the module-owned outbound port
    //  MethodologyReferencePort (impl EvaluationMethodologyReferenceAdapter in
    //  the evaluation module), so the static arrow now flows evaluation →
    //  methodology only.
    //
    //  The integration ↔ reporting cycle was subsequently broken (arch-hardening
    //  2026-07-13, follow-up) by inverting the three upward integration →
    //  reporting edges: (1) the shared read SPI ReportDataPort + its row records
    //  + EvaluationReportFilter were relocated to integration.reportdata and the
    //  DocxBuilder/PdfBuilder document primitives to integration.docs (siblings
    //  of integration.excel.ExcelWriter), so ExportContentGenerator no longer
    //  reaches up into reporting; (2)/(3) the WorkerReQueuer / WorkerRetryScanner
    //  now depend on the integration-owned ports ReportGenerationPort /
    //  ReportRetryScanPort, implemented by reporting-side adapters. The static
    //  arrow now flows reporting → integration only.
    //
    //  The access ↔ project cycle was subsequently broken (arch-hardening
    //  2026-07-14) by inverting the single upward access → project edge: the
    //  project-existence check in SetUserProjectAssignmentsUseCase now flows
    //  through the access-owned ProjectReferencePort (impl
    //  project.infrastructure.AccessProjectReferenceAdapter, delegating verbatim
    //  to the tenant-aware ProjectRepository.existsByIdAndTenantId), so the static
    //  arrow flows project → access only — access is the low-level authorization
    //  kernel every other module depends on.
    //
    //  SCOPE: the cleaned slice set is {evaluation, methodology, integration,
    //  reporting} — proven a DAG (reporting → {integration, evaluation} →
    //  methodology, evaluation → methodology). A whole-app beFreeOfCycles() is
    //  still RED — the modular monolith carries other pre-existing package cycles.
    //  NOTE: access ↔ project is now cycle-free, but access CANNOT yet join this
    //  guarded set because a SEPARATE pre-existing cycle access ↔ integration is
    //  still open (access → integration via IdentityProvisioningPort /
    //  ZitadelIdpProperties in the IdP-provisioning use cases; integration →
    //  access via AbacGate / PermissionCodes) — and integration is already in the
    //  set, so adding access would surface it and turn this rule RED. Add
    //  {access, project} here once that access → integration edge is inverted too.
    //  Widen this set as each further pair is genuinely cleaned; do NOT flip it to
    //  the whole app until every pair is.
    // ---------------------------------------------------------------------

    /** Slices whose mutual dependencies are now proven cycle-free and must stay so. */
    private static final DescribedPredicate<Slice> CYCLE_FREE_MODULE_PAIR =
            new DescribedPredicate<>(
                    "in the cycle-free module set [evaluation, methodology, integration, reporting]") {
                @Override
                public boolean test(Slice slice) {
                    String module = slice.getNamePart(1);
                    return "evaluation".equals(module) || "methodology".equals(module)
                            || "integration".equals(module) || "reporting".equals(module);
                }
            };

    @Test
    void cleanedModulesMustBeFreeOfCycles() {
        ArchRule rule = slices()
                .matching(BASE_PACKAGE + ".(*)..")
                .that(CYCLE_FREE_MODULE_PAIR)
                .should().beFreeOfCycles()
                .because("arch-hardening 2026-07-13 — the evaluation ↔ methodology cycle was "
                        + "inverted via MethodologyReferencePort, and the integration ↔ reporting "
                        + "cycle via the integration-owned ReportDataPort/ReportGenerationPort/"
                        + "ReportRetryScanPort ports; no new dependency may re-introduce a cycle "
                        + "among {evaluation, methodology, integration, reporting}.");
        rule.check(CLASSES);
    }

    @Test
    void controllersMustNotBindTenantIdAsPathVariableOutsideAdmin() {
        ArchRule rule = classes()
                .that().areAnnotatedWith(RestController.class)
                .or().areAnnotatedWith(Controller.class)
                .and().resideOutsideOfPackage("..admin..")
                .should(new ArchCondition<>("not declare @PathVariable named " +
                        FORBIDDEN_TENANT_NAMES + " outside ..admin..") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        for (JavaMethod method : item.getMethods()) {
                            for (JavaParameter param : method.getParameters()) {
                                param.getAnnotations().stream()
                                        .filter(a -> a.getRawType().getName()
                                                .equals(PathVariable.class.getName()))
                                        .forEach(a -> {
                                            Object value = a.getProperties().get("value");
                                            Object name  = a.getProperties().get("name");
                                            if (isForbiddenTenantName(value == null ? null : value.toString())
                                                    || isForbiddenTenantName(name == null ? null : name.toString())) {
                                                events.add(SimpleConditionEvent.violated(method,
                                                        method.getFullName() + " accepts forbidden " +
                                                                "tenantId @PathVariable on a non-admin " +
                                                                "controller (security-blueprint §13)"));
                                            }
                                        });
                            }
                        }
                    }
                })
                .because("security-blueprint §13 — tenant id never appears in the path of business APIs; " +
                        "only admin/control-plane APIs may surface it");
        rule.check(CLASSES);
    }
}
