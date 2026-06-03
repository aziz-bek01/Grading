/**
 * Mutation hooks for the Client Companies module.
 *
 * Every mutation blows away the `clientKeys.all` namespace so the list view
 * and the detail view stay consistent. The narrower invalidation surface
 * (single-tenant cache key) is intentionally NOT used here because mutations
 * may have ripple effects (e.g. archiving a tenant changes project_count).
 */
import { useMutation, useQueryClient } from '@tanstack/react-query';
import {
  archiveTenant,
  clientKeys,
  updateClient,
  updateTenant,
} from '../api/clientApi';
import type {
  ArchiveTenantPayload,
  UpdateClientCompanyPayload,
  UpdateTenantPayload,
} from '../types/clientTypes';

function useInvalidate() {
  const qc = useQueryClient();
  return () => qc.invalidateQueries({ queryKey: clientKeys.all });
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
