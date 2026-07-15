/**
 * Audit Reader — domain types (D-1 FE).
 *
 * Mirrors the backend `AuditEventResponse` contract for
 * `GET /api/v1/audit?from=&to=&actorUserId=&tenantId=&action=&entityType=&entityId=&page=&size=`.
 *
 * Field names mirror backend JSON (camelCase here matches the contract notes
 * in the prompt — `createdAt`, `hashCurrent`, `correlationId`, etc.).
 *
 * Security rules:
 *   - The audit feed never contains salary values. The optional `metadata`
 *     bag is OPAQUE in this UI; we render it as JSON so a salary-related
 *     bag would simply not be human-readable (no specialised salary view).
 *   - `tenantId` may be present for HRLAB_SUPER_ADMIN with
 *     AUDIT_READ_CROSS_TENANT; regular users only see their own tenant
 *     because the backend filters by JWT.
 */
import type { PageEnvelope } from '@/shared/types/common';

/**
 * Canonical action code (matches `AuditAction.java` — 71 codes catalogued).
 * Kept as plain string to remain forward-compatible if backend adds codes.
 */
export type AuditActionCode = string;

/** Entity types that appear in `entityType` — non-exhaustive, plain string. */
export type AuditEntityType =
  | 'PROJECT'
  | 'TENANT'
  | 'USER'
  | 'USER_MEMBERSHIP'
  | 'DEPARTMENT'
  | 'POSITION'
  | 'JOB_PROFILE'
  | 'JOB_ANALYSIS'
  | 'METHODOLOGY'
  | 'METHODOLOGY_VERSION'
  | 'FACTOR'
  | 'FACTOR_LEVEL'
  | 'EVALUATION'
  | 'EVALUATION_SCORE'
  | 'GRADE_STRUCTURE'
  | 'GRADE'
  | 'GRADE_BAND'
  | 'APPROVAL_REQUEST'
  | 'COMMENT'
  | 'IMPORT'
  | 'EXPORT'
  | 'REPORT'
  | 'FILE'
  | (string & {});

/**
 * Backend wire shape — `AuditEventResponse`.
 *
 * `hashCurrent` is the per-event hash chain link; surfacing it as a badge
 * lets auditors verify chain integrity at a glance (forensics use case).
 */
export interface AuditEvent {
  id: string;
  action: AuditActionCode;
  tenantId: string | null;
  actorUserId: string | null;
  /** Optional convenience aggregate — backend may include actor display name. */
  actorName?: string | null;
  entityType: AuditEntityType | null;
  entityId: string | null;
  reason?: string | null;
  ipAddress?: string | null;
  userAgent?: string | null;
  correlationId?: string | null;
  /** ISO 8601 UTC timestamp. */
  createdAt: string;
  hashCurrent?: string | null;
  hashPrevious?: string | null;
  /** Opaque structured payload — render as JSON in the details drawer. */
  metadata?: Record<string, unknown> | null;
  /** Optional before/after snapshots for diffable mutations. */
  before?: Record<string, unknown> | null;
  after?: Record<string, unknown> | null;
}

export type AuditEventList = PageEnvelope<AuditEvent>;

/**
 * List query parameters. Each is optional.
 *
 * Tenant rule (FE-TI-004):
 *   - For regular roles, the backend filters by JWT — `tenantId` here is
 *     IGNORED unless the caller has AUDIT_READ_CROSS_TENANT.
 *   - Frontend never sends `tenantId` from a normal business form;
 *     the only call sites are the super-admin filter bar.
 */
export interface AuditQuery {
  from?: string;
  to?: string;
  actorUserId?: string;
  /** Honoured only for AUDIT_READ_CROSS_TENANT; ignored otherwise. */
  tenantId?: string;
  action?: AuditActionCode;
  entityType?: AuditEntityType;
  entityId?: string;
  page?: number;
  size?: number;
}

/**
 * MVP1-E10-1 — `GET /api/v1/audit/integrity` (AUDIT_READ, no request
 * params; tenant is derived server-side from `TenantContext`).
 *
 * Mirrors `AuditChainVerificationResult.Status` (backend
 * `uz.hrlab.grading.audit.application.AuditChainVerificationResult`).
 * Kept as `string & {}` fallback so an unrecognised future status never
 * crashes the UI — it degrades to the neutral/empty rendering instead.
 */
export type AuditIntegrityStatus = 'INTACT' | 'BROKEN' | 'EMPTY' | (string & {});

/** Mirrors `AuditChainVerificationResult.BreakType`. */
export type AuditIntegrityBreakType =
  | 'HASH_MISMATCH'
  | 'BROKEN_PREV_LINK'
  | 'GAP'
  | 'VERSION_REGRESSION'
  | (string & {});

/** Mirrors `AuditIntegrityResponse.Break`. */
export interface AuditIntegrityBreak {
  rowId: string;
  /** ISO 8601 UTC timestamp of the offending row. */
  createdAt: string | null;
  breakType: AuditIntegrityBreakType;
  /** SHA-256 hex digest — safe to display (never a secret). */
  expectedHash: string | null;
  /** SHA-256 hex digest — safe to display (never a secret). */
  actualHash: string | null;
}

/**
 * Mirrors `AuditIntegrityResponse` (backend
 * `uz.hrlab.grading.access.api.AuditIntegrityResponse`).
 *
 * Honesty contract (do not overclaim in the UI):
 *   - `intact === true` does NOT by itself mean "fully verified" — check
 *     `bounded`. When `bounded === true` the run stopped at `maxRows`
 *     before reaching the end of the chain: render as a PARTIAL pass
 *     ("first N of M rows"), never as a plain green "all intact".
 *   - `independentlyVerifiedCount` (content re-hashed) is a SUBSET of
 *     `checkedCount` (linkage-checked); `legacyUnverifiableCount` rows are
 *     linkage-checked but not content-recomputable — NEVER a break.
 */
export interface AuditIntegrityResult {
  tenantId: string | null;
  status: AuditIntegrityStatus;
  intact: boolean;
  checkedCount: number;
  chainLength: number;
  independentlyVerifiedCount: number;
  legacyUnverifiableCount: number;
  /** `created_at` of the first current-format (independently-verifiable) row. */
  verifiableFrom: string | null;
  /** `created_at` of the last row that passed its applicable checks. */
  verifiedThrough: string | null;
  /** True when the run stopped at `maxRows` before reaching the chain head. */
  bounded: boolean;
  maxRows: number;
  /** Present only when `status === 'BROKEN'`. */
  firstBreak: AuditIntegrityBreak | null;
  /** When this verification run executed. */
  verifiedAt: string | null;
}
