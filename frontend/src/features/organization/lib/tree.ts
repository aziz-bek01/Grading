import type { Department, DepartmentTreeNode } from '../types/organizationTypes';

/** Build adjacency tree from flat list. Detached nodes (orphan parent_id) become roots. */
export function buildDepartmentTree(items: Department[]): DepartmentTreeNode[] {
  const byId = new Map<string, DepartmentTreeNode>();
  items.forEach((d) => byId.set(d.id, { ...d, children: [] }));
  const roots: DepartmentTreeNode[] = [];
  byId.forEach((node) => {
    if (node.parent_id && byId.has(node.parent_id)) {
      byId.get(node.parent_id)!.children.push(node);
    } else {
      roots.push(node);
    }
  });
  const sortFn = (a: DepartmentTreeNode, b: DepartmentTreeNode) => a.code.localeCompare(b.code);
  const sortAll = (nodes: DepartmentTreeNode[]) => {
    nodes.sort(sortFn);
    nodes.forEach((n) => sortAll(n.children));
  };
  sortAll(roots);
  return roots;
}

/** Returns the ids of `node` plus every descendant — used to forbid parent self-cycles. */
export function collectDescendantIds(items: Department[], rootId: string): Set<string> {
  const childrenByParent = new Map<string, string[]>();
  items.forEach((d) => {
    if (!d.parent_id) return;
    const arr = childrenByParent.get(d.parent_id) ?? [];
    arr.push(d.id);
    childrenByParent.set(d.parent_id, arr);
  });
  const out = new Set<string>([rootId]);
  const walk = (id: string) => {
    const kids = childrenByParent.get(id);
    if (!kids) return;
    kids.forEach((k) => {
      if (!out.has(k)) {
        out.add(k);
        walk(k);
      }
    });
  };
  walk(rootId);
  return out;
}
