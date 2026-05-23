package uz.hrlab.grading.organization.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Tree node returned by {@link DepartmentTreeBuilder} — domain-only,
 * no JPA references.
 */
public final class DepartmentTreeNode {

    private final Department department;
    private final List<DepartmentTreeNode> children = new ArrayList<>();

    public DepartmentTreeNode(Department department) {
        this.department = department;
    }

    public Department department() { return department; }
    public List<DepartmentTreeNode> children() { return children; }
    public void addChild(DepartmentTreeNode node) { children.add(node); }
}
