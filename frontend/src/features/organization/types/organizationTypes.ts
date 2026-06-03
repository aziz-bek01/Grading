import type { LocalizedString } from '@/shared/types/common';

export type DepartmentType = 'BRANCH' | 'DEPARTMENT' | 'DIVISION' | 'UNIT';
export type DepartmentStatus = 'ACTIVE' | 'ARCHIVED' | 'DRAFT';

/**
 * Mirrors backend `DepartmentResponse` (Spring SNAKE_CASE Jackson strategy):
 * `{ id, project_id, parent_id, code, name_i18n, type, status }`.
 */
export interface Department {
  id: string;
  project_id: string;
  parent_id: string | null;
  code: string;
  name_i18n: LocalizedString;
  type: DepartmentType;
  status: DepartmentStatus;
  /** Optional — backend response does not include updated_at; MSW keeps it for table sorting. */
  updated_at?: string;
}

export interface DepartmentCreatePayload {
  project_id: string;
  parent_id: string | null;
  code: string;
  name_i18n: LocalizedString;
  type: DepartmentType;
}

export type DepartmentUpdatePayload = Partial<DepartmentCreatePayload>;

export interface DepartmentTreeNode extends Department {
  children: DepartmentTreeNode[];
}
