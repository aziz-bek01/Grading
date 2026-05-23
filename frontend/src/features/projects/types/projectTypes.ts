import type { LocalizedString, PageEnvelope } from '@/shared/types/common';

export type ProjectStatus = 'DRAFT' | 'ACTIVE' | 'IN_REVIEW' | 'APPROVED' | 'LOCKED' | 'ARCHIVED';

export interface Project {
  id: string;
  tenant_id: string;
  code: string;
  name: LocalizedString;
  description?: string;
  status: ProjectStatus;
  start_date?: string;
  end_date?: string;
  updated_at: string;
}

export interface ProjectCreatePayload {
  code: string;
  name: LocalizedString;
  description?: string;
  start_date?: string;
  end_date?: string;
}

export type ProjectUpdatePayload = Partial<ProjectCreatePayload> & { status?: ProjectStatus };

export type ProjectList = PageEnvelope<Project>;
