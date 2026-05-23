package uz.hrlab.grading.organization.api;

import uz.hrlab.grading.organization.domain.DepartmentTreeNode;

import java.util.List;

public record DepartmentTreeNodeResponse(
        DepartmentResponse department,
        List<DepartmentTreeNodeResponse> children
) {
    public static DepartmentTreeNodeResponse from(DepartmentTreeNode node) {
        return new DepartmentTreeNodeResponse(
                DepartmentResponse.from(node.department()),
                node.children().stream().map(DepartmentTreeNodeResponse::from).toList());
    }
}
