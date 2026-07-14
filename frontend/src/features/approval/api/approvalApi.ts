import { httpClient } from '@/shared/api/httpClient';
import { endpoints } from '@/shared/api/endpoints';
import { pick, type Raw } from '@/shared/api/wireAdapter';
import i18n from '@/shared/i18n';
import { pickLocalized } from '@/shared/lib/localized';
import type { LocalizedString } from '@/shared/types/common';
import type {
  ApprovalDecision,
  ApprovalRequest,
  ApprovalRequestCreatePayload,
  ApprovalRequestListFilters,
  ApprovalRequestListResponse,
  ApprovalRequestSummary,
  ApprovalRequestStatus,
  ApprovalStep,
  ApprovalStepStatus,
} from '../types';

export const approvalKeys = {
  all: ['approvals'] as const,
  list: (filters?: ApprovalRequestListFilters) =>
    ['approvals', 'list', filters ?? {}] as const,
  detail: (id: string) => ['approvals', 'detail', id] as const,
  // Legacy un-scoped key (kept for back-compat with any direct cache writes).
  myInbox: ['approvals', 'my-inbox'] as const,
  // FE-1 (2026-06-12) — tenant-scoped inbox key. Including the active tenant id
  // means a tenant switch yields a DIFFERENT cache entry, so the inbox + sidebar
  // badge can never render the previous company-client's rows. The badge shares
  // THIS exact key (Sidebar.useApprovalInboxCount → useMyApprovalInbox), so
  // badge count == inbox length always (FE-3).
  myInboxForTenant: (tenantId: string | null) =>
    ['approvals', 'my-inbox', tenantId] as const,
};

/* ------------------------------------------------------------------ *
 * Wire → domain adapter (Contract-A)
 *
 * The REAL backend serialises every response in SNAKE_CASE (global Jackson
 * `spring.jackson.property-naming-strategy: SNAKE_CASE`, application.yml:38)
 * with `@JsonInclude(NON_NULL)` — null fields are OMITTED, never sent. The
 * approval feature's domain types and EVERY consumer (inbox cards, list page,
 * detail page, actions bar, sidebar badge) read camelCase. Without this map
 * `initiatedAt`, `status`, `steps[].stepOrder` are all `undefined` → "Invalid
 * Date" / `toneMap[undefined]` crash → the global "Хатолик юз берди" screen.
 *
 * The shared `pick` primitive (wireAdapter.ts, same pattern as the proven
 * `normalizeAuditEvent` in auditApi.ts) reads snake_case FIRST with a camelCase
 * fallback `(raw[snake] ?? raw[camel])` so the in-process MSW mock and the real
 * backend both deserialise. This is the ONLY place case-translation happens —
 * no per-component duplication. The i18n-aware helpers below
 * (`asI18n` / `localized`) and the derived/synthesized fields stay local: they
 * are approval-specific and would leak into the shared layer.
 *
 * Display-only fields the BE does NOT send in this hotfix are derived HERE:
 *   totalSteps         = steps.length
 *   currentStepOrder   = min(stepOrder where status === 'PENDING')
 *   initiatedByName    = initiated_by_name (if BE later sends it) ?? null
 *   entityLabel        = entity_label_i18n (if present) ?? undefined
 *   projectLabel       = project_label_i18n (if present) ?? undefined — mapped
 *                         through the SAME `asI18n` helper as entityLabel
 *   decisions[]        = synthesized from decided steps
 *   step.reason        = localized(reason_i18n) for the active locale
 * ------------------------------------------------------------------ */

function asI18n(value: unknown): LocalizedString | undefined {
  if (value && typeof value === 'object' && !Array.isArray(value)) {
    return value as LocalizedString;
  }
  return undefined;
}

function localized(value: unknown): string | null {
  const map = asI18n(value);
  if (!map) return null;
  const text = pickLocalized(map, i18n.language);
  return text.length > 0 ? text : null;
}

