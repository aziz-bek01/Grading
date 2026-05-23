import type { LocalizedString, PageEnvelope } from '@/shared/types/common';

export type PositionStatus = 'ACTIVE' | 'ARCHIVED' | 'DRAFT';

export interface Position {
  id: string;
  project_id: string;
  department_id: string;
  code: string;
  title: LocalizedString;
  function?: string;
  category?: string;
  job_family?: string;
  job_level?: string;
  status: PositionStatus;
  updated_at: string;
}

export interface PositionCreatePayload {
  project_id: string;
  department_id: string;
  code: string;
  title: LocalizedString;
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
  status?: PositionStatus | null;
  jobFamily?: string | null;
  page?: number;
  size?: number;
}
