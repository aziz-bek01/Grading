import type { LocalizedString, PageEnvelope } from '@/shared/types/common';

export type PositionStatus = 'ACTIVE' | 'ARCHIVED' | 'DRAFT';

/**
 * Mirrors backend `PositionResponse` (Spring SNAKE_CASE Jackson strategy):
 * `{ id, project_id, department_id, code, title_i18n, function, category, job_family, job_level, status }`.
 */
export interface Position {
  id: string;
  project_id: string;
  department_id: string;
  code: string;
  title_i18n: LocalizedString;
  function?: string;
  category?: string;
  job_family?: string;
  job_level?: string;
  status: PositionStatus;
  /** Optional — backend response does not include updated_at; MSW keeps it for table sorting. */
  updated_at?: string;
}

export interface PositionCreatePayload {
  project_id: string;
  department_id: string;
  code: string;
  title_i18n: LocalizedString;
  function?: string;
  category?: string;
  job_family?: string;
  job_level?: string;
}

export type PositionUpdatePayload = Partial<PositionCreatePayload> & { status?: PositionStatus };

export type PositionList = PageEnvelope<Position>;

export interface PositionListParams {
  projectId: string;
  departmentId?: string | null;
  /**
   * T4 — when true, the BE expands `departmentId` to its whole subtree (closure
   * call) and returns descendants too. Default false ⇒ direct positions only.
   * No-op without a `departmentId`.
   */
  includeSubtree?: boolean;
  status?: PositionStatus | null;
  jobFamily?: string | null;
  page?: number;
  size?: number;
}