function mapStepStatusToDecision(
  status: ApprovalStepStatus,
): ApprovalDecision['decision'] {
  if (status === 'REJECTED') return 'REJECTED';
  return 'APPROVED';
}

export function normalizeApprovalStep(raw: Raw, requestId: string): ApprovalStep {
  const reasonI18n = asI18n(raw['reason_i18n'] ?? raw['reasonI18n']);
  return {
    id: String(raw.id ?? ''),
    approvalRequestId: requestId,
    stepOrder: Number(pick<number>(raw, 'step_order', 'stepOrder') ?? 0),
    approverUserId: pick(raw, 'approver_user_id', 'approverUserId'),
    // BE does not send a name today — fall back to the UUID in components.
    approverName: pick(raw, 'approver_name', 'approverName'),
    requiredPermission: pick(raw, 'required_permission', 'requiredPermission'),
    status: (pick<ApprovalStepStatus>(raw, 'status', 'status') ??
      'PENDING') as ApprovalStepStatus,
    decidedAt: pick(raw, 'decided_at', 'decidedAt'),
    decidedByUserId: pick(raw, 'decided_by', 'decidedBy') ?? pick(raw, 'decided_by_user_id', 'decidedByUserId'),
    decidedByName: pick(raw, 'decided_by_name', 'decidedByName'),
    notes: pick(raw, 'notes', 'notes'),
    // Localize reason_i18n into the flat `reason` string StepCard reads, so
    // reasons render without a component change.
    reason: localized(reasonI18n) ?? pick(raw, 'reason', 'reason'),
  };
}

export function normalizeApprovalRequest(input: unknown): ApprovalRequest {
  const raw = (input ?? {}) as Raw;
  const id = String(raw.id ?? '');

  const rawSteps = Array.isArray(raw['steps']) ? (raw['steps'] as unknown[]) : [];
  const steps: ApprovalStep[] = rawSteps.map((s) =>
    normalizeApprovalStep((s ?? {}) as Raw, id),
  );

  // Derived display fields the BE does not send (this hotfix).
  const totalSteps = steps.length;
  const pendingOrders = steps
    .filter((s) => s.status === 'PENDING')
    .map((s) => s.stepOrder);
  const currentStepOrder = pendingOrders.length > 0 ? Math.min(...pendingOrders) : null;

  // Synthesize the decisions[] array from already-decided steps so the detail
  // page renders the decision history without a BE change. Guaranteed array.
  const decisions: ApprovalDecision[] = steps
    .filter((s) => (s.status === 'APPROVED' || s.status === 'REJECTED') && !!s.decidedAt)
    .map((s) => ({
      id: s.id,
      approvalRequestId: id,
      approvalStepId: s.id,
      decision: mapStepStatusToDecision(s.status),
      decidedByUserId: s.decidedByUserId ?? '',
      decidedByName: s.decidedByName ?? null,
      decidedAt: s.decidedAt ?? '',
      notes: s.notes ?? null,
      reason: s.reason ?? null,
    }));

  const summary: ApprovalRequestSummary = {
    id,
    projectId: pick(raw, 'project_id', 'projectId') ?? '',
    // Fall back to a REAL backend enum value ('JOB_PROFILE'), never the
    // non-existent 'PROJECT' — a defaulted-to-PROJECT type echoed back into an
    // entity_type filter would 400. In practice the backend always sends a
    // value; this default only guards a malformed/partial payload.
    entityType: (pick(raw, 'entity_type', 'entityType') ??
      'JOB_PROFILE') as ApprovalRequestSummary['entityType'],
    entityId: pick(raw, 'entity_id', 'entityId') ?? '',
    entityLabel: asI18n(raw['entity_label_i18n'] ?? raw['entityLabel']),
    // Mirrors entityLabel exactly — same helper, same NON_NULL omission.
    projectLabel: asI18n(raw['project_label_i18n'] ?? raw['projectLabel']),
    status: (pick<ApprovalRequestStatus>(raw, 'current_status', 'status') ??
      'PENDING') as ApprovalRequestStatus,
    initiatedByUserId:
      pick(raw, 'requested_by', 'initiatedByUserId') ??
      pick(raw, 'requested_by', 'requestedBy') ??
      '',
    initiatedByName: pick(raw, 'initiated_by_name', 'initiatedByName'),
    initiatedAt: pick(raw, 'requested_at', 'initiatedAt') ?? pick(raw, 'requested_at', 'requestedAt') ?? '',
    currentStepOrder,
    totalSteps,
    notesI18n: asI18n(raw['notes_i18n'] ?? raw['notesI18n']),
  };

  return {
    ...summary,
    steps,
    decisions,
    completedAt: pick(raw, 'completed_at', 'completedAt'),
  };
}

