/**
 * Audit Reader API client (D-1 FE).
 *
 * Covers two endpoints:
 *   1. GET /audit                         → paginated list with filters
 *   2. GET /audit/:id                     → single event (used by drawer)
 *
 * Tenant rule (F-208 / FE-TI-004):
 *   The body NEVER carries `tenant_id`. The list endpoint accepts an
 *   explicit `tenantId` query param ONLY for callers with
 *   AUDIT_READ_CROSS_TENANT (HRLAB_SUPER_ADMIN); for everyone else the
 *   backend filters by JWT and we MUST NOT include it. The
 *   `useAuditEvents` hook decides whether to forward it.
 */
import { httpClient } from '@/shared/api/httpClient';
import { endpoints } from '@/shared/api/endpoints';
import type {
  AuditEvent,
  AuditEventList,
  AuditIntegrityBreak,
  AuditIntegrityResult,
  AuditQuery,
} from '../types/auditTypes';

/**
 * React-Query cache keys. `tenantScope` is included so the cache is busted
 * when the active tenant changes (mirrors the pattern used by `userKeys`).
 * It is NEVER sent on the wire by these fetchers for non-super-admins.
 */
export const auditKeys = {
  all: ['audit'] as const,
  list: (tenantScope?: string, query?: AuditQuery) =>
    ['audit', 'list', tenantScope ?? null, query ?? null] as const,
  detail: (id: string) => ['audit', 'detail', id] as const,
  /**
   * MVP1-E10-1 — cache slot for the last "Verify integrity" run result.
   * Scoped by `tenantScope` (mirrors `list`) so switching the active tenant
   * never leaks one tenant's verification evidence onto another's badge.
   * `useVerifyAuditIntegrity` WRITES this key on success; `useAuditIntegrityStatus`
   * only READS it (never auto-fetches) so every consumer (the verify panel AND
   * the per-event hash-chain badge) shares one earned-evidence source of truth.
   */
  integrity: (tenantScope?: string) => ['audit', 'integrity', tenantScope ?? null] as const,
};

export async function fetchAuditEvents(query: AuditQuery = {}): Promise<AuditEventList> {
  const res = await httpClient.get<AuditEventList>(endpoints.audit.list, {
    params: cleanParams(query as Record<string, unknown>),
  });
  const env = (res.data ?? {}) as Partial<AuditEventList> & Record<string, unknown>;
  const rawItems = Array.isArray(env.items) ? (env.items as unknown[]) : [];
  return {
    items: rawItems.map((r) => normalizeAuditEvent(r as Record<string, unknown>)),
    page: (env.page as number) ?? 0,
    size: (env.size as number) ?? rawItems.length,
    total_elements: (env.total_elements as number) ?? rawItems.length,
    total_pages: (env.total_pages as number) ?? 1,
  };
}

export async function fetchAuditEvent(id: string): Promise<AuditEvent> {
  // Raw wire shape — `normalizeAuditEvent` is the wire→domain adapter, so type
  // the response as `unknown` (it is NOT yet an AuditEvent before normalisation).
  const res = await httpClient.get<unknown>(endpoints.audit.detail(id));
  return normalizeAuditEvent((res.data ?? {}) as Record<string, unknown>);
}

/**
 * Shared snake_case↔camelCase field picker for this module's wire→domain
 * adapters (`normalizeAuditEvent`, `normalizeAuditIntegrity`). Extracted so
 * both tolerate the same two shapes (real snake_case backend, camelCase MSW
 * fixtures) via ONE implementation instead of two near-identical closures.
 */
function pick<T = string>(
  raw: Record<string, unknown>,
  snake: string,
  camel: string,
): T | null {
  return ((raw[snake] ?? raw[camel]) as T | undefined) ?? null;
}

/**
 * Wire → domain adapter (Contract-A).
 *
 * The real backend serialises every response in SNAKE_CASE (global Jackson
 * `spring.jackson.property-naming-strategy: SNAKE_CASE`): `created_at`,
 * `actor_user_id`, `entity_type`, `correlation_id`, … . The audit feature's
 * domain type ({@link AuditEvent}) and EVERY consumer — the list table, the
 * details drawer, the CSV export, and the Workspace "Recent Activity" panel
 * (D-2) — read camelCase fields. Without this mapping `createdAt` is
 * `undefined` (→ "Invalid Date") and `actorUserId` is `undefined`
 * (→ "Unknown user").
 *
 * This is the single wire→domain boundary, so no component needs to change.
 * It is tolerant of BOTH shapes (`snake ?? camel`) so the in-process MSW
 * mock (camelCase fixtures in `auditFixtures.ts`) and the real backend
 * (snake_case) both deserialise correctly.
 */
