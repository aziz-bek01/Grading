/**
 * MVP1-E10-1 — "Verify integrity" action hooks for `GET /api/v1/audit/integrity`
 * (AUDIT_READ, no request params — tenant is derived server-side).
 *
 * Two hooks share ONE cache slot (`auditKeys.integrity(tenantScope)`),
 * mirroring the existing `useAdvanceWorkflow` / `useWorkflowProgress`
 * pattern in `features/workflow/hooks/useWorkflowProgress.ts` (a mutation
 * writes the query cache on success so every subscriber picks it up without
 * an extra request):
 *
 *   - `useVerifyAuditIntegrity` — the mutation the "Verify integrity" button
 *     fires. On success it writes the result into the shared cache slot.
 *   - `useAuditIntegrityStatus` — a READ-ONLY subscription (`enabled: false`,
 *     never auto-fetches) to that same slot. Used by the per-event
 *     hash-chain badge in `AuditDetailsDrawer` so it reflects only EARNED
 *     evidence (a verification the user actually ran this session) and
 *     never claims verification the backend hasn't returned.
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useAuthStore } from '@/features/auth/authStore';
import { fetchAuditIntegrity, auditKeys } from '../api/auditApi';

/**
 * Fires the on-demand chain verification. Not auto-run — the caller invokes
 * `mutate()`/`mutateAsync()` from the "Verify integrity" button.
 */
export function useVerifyAuditIntegrity() {
  const qc = useQueryClient();
  const tenantScope = useAuthStore((s) => s.activeTenant?.id);
  return useMutation({
    mutationFn: () => fetchAuditIntegrity(),
    onSuccess: (data) => {
      qc.setQueryData(auditKeys.integrity(tenantScope), data);
    },
  });
}

/**
 * Read-only view of the last verification run for the active tenant, if
 * any. `enabled: false` means this NEVER triggers a network request by
 * itself — it only observes whatever `useVerifyAuditIntegrity` most
 * recently wrote (or `undefined` before any run this session).
 */
export function useAuditIntegrityStatus() {
  const tenantScope = useAuthStore((s) => s.activeTenant?.id);
  return useQuery({
    queryKey: auditKeys.integrity(tenantScope),
    queryFn: () => fetchAuditIntegrity(),
    enabled: false,
  });
}
