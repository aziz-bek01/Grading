package uz.hrlab.grading.organization.domain;

import org.junit.jupiter.api.Test;
import uz.hrlab.grading.common.exception.ValidationException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DepartmentValidationPolicyTest {

    private final DepartmentValidationPolicy policy = new DepartmentValidationPolicy();

    @Test
    void rejectsCrossTenantParent() {
        UUID tenant = UUID.randomUUID();
        UUID project = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        Department crossTenantParent = new Department(parentId, UUID.randomUUID(), project,
                null, "ROOT", Map.of("ru-RU", "Root"), DepartmentType.BRANCH,
                DepartmentStatus.ACTIVE);
        assertThatThrownBy(() -> policy.validateParentForCreate(tenant, project, parentId,
                id -> crossTenantParent))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void rejectsSelfParent() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> policy.validateParentForUpdate(
                id, UUID.randomUUID(), UUID.randomUUID(), id,
                pid -> null,
                root -> List.of()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("cannot be its own parent");
    }

    @Test
    void rejectsCycle() {
        UUID tenant = UUID.randomUUID();
        UUID project = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        Department child = new Department(childId, tenant, project, rootId, "CHILD",
                Map.of("ru-RU", "C"), DepartmentType.DEPARTMENT, DepartmentStatus.ACTIVE);
        // Trying to move rootId under childId — childId is a descendant of rootId.
        assertThatThrownBy(() -> policy.validateParentForUpdate(
                rootId, tenant, project, childId,
                pid -> child,
                root -> List.of(child)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Cannot move department under its own descendant");
    }
}
