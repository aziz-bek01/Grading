import type { LocalizedString, PageEnvelope } from '@/shared/types/common';

export type ProjectStatus = 'DRAFT' | 'ACTIVE' | 'IN_REVIEW' | 'APPROVED' | 'LOCKED' | 'ARCHIVED';

/**
 * Mirrors backend `ProjectResponse` (snake_case JSON via Spring Jackson SNAKE_CASE).
 *
 * NOTE: backend does NOT echo `tenant_id` on the wire (it is derived from JWT) —
 * historic `tenant_id` field is retained as optional ONLY for MSW back-compat
 * during the Contract-A migration window.
 */
export interface Project {
  id: string;
  /** @deprecated backend never echoes tenant_id; kept optional for MSW back-compat. */
  tenant_id?: string;
  code: string;
  name_i18n: LocalizedString;
  description?: string;
  status: ProjectStatus;
  methodology_version_id?: string | null;
  start_date?: string;
  end_date?: string;
  /** Optional — backend may not include in list rows (kept for MSW back-compat). */
  updated_at?: string;
}

export interface ProjectCreatePayload {
  code: string;
  name_i18n: LocalizedString;
  description?: string;
  start_date?: string;
  end_date?: string;
}

export type ProjectUpdatePayload = Partial<ProjectCreatePayload> & { status?: ProjectStatus };

export type ProjectList = PageEnvelope<Project>;
