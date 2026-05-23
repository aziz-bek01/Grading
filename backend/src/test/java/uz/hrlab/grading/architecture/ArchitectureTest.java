package uz.hrlab.grading.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaParameter;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
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
            "RoleRepository",
            "PermissionRepository",
            "SystemAuditLogRepository"
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
                .should(new ArchCondition<>("not declare a field named " + FORBIDDEN_TENANT_NAMES) {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
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