function normalizeAuditEvent(raw: Record<string, unknown>): AuditEvent {
  const s = <T = string>(snake: string, camel: string): T | null => pick<T>(raw, snake, camel);
  return {
    id: String(raw.id ?? ''),
    action: String(raw.action ?? ''),
    tenantId: s('tenant_id', 'tenantId'),
    actorUserId: s('actor_user_id', 'actorUserId'),
    actorName: s('actor_name', 'actorName'),
    entityType: s<AuditEvent['entityType']>('entity_type', 'entityType'),
    entityId: s('entity_id', 'entityId'),
    reason: s('reason', 'reason'),
    ipAddress: s('ip_address', 'ipAddress'),
    userAgent: s('user_agent', 'userAgent'),
    correlationId: s('correlation_id', 'correlationId'),
    createdAt: String((raw.created_at ?? raw.createdAt) ?? ''),
    hashCurrent: s('hash_current', 'hashCurrent'),
    hashPrevious: s('hash_previous', 'hashPrevious'),
    metadata: s<Record<string, unknown>>('metadata', 'metadata'),
    before: s<Record<string, unknown>>('before', 'before'),
    after: s<Record<string, unknown>>('after', 'after'),
  };
}

/**
 * MVP1-E10-1 — `GET /audit/integrity` (AUDIT_READ, no request params). See
 * `useVerifyAuditIntegrity` for the mutation that calls this on demand.
 */
export async function fetchAuditIntegrity(): Promise<AuditIntegrityResult> {
  const res = await httpClient.get<unknown>(endpoints.audit.integrity);
  return normalizeAuditIntegrity((res.data ?? {}) as Record<string, unknown>);
}

function normalizeAuditIntegrityBreak(raw: unknown): AuditIntegrityBreak | null {
  if (!raw || typeof raw !== 'object') return null;
  const r = raw as Record<string, unknown>;
  return {
    rowId: String(pick(r, 'row_id', 'rowId') ?? ''),
    createdAt: pick(r, 'created_at', 'createdAt'),
    breakType: String(pick(r, 'break_type', 'breakType') ?? ''),
    expectedHash: pick(r, 'expected_hash', 'expectedHash'),
    actualHash: pick(r, 'actual_hash', 'actualHash'),
  };
}

/** Wire (`AuditIntegrityResponse`, snake_case) → domain adapter. */
function normalizeAuditIntegrity(raw: Record<string, unknown>): AuditIntegrityResult {
  const num = (snake: string, camel: string): number => {
    const v = pick<number | string>(raw, snake, camel);
    return v === null ? 0 : Number(v);
  };
  const bool = (snake: string, camel: string): boolean => pick<boolean>(raw, snake, camel) === true;
  return {
    tenantId: pick(raw, 'tenant_id', 'tenantId'),
    status: String(raw.status ?? ''),
    intact: bool('intact', 'intact'),
    checkedCount: num('checked_count', 'checkedCount'),
    chainLength: num('chain_length', 'chainLength'),
    independentlyVerifiedCount: num('independently_verified_count', 'independentlyVerifiedCount'),
    legacyUnverifiableCount: num('legacy_unverifiable_count', 'legacyUnverifiableCount'),
    verifiableFrom: pick(raw, 'verifiable_from', 'verifiableFrom'),
    verifiedThrough: pick(raw, 'verified_through', 'verifiedThrough'),
    bounded: bool('bounded', 'bounded'),
    maxRows: num('max_rows', 'maxRows'),
    firstBreak: normalizeAuditIntegrityBreak(pick<unknown>(raw, 'first_break', 'firstBreak')),
    verifiedAt: pick(raw, 'verified_at', 'verifiedAt'),
  };
}

function cleanParams(input: Record<string, unknown>): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const [k, v] of Object.entries(input)) {
    if (v === undefined || v === null || v === '') continue;
    out[k] = v;
  }
  return out;
}
