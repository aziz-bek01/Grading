package uz.hrlab.grading.organization.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DepartmentTreeBuilderTest {

    private final DepartmentTreeBuilder builder = new DepartmentTreeBuilder();

    @Test
    void buildsHierarchy() {
        UUID tenant = UUID.randomUUID();
        UUID project = UUID.randomUUID();
        Department root = dept(tenant, project, null, "ROOT");
        Department child1 = dept(tenant, project, root.id(), "CHILD1");
        Department child2 = dept(tenant, project, root.id(), "CHILD2");
        Department grand = dept(tenant, project, child1.id(), "GRAND");

        List<DepartmentTreeNode> roots = builder.build(List.of(root, child1, child2, grand));

        assertThat(roots).hasSize(1);
        assertThat(roots.get(0).department().code()).isEqualTo("ROOT");
        assertThat(roots.get(0).children()).hasSize(2);
        DepartmentTreeNode c1 = roots.get(0).children().stream()
                .filter(c -> c.department().code().equals("CHILD1"))
                .findFirst().orElseThrow();
        assertThat(c1.children()).hasSize(1);
        assertThat(c1.children().get(0).department().code()).isEqualTo("GRAND");
    }

    @Test
    void orphanedNodesPromotedToRoots() {
        UUID tenant = UUID.randomUUID();
        UUID project = UUID.randomUUID();
        // parent not in flat list — orphan
        UUID missingParent = UUID.randomUUID();
        Department orphan = dept(tenant, project, missingParent, "ORPHAN");
        List<DepartmentTreeNode> roots = builder.build(List.of(orphan));
        assertThat(roots).hasSize(1);
        assertThat(roots.get(0).department().code()).isEqualTo("ORPHAN");
    }

    private Department dept(UUID t, UUID p, UUID parent, String code) {
        return new Department(UUID.randomUUID(), t, p, parent, code,
                Map.of("ru-RU", code), DepartmentType.DEPARTMENT, DepartmentStatus.ACTIVE);
    }
}
