/**
 * Approval domain types — mirror the MVP 2 Phase 1 backend contract for
 * ApprovalRequest, ApprovalStep, ApprovalDecision.
 */
import type { Locale } from '@/shared/types/common';

export type ApprovalRequestStatus =
  | 'PENDING'
  | 'APPROVED'
  | 'REJECTED'
  | 'CHANGES_REQUESTED'
  | 'CANCELLED';

export type ApprovalStepStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'SKIPPED';

export type ApprovalEntityType =
  | 'JOB_PROFILE'
  | 'METHODOLOGY_VERSION'
  | 'EVALUATION'
  | 'GRADE_STRUCTURE'
  | 'PROJECT';

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
