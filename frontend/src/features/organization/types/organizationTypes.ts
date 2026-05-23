import type { LocalizedString } from '@/shared/types/common';

export type DepartmentType = 'BRANCH' | 'DEPARTMENT' | 'DIVISION' | 'UNIT';
export type DepartmentStatus = 'ACTIVE' | 'ARCHIVED' | 'DRAFT';

export interface Department {
  id: string;
  project_id: string;
  parent_id: string | null;
  code: string;
  name: LocalizedString;
  type: DepartmentType;
  status: DepartmentStatus;
  updated_at: string;
}

export interface DepartmentCreatePayload {
  project_id: string;
  parent_id: string | null;
  code: string;
  name: LocalizedString;
  type: DepartmentType;
}

export type DepartmentUpdatePayload = Partial<DepartmentCreatePayload>;

export interface DepartmentTreeNode extends Department {
  children: DepartmentTreeNode[];
}
