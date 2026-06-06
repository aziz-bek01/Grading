/**
 * Mutation hooks for the Client Companies module.
 *
 * Every mutation blows away the `clientKeys.all` namespace so the list view
 * and the detail view stay consistent. The narrower invalidation surface
 * (single-tenant cache key) is intentionally NOT used here because mutations
 * may have ripple effects (e.g. archiving a tenant changes project_count).
 */
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useAuthStore } from '@/features/auth/authStore';
import {
  archiveTenant,
  clientKeys,
  createTenant,
  updateClient,
  updateTenant,
} from '../api/clientApi';
import type {
  ArchiveTenantPayload,
  CreateTenantPayload,
  UpdateClientCompanyPayload,
  UpdateTenantPayload,
} from '../types/clientTypes';

function useInvalidate() {
  const qc = useQueryClient();
  return () => qc.invalidateQueries({ queryKey: clientKeys.all });
}

/**
 * Create a new tenant / company-client.
 *
 * On success we both:
 *   1. invalidate the portfolio tenants/clients list (so the table refreshes), and
 *   2. refresh `/users/me` via the auth store so the new tenant — which the
 *      backend auto-members the creator into in shared mode — shows up in the
 *      TenantSelector immediately (the switcher reads `user.tenants`, not React
 *      Query). The refresh failure is swallowed so a stale switcher never blocks
 *      a successful create.
 */
export function useCreateTenant() {
  const invalidate = useInvalidate();
  const refreshUser = useAuthStore((s) => s.refreshUser);
  return useMutation({
    mutationFn: (payload: CreateTenantPayload) => createTenant(payload),
    onSuccess: async () => {
      invalidate();
      try {
        await refreshUser();
      } catch {
        /* switcher will catch up on next /users/me refresh */
      }
    },
  });
}

export function useUpdateTenant(tenantId: string) {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: (payload: UpdateTenantPayload) => updateTenant(tenantId, payload),
    onSuccess: invalidate,
  });
}

export function useArchiveTenant(tenantId: string) {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: (payload: ArchiveTenantPayload) => archiveTenant(tenantId, payload),
    onSuccess: invalidate,
  });
}

export function useUpdateClient(clientId: string) {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: (payload: UpdateClientCompanyPayload) => updateClient(clientId, payload),
    onSuccess: invalidate,
  });
}
