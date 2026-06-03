import { useQuery } from '@tanstack/react-query';
import { useAuthStore } from '@/features/auth/authStore';
import { fetchAuditEvent, fetchAuditEvents, auditKeys } from '../api/auditApi';
import type { AuditQuery } from '../types/auditTypes';

/**
 * Paginated audit feed. Tenant id is included in the cache key (FE-TI-004)
 * so the list refetches when the active tenant changes — it is NEVER sent
 * on the wire for non-super-admin roles (backend derives it from the JWT).
 *
 * Callers with AUDIT_READ_CROSS_TENANT may explicitly set `query.tenantId`
 * to inspect another tenant; the backend honours that only for them.
 */
export function useAuditEvents(query: AuditQuery = {}, opts: { enabled?: boolean } = {}) {
  const tenantScope = useAuthStore((s) => s.activeTenant?.id);
  return useQuery({
    queryKey: auditKeys.list(tenantScope, query),
    queryFn: () => fetchAuditEvents(query),
    enabled: opts.enabled ?? true,
  });
}

export function useAuditEvent(id: string | undefined) {
  return useQuery({
    queryKey: id ? auditKeys.detail(id) : ['audit', 'detail', null],
    queryFn: () => fetchAuditEvent(id!),
    enabled: !!id,
  });
}
