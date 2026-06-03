/**
 * /app/clients/:tenantId — single tenant detail view.
 *
 * Sections:
 *   - Header (display name, slug, status, fingerprint avatar) + actions
 *   - KPI cards (project / user / activity / last-activity)
 *   - Client-company (legal entity) panel
 *   - Quick links: open audit (?tenantId=), open projects, manage users
 *   - Drawers: EditTenant, EditClient, ArchiveTenant
 */
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate, useParams } from 'react-router-dom';
import {
  ArrowLeft,
  Building2,
  ExternalLink,
  FolderKanban,
  Pencil,
  ScrollText,
  Users,
  Archive,
} from 'lucide-react';
import { Button } from '@/shared/components/ui/Button';
import { Card } from '@/shared/components/ui/Card';
import { Breadcrumbs } from '@/shared/components/layout/Breadcrumbs';
import { ErrorState } from '@/shared/components/feedback/ErrorState';
import { LoadingState } from '@/shared/components/feedback/LoadingState';
import { NoAccessState } from '@/shared/components/feedback/NoAccessState';
import { PermissionGate } from '@/shared/components/access/PermissionGate';
import { PERMISSIONS } from '@/shared/types/permissions';
import { routes } from '@/shared/config/routes';
import { ApiError } from '@/shared/api/apiError';
import { useTenant } from '../hooks/useTenant';
import { useTenantStats } from '../hooks/useTenantStats';
import {
  useArchiveTenant,
  useUpdateClient,
  useUpdateTenant,
} from '../hooks/useTenantMutations';
import { TenantStatsCards } from '../components/TenantStatsCards';
import { TenantStatusBadge } from '../components/TenantStatusBadge';
import { EditTenantDialog } from '../components/EditTenantDialog';
import { EditClientDialog } from '../components/EditClientDialog';
import { ArchiveTenantConfirm } from '../components/ArchiveTenantConfirm';