export async function createApprovalRequest(
  payload: ApprovalRequestCreatePayload,
): Promise<ApprovalRequest> {
  const res = await httpClient.post<unknown>(endpoints.approval.requests, payload);
  return normalizeApprovalRequest(res.data);
}

export async function fetchApprovalRequests(
  filters: ApprovalRequestListFilters = {},
): Promise<ApprovalRequestListResponse> {
  // Real wire = PageResponse envelope { items, page, size, total_elements,
  // total_pages }. Type as `unknown` (audit pattern) — the camelCase type must
  // not lie about the wire — and normalise each item at the boundary.
  const res = await httpClient.get<unknown>(endpoints.approval.requests, {
    params: filters,
  });
  const env = (res.data ?? {}) as Raw;
  const rawItems = Array.isArray(env.items)
    ? (env.items as unknown[])
    : Array.isArray(res.data)
      ? (res.data as unknown[])
      : [];
  return { items: rawItems.map((r) => normalizeApprovalRequest(r)) };
}

export async function fetchApprovalRequest(id: string): Promise<ApprovalRequest> {
  const res = await httpClient.get<unknown>(endpoints.approval.detail(id));
  return normalizeApprovalRequest(res.data);
}

export async function approveStep(
  approvalId: string,
  stepId: string,
  notes?: string,
): Promise<ApprovalRequest> {
  const res = await httpClient.post<unknown>(
    endpoints.approval.approveStep(approvalId, stepId),
    notes ? { notes } : {},
  );
  return normalizeApprovalRequest(res.data);
}

export async function rejectStep(
  approvalId: string,
  stepId: string,
  reason: string,
): Promise<ApprovalRequest> {
  const res = await httpClient.post<unknown>(
    endpoints.approval.rejectStep(approvalId, stepId),
    { reason },
  );
  return normalizeApprovalRequest(res.data);
}

export async function requestChangesStep(
  approvalId: string,
  stepId: string,
  reason: string,
): Promise<ApprovalRequest> {
  const res = await httpClient.post<unknown>(
    endpoints.approval.requestChangesStep(approvalId, stepId),
    { reason },
  );
  return normalizeApprovalRequest(res.data);
}

export async function cancelApprovalRequest(approvalId: string): Promise<ApprovalRequest> {
  const res = await httpClient.post<unknown>(endpoints.approval.cancel(approvalId), {});
  return normalizeApprovalRequest(res.data);
}

export async function fetchMyInbox(): Promise<ApprovalRequest[]> {
  // Backend /my-inbox returns a BARE ARRAY (`[]`), NOT a PageResponse envelope
  // (ApprovalController.inbox()). We tolerate a `{items}` fallback for safety
  // and normalise every element through the wire→domain adapter so the cards
  // and the sidebar badge read the same domain shape (including steps[]).
  const res = await httpClient.get<unknown>(endpoints.approval.myInbox);
  const data = res.data;
  const rawItems = Array.isArray(data)
    ? (data as unknown[])
    : Array.isArray((data as Raw)?.items)
      ? ((data as Raw).items as unknown[])
      : [];
  return rawItems.map((r) => normalizeApprovalRequest(r));
}
