package uz.hrlab.grading.organization.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Domain service: builds an in-memory department tree from a flat list.
 *
 * <p>Pure (no JPA / Spring). Single-pass O(n) with a id→node lookup map.
 */
public final class DepartmentTreeBuilder {

    public List<DepartmentTreeNode> build(List<Department> flat) {
        Map<UUID, DepartmentTreeNode> byId = new HashMap<>();
        for (Department d : flat) {
            byId.put(d.id(), new DepartmentTreeNode(d));
        }
        List<DepartmentTreeNode> roots = new ArrayList<>();
        for (Department d : flat) {
            DepartmentTreeNode node = byId.get(d.id());
            if (d.parentId() == null) {
                roots.add(node);
                continue;
            }
            DepartmentTreeNode parent = byId.get(d.parentId());
            if (parent == null) {
                // Orphan: parent not in result set (different project or archived).
                // Treat as a root to avoid silently dropping the node.
                roots.add(node);
            } else {
                parent.addChild(node);
            }
        }
        return roots;
    }
}
