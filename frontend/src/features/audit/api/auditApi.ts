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
import type { AuditEvent, AuditEventList, AuditQuery } from '../types/auditTypes';

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
  const s = <T = string>(snake: string, camel: string): T | null =>
    ((raw[snake] ?? raw[camel]) as T | undefined) ?? null;
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

function cleanParams(input: Record<string, unknown>): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const [k, v] of Object.entries(input)) {
    if (v === undefined || v === null || v === '') continue;
    out[k] = v;
  }
  return out;
}
