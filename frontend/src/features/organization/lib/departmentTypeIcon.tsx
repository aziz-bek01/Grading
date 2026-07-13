/**
 * Department-type -> icon map, shared by every department-tree renderer
 * (`DepartmentTree`, `DepartmentSingleSelectTree`, and the users-access
 * feature's `DepartmentScopeTree`). Previously copy-pasted byte-for-byte in
 * all three files (DUP sweep) — single source now, alongside the other tree
 * building block (`./tree.ts`) that those same three components already
 * shared.
 */
import { Building2, Network as Sitemap, Layers, Users } from 'lucide-react';
import type { DepartmentType } from '../types/organizationTypes';

export const departmentTypeIcon: Record<DepartmentType, React.ReactNode> = {
  BRANCH: <Building2 size={14} aria-hidden />,
  DEPARTMENT: <Sitemap size={14} aria-hidden />,
  DIVISION: <Layers size={14} aria-hidden />,
  UNIT: <Users size={14} aria-hidden />,
};