export function ClientDetailsPage() {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const { tenantId } = useParams<{ tenantId: string }>();

  const { data: tenant, isLoading, error, refetch } = useTenant(tenantId);
  // Stats endpoint is a separate hook so we can refetch it after archive
  // without re-pulling the full detail payload.
  const { data: stats, isLoading: statsLoading } = useTenantStats(tenantId);

  const updateTenantMutation = useUpdateTenant(tenantId ?? '');
  const updateClientMutation = useUpdateClient(tenant?.client_company.id ?? '');
  const archiveTenantMutation = useArchiveTenant(tenantId ?? '');

  const [editTenantOpen, setEditTenantOpen] = useState(false);
  const [editClientOpen, setEditClientOpen] = useState(false);
  const [archiveOpen, setArchiveOpen] = useState(false);

  if (isLoading) {
    return (
      <div className="space-y-6">
        <Breadcrumbs extra={[{ label: t('nav.clients'), to: routes.clients }]} />
        <LoadingState />
      </div>
    );
  }

  if (error) {
    const isForbidden = error instanceof ApiError && (error.status === 403 || error.status === 404);
    return (
      <div className="space-y-6">
        <Breadcrumbs extra={[{ label: t('nav.clients'), to: routes.clients }]} />
        {isForbidden ? <NoAccessState /> : <ErrorState onRetry={() => refetch()} />}
      </div>
    );
  }

  if (!tenant) return null;
  const isArchived = tenant.status === 'ARCHIVED';

  return (
    <div className="space-y-6">
      <Breadcrumbs
        extra={[
          { label: t('nav.clients'), to: routes.clients },
          { label: tenant.display_name },
        ]}
      />

      <div>
        <Button
          variant="ghost"
          size="sm"
          leadingIcon={<ArrowLeft size={14} />}
          onClick={() => navigate(routes.clients)}
        >
          {t('common.back')}
        </Button>
      </div>

      <Card>
        <div className="flex items-start justify-between gap-4 flex-wrap">
          <div className="flex items-start gap-4">
            <span
              className="inline-flex h-14 w-14 items-center justify-center rounded-lg text-lg font-semibold text-text-inverse"
              style={{ backgroundColor: `hsl(${tenant.fingerprint_hue} 60% 45%)` }}
              aria-hidden
            >
              {tenant.display_name.slice(0, 2).toUpperCase()}
            </span>
            <div>
              <h1 className="text-2xl text-text-primary">{tenant.display_name}</h1>
              <div className="mt-2 flex flex-wrap items-center gap-3 text-sm text-text-secondary">
                <span className="font-mono text-xs">{tenant.slug}</span>
                <TenantStatusBadge status={tenant.status} />
                <span>
                  {t('clients.detail.updated_at', {
                    date: new Date(tenant.updated_at).toLocaleString(i18n.language),
                  })}
                </span>
                <span>
                  {t('clients.detail.default_locale', {
                    locale: t(`language.${tenant.default_locale}`),
                  })}
                </span>
              </div>
            </div>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <PermissionGate permission={PERMISSIONS.AUDIT_READ}>
              <Button
                variant="secondary"
                size="sm"
                leadingIcon={<ScrollText size={14} />}
                onClick={() => navigate(`${routes.audit}?tenantId=${tenant.id}`)}
              >
                {t('clients.detail.open_audit')}
              </Button>
            </PermissionGate>
            <PermissionGate permission={PERMISSIONS.PROJECT_READ}>
              <Button
                variant="secondary"
                size="sm"
                leadingIcon={<FolderKanban size={14} />}
                onClick={() => navigate(routes.projects)}
              >
                {t('clients.detail.open_projects')}
              </Button>
            </PermissionGate>
            <PermissionGate permission={[PERMISSIONS.USER_READ, PERMISSIONS.USER_ACCESS_MANAGE]} mode="any">
              <Button
                variant="secondary"
                size="sm"
                leadingIcon={<Users size={14} />}
                onClick={() => navigate(routes.usersAccess)}
              >
                {t('clients.detail.open_users')}
              </Button>
            </PermissionGate>
            <PermissionGate permission={PERMISSIONS.TENANT_EDIT}>
              <Button
                variant="primary"
                size="sm"
                leadingIcon={<Pencil size={14} />}
                onClick={() => setEditTenantOpen(true)}
                disabled={isArchived}
                data-testid="edit-tenant-button"
              >
                {t('clients.detail.edit_tenant')}
              </Button>
            </PermissionGate>
            <PermissionGate permission={PERMISSIONS.TENANT_ARCHIVE}>
              <Button
                variant="danger"
                size="sm"
                leadingIcon={<Archive size={14} />}
                onClick={() => setArchiveOpen(true)}
                disabled={isArchived}
                data-testid="archive-tenant-button"
              >
                {t('clients.detail.archive_tenant')}
              </Button>
            </PermissionGate>
          </div>
        </div>
      </Card>

      <TenantStatsCards stats={stats} loading={statsLoading} />

      <Card
        title={
          <span className="inline-flex items-center gap-2">
            <Building2 size={18} aria-hidden />
            {t('clients.detail.client_panel_title')}
          </span>
        }
        subtitle={t('clients.detail.client_panel_subtitle')}
        action={
          <PermissionGate permission={PERMISSIONS.CLIENT_UPDATE}>
            <Button
              variant="secondary"
              size="sm"
              leadingIcon={<Pencil size={14} />}
              onClick={() => setEditClientOpen(true)}
              disabled={isArchived}
              data-testid="edit-client-button"
            >
              {t('clients.detail.edit_client')}
            </Button>
          </PermissionGate>
        }
      >
        <dl className="grid grid-cols-1 md:grid-cols-2 gap-x-6 gap-y-3 text-sm">
          <DescriptionRow label={t('clients.field.legal_name')} value={tenant.client_company.legal_name} />
          <DescriptionRow label={t('clients.field.brand_name')} value={tenant.client_company.brand_name} />
          <DescriptionRow
            label={t('clients.field.industry')}
            value={tenant.client_company.industry ?? '—'}
          />
          <DescriptionRow
            label={t('clients.field.country_code')}
            value={tenant.client_company.country_code ?? '—'}
          />
          <DescriptionRow
            label={t('clients.field.tax_id')}
            value={tenant.client_company.tax_id ?? '—'}
          />
        </dl>
      </Card>

      <Card
        title={
          <span className="inline-flex items-center gap-2">
            <ExternalLink size={18} aria-hidden />
            {t('clients.detail.related_title')}
          </span>
        }
        subtitle={t('clients.detail.related_subtitle')}
      >
        <ul className="text-sm text-text-secondary space-y-2">
          <li>
            {t('clients.detail.related_projects', {
              count: stats?.project_count ?? 0,
            })}
          </li>
          <li>
            {t('clients.detail.related_users', {
              count: stats?.user_count ?? 0,
            })}
          </li>
          <li className="text-text-muted">{t('clients.detail.related_more_soon')}</li>
        </ul>
      </Card>

      <EditTenantDialog
        open={editTenantOpen}
        tenant={tenant}
        onClose={() => setEditTenantOpen(false)}
        onSubmit={async (payload) => {
          await updateTenantMutation.mutateAsync(payload);
        }}
      />

      <EditClientDialog
        open={editClientOpen}
        client={tenant.client_company}
        onClose={() => setEditClientOpen(false)}
        onSubmit={async (payload) => {
          await updateClientMutation.mutateAsync(payload);
        }}
      />

      <ArchiveTenantConfirm
        open={archiveOpen}
        tenantName={tenant.display_name}
        onCancel={() => setArchiveOpen(false)}
        onConfirm={async (reason) => {
          await archiveTenantMutation.mutateAsync({ reason });
          setArchiveOpen(false);
        }}
      />
    </div>
  );
}

function DescriptionRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex flex-col">
      <dt className="text-xs uppercase tracking-wide text-text-muted">{label}</dt>
      <dd className="text-text-primary mt-0.5 break-all">{value}</dd>
    </div>
  );
}
