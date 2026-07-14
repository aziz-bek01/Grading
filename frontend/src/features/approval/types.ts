/**
 * Approval domain types — POST-ADAPTER DOMAIN shapes (camelCase).
 *
 * The REAL backend wire is SNAKE_CASE (`spring.jackson.property-naming-strategy:
 * SNAKE_CASE`, application.yml:38) with `@JsonInclude(NON_NULL)` (null fields are
 * OMITTED, never sent). The single wire→domain boundary that translates
 * snake_case → camelCase and derives display-only fields is
 * `normalizeApprovalRequest` / `normalizeApprovalStep` in `api/approvalApi.ts`.
 * No component touches the raw wire — they all consume these domain types.
 *
 * `decisions[]` is SYNTHESIZED by the adapter from already-decided steps; it is
 * NOT a backend field on this endpoint. `totalSteps` and `currentStepOrder` are
 * likewise derived by the adapter.
 *
 * `entityLabel` (from `entity_label_i18n`), `initiatedByName`, `approverName`
 * and `decidedByName` ARE sent by the backend on the READ paths (detail /
 * inbox / list — BE-3..BE-6) and consumed by the adapter snake-first. They are
 * OMITTED (`@JsonInclude(NON_NULL)`) when the entity/user is missing or
 * cross-tenant; the components then fall back to a SHORT id via `shortId`.
 *
 * `projectLabel` (from `project_label_i18n`) mirrors `entityLabel` EXACTLY —
 * same localized-map shape, same `@JsonInclude(NON_NULL)` omission when the
 * project cannot be resolved, mapped through the same `asI18n` helper in the
 * adapter. It lets the cross-project global inbox (`/app/approvals`) show
 * WHICH project a card belongs to.
 */
import type { Locale } from '@/shared/types/common';

export type ApprovalRequestStatus =
  | 'PENDING'
  | 'APPROVED'
  | 'REJECTED'
  | 'CHANGES_REQUESTED'
  | 'CANCELLED';

export type ApprovalStepStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'SKIPPED';

// Mirrors backend ApprovalEntityType (approval/domain/ApprovalEntityType.java)
// EXACTLY — the 5 canonical types. 'PROJECT' was never a backend value and the
// backend rejects it as an `entity_type` filter with 400.
export type ApprovalEntityType =
  | 'JOB_PROFILE'
  | 'METHODOLOGY_VERSION'
  | 'EVALUATION'
  | 'EVALUATION_PANEL'
  | 'GRADE_STRUCTURE';

export interface ApprovalDecision {
  id: string;
  approvalRequestId: string;
  approvalStepId: string;
  decision: 'APPROVED' | 'REJECTED' | 'CHANGES_REQUESTED';
  decidedByUserId: string;
  decidedByName?: string | null;
  decidedAt: string;
  notes?: string | null;
  reason?: string | null;
}

export interface ApprovalStep {
  id: string;
  approvalRequestId: string;
  stepOrder: number;
  /** Optional explicit approver (UUID). When null the step is gated by `requiredPermission`. */
  approverUserId?: string | null;
  approverName?: string | null;
  /** Optional permission code that grants any holder the right to act on this step. */
  requiredPermission?: string | null;
  status: ApprovalStepStatus;
  decidedAt?: string | null;
  decidedByUserId?: string | null;
  decidedByName?: string | null;
  /** Notes (free-form) or reason text (required ≥ 20 chars for REJECT / CHANGES_REQUESTED). */
  notes?: string | null;
  reason?: string | null;
}

export interface ApprovalRequestSummary {
  id: string;
  projectId: string;
  entityType: ApprovalEntityType;
  entityId: string;
  entityLabel?: Partial<Record<Locale, string>>;
  /** BE-resolved localized project name (OMITTED when unresolved). See header note. */
  projectLabel?: Partial<Record<Locale, string>>;
  status: ApprovalRequestStatus;
  initiatedByUserId: string;
  initiatedByName?: string | null;
  initiatedAt: string;
  currentStepOrder?: number | null;
  totalSteps: number;
  notesI18n?: Partial<Record<Locale, string>>;
}

export interface ApprovalRequest extends ApprovalRequestSummary {
  steps: ApprovalStep[];
  /**
   * SYNTHESIZED by `normalizeApprovalRequest` from steps that are already
   * decided (APPROVED/REJECTED with a `decided_at`). This is NOT a backend
   * field — the BE response carries only `steps[]`. Always an array.
   */
  decisions: ApprovalDecision[];
  completedAt?: string | null;
}

export interface ApprovalRequestCreatePayload {
  entityType: ApprovalEntityType;
  entityId: string;
  notesI18n?: Partial<Record<Locale, string>>;
}

export interface ApprovalRequestListResponse {
  items: ApprovalRequestSummary[];
}

export interface ApprovalRequestListFilters {
  projectId?: string;
  entityType?: ApprovalEntityType;
  entityId?: string;
  status?: ApprovalRequestStatus;
  forCurrentUser?: boolean;
}
