import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Building2, ChevronDown, Check } from 'lucide-react';
import { useQueryClient } from '@tanstack/react-query';
import { useAuthStore } from '@/features/auth/authStore';
import { ConfirmDialog } from '@/shared/components/confirm-dialog/ConfirmDialog';
import type { TenantSummary } from '@/shared/auth/authTypes';
import { cn } from '@/shared/lib/cn';

/**
 * TenantSelector — never accepts free-text tenant_id (security blueprint API-13).
 * Switching is gated by ConfirmDialog and clears the project context.
 *
 * FE-TI-004: after a tenant switch we MUST invalidate the whole React Query
 * cache so that any project-scoped resource (projects, positions, methodologies,
 * evaluations, grade structures, approvals, etc.) re-fetches under the new
 * tenant context. Without this, the projects page would render the stale list
 * for the previous tenant until staleTime elapses. Tenant resolution itself
 * stays server-side: the backend reads the active tenant from the JWT and the
 * mock adapter reads it from `X-Mock-Tenant-Id`; clients never send tenant_id.
 */
export function TenantSelector() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const user = useAuthStore((s) => s.user);
  const active = useAuthStore((s) => s.activeTenant);
  const setActiveTenant = useAuthStore((s) => s.setActiveTenant);
  const [open, setOpen] = useState(false);
  const [pending, setPending] = useState<TenantSummary | null>(null);

  if (!user) return null;
  const tenants = user.tenants;

  const onSelect = (tenant: TenantSummary) => {
    setOpen(false);
    if (tenant.id === active?.id) return;
    setPending(tenant);
  };

  const onConfirm = () => {
    if (pending) {
      setActiveTenant(pending);
      // Bust every cached query so resource hooks re-fetch under the new
      // tenant context. activeProject is already cleared by the store.
      queryClient.invalidateQueries();
    }
    setPending(null);
  };

  const fingerprintStyle = active?.fingerprint_hue !== undefined
    ? { backgroundColor: `hsl(${active.fingerprint_hue} 70% 45%)` }
    : { backgroundColor: 'transparent' };

  return (
    <>
      <div className="relative">
        <button
          type="button"
          aria-label={t('tenant.selector_aria')}
          aria-haspopup="listbox"
          aria-expanded={open}
          onClick={() => setOpen((v) => !v)}
          className={cn(
            'inline-flex items-center gap-2 h-9 px-3 rounded-md text-sm',
            'border border-border-strong bg-surface text-text-primary hover:bg-divider',
          )}
        >
          <span className="inline-block w-2 h-2 rounded-full" aria-hidden style={fingerprintStyle} />
          <Building2 size={14} aria-hidden className="text-text-secondary" />
          <span className="font-medium">
            {active ? active.brand_name : t('app.all_clients')}
          </span>
          <ChevronDown size={14} aria-hidden />
        </button>
        {open ? (
          <ul
            role="listbox"
            className="absolute left-0 mt-2 w-72 bg-surface border border-border rounded-lg shadow-md py-1 z-30"
          >
            {tenants.length === 0 ? (
              <li className="px-3 py-2 text-sm text-text-muted">—</li>
            ) : (
              tenants.map((tn) => (
                <li key={tn.id}>
                  <button
                    role="option"
                    aria-selected={active?.id === tn.id}
                    onClick={() => onSelect(tn)}
                    className={cn(
                      'w-full flex items-center justify-between px-3 py-2 text-sm hover:bg-divider',
                      active?.id === tn.id ? 'text-primary-600' : 'text-text-primary',
                    )}
                  >
                    <span className="flex items-center gap-2">
                      <span
                        className="inline-block w-2 h-2 rounded-full"
                        aria-hidden
                        style={
                          tn.fingerprint_hue !== undefined
                            ? { backgroundColor: `hsl(${tn.fingerprint_hue} 70% 45%)` }
                            : undefined
                        }
                      />
                      {tn.brand_name}
                    </span>
                    {active?.id === tn.id ? <Check size={14} aria-hidden /> : null}
                  </button>
                </li>
              ))
            )}
          </ul>
        ) : null}
      </div>

      <ConfirmDialog
        open={!!pending}
        title={t('tenant.switch_title')}
        body={t('tenant.switch_body', {
          from: active?.brand_name ?? '',
          to: pending?.brand_name ?? '',
        })}
        confirmLabel={t('tenant.switch_confirm') ?? undefined}
        onConfirm={onConfirm}
        onCancel={() => setPending(null)}
      />
    </>
  );
}
