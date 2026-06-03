/**
 * /app/audit — Audit Reader UI (D-1 FE).
 *
 * Replaces the previous PlaceholderPage. Full forensic view of audit events
 * for the active tenant — table + filter bar + details drawer + CSV export.
 *
 * Security:
 *   - The route is wrapped in <RequireAuditPermission /> so unauthorised
 *     users never reach this component.
 *   - For HRLAB_SUPER_ADMIN with AUDIT_READ_CROSS_TENANT, the tenant
 *     filter is visible so a single view can span all tenants. For
 *     regular users, the backend silently filters by JWT.
 *   - CSV export is gated by EXPORT_GENERAL (PermissionGate wrap).
 *   - Salary values are never present in audit events — no SalaryValue
 *     mask is needed in this view.
 *
 * Deep-link support: reads `userId`, `projectId`, `action`, `entityType`,
 * `entityId` from query string so `/app/users/:id` and the Recent
 * Activity panel (D-2) can link in pre-filtered.
 */
import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Card } from '@/shared/components/ui/Card';
import { DataTable, type DataTableColumn } from '@/shared/components/data-table/DataTable';
import { Breadcrumbs } from '@/shared/components/layout/Breadcrumbs';
import { ErrorState } from '@/shared/components/feedback/ErrorState';
import { PermissionGate } from '@/shared/components/access/PermissionGate';
import { PERMISSIONS } from '@/shared/types/permissions';
import { useAuthStore } from '@/features/auth/authStore';
import { usePermission } from '@/features/auth/usePermission';
import { useAuditEvents } from '../hooks/useAuditEvents';
import { AuditFilterBar } from '../components/AuditFilterBar';
import { AuditDetailsDrawer } from '../components/AuditDetailsDrawer';
import { AuditCsvExportButton } from '../components/AuditCsvExportButton';
import { ActionIcon, actionIconKind } from '../components/AuditEventRow';
import type { AuditEvent, AuditQuery } from '../types/auditTypes';

const PAGE_SIZE = 20;

export function AuditListPage() {
  const { t, i18n } = useTranslation();
  const [searchParams, setSearchParams] = useSearchParams();
  const user = useAuthStore((s) => s.user);
  const activeTenant = useAuthStore((s) => s.activeTenant);
  const { can } = usePermission();
  const isCrossTenantReader = can(PERMISSIONS.AUDIT_READ_CROSS_TENANT);

  // Initial filter from URL search params (deep-link support).
  const [filter, setFilter] = useState<AuditQuery>(() => ({
    actorUserId: searchParams.get('userId') ?? searchParams.get('actorUserId') ?? undefined,
    entityType: (searchParams.get('entityType') as AuditQuery['entityType']) ?? undefined,
    entityId:
      searchParams.get('projectId') ??
      searchParams.get('entityId') ??
      undefined,
    action: searchParams.get('action') ?? undefined,
    from: searchParams.get('from') ?? undefined,
    to: searchParams.get('to') ?? undefined,
    page: 0,
    size: PAGE_SIZE,
  }));

  // Keep URL in sync with current filter for shareable links.
  useEffect(() => {
    const next = new URLSearchParams();
    if (filter.actorUserId) next.set('actorUserId', filter.actorUserId);
    if (filter.entityType) next.set('entityType', filter.entityType);
    if (filter.entityId) next.set('entityId', filter.entityId);
    if (filter.action) next.set('action', filter.action);
    if (filter.from) next.set('from', filter.from);
    if (filter.to) next.set('to', filter.to);
    setSearchParams(next, { replace: true });
  }, [filter, setSearchParams]);

  const { data, isLoading, error, refetch } = useAuditEvents(filter);

  const [drawerEvent, setDrawerEvent] = useState<AuditEvent | null>(null);

  const rows = data?.items ?? [];

  const subtitle = activeTenant
    ? t('audit.subtitle_for_tenant', { tenant: activeTenant.brand_name })
    : t('audit.subtitle');

  const tenantOptions = useMemo(
    () =>
      isCrossTenantReader && user
        ? user.tenants.map((tn) => ({ value: tn.id, label: tn.brand_name }))
        : [],
    [isCrossTenantReader, user],
  );

  const columns: DataTableColumn<AuditEvent>[] = [
    {
      key: 'createdAt',
      header: t('audit.column.timestamp'),
      width: '180px',
      render: (e) => (
        <span className="text-text-primary text-sm">
          {new Date(e.createdAt).toLocaleString(i18n.language)}
        </span>
      ),
      sortable: true,
      sortAccessor: (e) => e.createdAt,
    },
    {
      key: 'action',
      header: t('audit.column.action'),
      render: (e) => {
        const kind = actionIconKind(e.action);
        return (
          <div className="flex items-center gap-2">
            <ActionIcon kind={kind} />
            <span className="text-text-primary text-sm">
              {t(`audit.action.${e.action}`, { defaultValue: e.action })}
            </span>
          </div>
        );
      },
      sortable: true,
      sortAccessor: (e) => e.action,
    },
    {
      key: 'actor',
      header: t('audit.column.actor'),
      width: '200px',
      render: (e) => (
        <span className="text-text-primary text-sm">
          {e.actorName ?? e.actorUserId ?? <em className="text-text-muted">{t('common.unknown_actor')}</em>}
        </span>
      ),
    },
    {
      key: 'entity',
      header: t('audit.column.entity'),
      render: (e) =>
        e.entityType ? (
          <span className="font-mono text-xs text-text-secondary">
            {e.entityType}
            {e.entityId ? ` · ${e.entityId.slice(0, 12)}` : ''}
          </span>
        ) : (
          <span className="text-text-muted">—</span>
        ),
    },
    {
      key: 'correlation',
      header: t('audit.column.correlation_id'),
      width: '140px',
      defaultVisible: false,
      render: (e) => (
        <span className="font-mono text-xs text-text-secondary">{e.correlationId ?? '—'}</span>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      <Breadcrumbs extra={[{ label: t('nav.audit') }]} />
      <header className="flex items-start justify-between gap-4 flex-wrap">
        <div>
          <h1 className="text-2xl text-text-primary">{t('audit.title')}</h1>
          <p className="text-sm text-text-secondary mt-1">{subtitle}</p>
        </div>
      </header>

      <Card>
        {error ? (
          <ErrorState onRetry={() => refetch()} />
        ) : (
          <DataTable<AuditEvent>
            rows={rows}
            columns={columns}
            rowKey={(e) => e.id}
            loading={isLoading}
            onRowClick={(e) => setDrawerEvent(e)}
            emptyTitle={t('audit.empty_title')}
            emptyBody={t('audit.empty')}
            filterBar={
              <AuditFilterBar
                value={filter}
                onChange={setFilter}
                onReset={() => setFilter({ page: 0, size: PAGE_SIZE })}
                tenantOptions={tenantOptions}
              />
            }
            toolbarRight={
              <PermissionGate permission={PERMISSIONS.EXPORT_GENERAL}>
                <AuditCsvExportButton events={rows} />
              </PermissionGate>
            }
            caption={t('audit.title')}
            pageSize={PAGE_SIZE}
          />
        )}
      </Card>

      <AuditDetailsDrawer
        event={drawerEvent}
        open={drawerEvent !== null}
        onClose={() => setDrawerEvent(null)}
      />
    </div>
  );
}
