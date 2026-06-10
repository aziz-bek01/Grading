import { useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Plus, Pencil, Archive, FilePlus2 } from 'lucide-react';
import { Breadcrumbs } from '@/shared/components/layout/Breadcrumbs';
import { DataTable } from '@/shared/components/data-table/DataTable';
import { FilterBar } from '@/shared/components/data-table/FilterBar';
import { Button } from '@/shared/components/ui/Button';
import { PermissionGate } from '@/shared/components/access/PermissionGate';
import { ReasonRequiredDialog } from '@/shared/components/confirm-dialog/ReasonRequiredDialog';
import { PERMISSIONS } from '@/shared/types/permissions';
import { useAuthStore } from '@/features/auth/authStore';
import { MethodologyTypeBadge } from '../components/MethodologyTypeBadge';
import { MethodologyStatusBadge } from '../components/MethodologyStatusBadge';
import { MethodologyTemplatePicker } from '../components/MethodologyTemplatePicker';
import { MethodologyMetadataDrawer } from '../components/MethodologyMetadataDrawer';
import { MethodologyCreateDrawer } from '../components/MethodologyCreateDrawer';
import {
  useArchiveMethodology,
  useCreateMethodology,
  useCreateMethodologyFromTemplate,
  useMethodologies,
  useUpdateMethodology,
} from '../hooks/useMethodology';
import type { Locale, LocalizedString } from '@/shared/types/common';
import type {
  Methodology,
  MethodologyCreatePayload,
  MethodologyType,
  MethodologyUpdatePayload,
} from '../types';

function nameInLocale(value: LocalizedString | undefined, locale: Locale) {
  if (!value) return '—';
  return value[locale] ?? value['ru-RU'] ?? value['en-US'] ?? '—';
}

export function MethodologyListPage() {
  const { t, i18n } = useTranslation();
  const { projectId = '' } = useParams<{ projectId: string }>();
  const navigate = useNavigate();
  const currentUserLocale = useAuthStore((s) => s.user?.locale) ?? (i18n.language as Locale);

  const query = useMethodologies(projectId);
  const items = useMemo(() => query.data?.items ?? [], [query.data]);

  const [typeFilter, setTypeFilter] = useState<string | null>(null);
  const [pickerOpen, setPickerOpen] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<Methodology | null>(null);
  const [archiveTarget, setArchiveTarget] = useState<Methodology | null>(null);

  const createFromTemplateMut = useCreateMethodologyFromTemplate(projectId);
  const createFromScratchMut = useCreateMethodology(projectId);
  const updateMut = useUpdateMethodology(editTarget?.id ?? '', projectId);
  const archiveMut = useArchiveMethodology(archiveTarget?.id ?? '', projectId);

  const filtered = useMemo(() => {
    // Archived containers are soft-deleted — keep them out of the active list (F6).
    const active = items.filter((m) => m.status !== 'ARCHIVED');
    return typeFilter ? active.filter((m) => m.methodology_type === typeFilter) : active;
  }, [items, typeFilter]);

  const deepLinkToVersion = (created: Methodology) => {
    if (created.latest_version_id) {
      navigate(
        `/app/projects/${projectId}/methodology/${created.id}/versions/${created.latest_version_id}/edit`,
      );
    } else {
      void query.refetch();
      navigate(`/app/projects/${projectId}/methodology`);
    }
  };

  // Template flow (CLASSIC / EXTENDED). CUSTOM routes to the from-scratch drawer.
  const handleTemplateSelect = async (type: MethodologyType) => {
    setPickerOpen(false);
    if (type === 'CUSTOM') {
      setCreateOpen(true);
      return;
    }
    const code = `M-${Date.now().toString(36).toUpperCase()}`;
    const created = await createFromTemplateMut.mutateAsync({
      project_id: projectId,
      code,
      name_i18n: {
        'ru-RU':
          type === 'CLASSIC_8_FACTOR'
            ? t('methodology.template_picker.default_name_classic')
            : t('methodology.template_picker.default_name_extended'),
      },
      methodology_type: type,
      source_template_code: type,
    });
    deepLinkToVersion(created);
  };

  // From-scratch flow (F7). Errors (duplicate code) bubble back to the drawer.
  const handleCreateFromScratch = async (payload: MethodologyCreatePayload) => {
    const created = await createFromScratchMut.mutateAsync(payload);
    setCreateOpen(false);
    deepLinkToVersion(created);
  };

  const handleUpdateMetadata = async (patch: MethodologyUpdatePayload) => {
    await updateMut.mutateAsync(patch);
    setEditTarget(null);
  };

  return (
    <div className="space-y-6">
      <Breadcrumbs extra={[{ label: t('nav.methodology') }]} />
      <header className="flex items-start justify-between gap-4 flex-wrap">
        <div>
          <h1 className="text-2xl text-text-primary">{t('methodology.list_page_title')}</h1>
          <p className="text-sm text-text-secondary mt-1">{t('methodology.list_page_subtitle')}</p>
        </div>
        <PermissionGate permission={PERMISSIONS.METHODOLOGY_CREATE}>
          <div className="flex items-center gap-2">
            <Button
              variant="secondary"
              onClick={() => setCreateOpen(true)}
              leadingIcon={<FilePlus2 size={16} />}
              data-testid="methodology-list-from-scratch"
            >
              {t('methodology.create.from_scratch')}
            </Button>
            <Button
              onClick={() => setPickerOpen(true)}
              leadingIcon={<Plus size={16} />}
              data-testid="methodology-list-new"
            >
              {t('methodology.new_methodology')}
            </Button>
          </div>
        </PermissionGate>
      </header>

      <DataTable<Methodology>
        rows={filtered}
        rowKey={(m) => m.id}
        loading={query.isLoading}
        searchPredicate={(m, q) =>
          (m.code ?? '').toLowerCase().includes(q) ||
          Object.values(m.name_i18n ?? {}).some((v) => (v as string).toLowerCase().includes(q))
        }
        filterBar={
          <FilterBar
            filters={[
              {
                key: 'type',
                label: t('methodology.filter.type'),
                value: typeFilter,
                onChange: setTypeFilter,
                options: [
                  { value: 'CLASSIC_8_FACTOR', label: t('methodology.type.classic_8_factor') },
                  { value: 'EXTENDED_11_CRITERIA', label: t('methodology.type.extended_11_criteria') },
                  { value: 'CUSTOM', label: t('methodology.type.custom') },
                ],
              },
            ]}
            onReset={() => setTypeFilter(null)}
          />
        }
        emptyTitle={t('methodology.empty_title')}
        emptyBody={t('methodology.empty_body')}
        columns={[
          {
            key: 'code',
            header: t('common.code'),
            render: (m) => <span className="font-mono text-xs text-text-secondary">{m.code}</span>,
            width: '12%',
            sortable: true,
            sortAccessor: (m) => m.code,
          },
          {
            key: 'name',
            header: t('common.name'),
            render: (m) => (
              <span className="text-text-primary font-medium">
                {nameInLocale(m.name_i18n, currentUserLocale)}
              </span>
            ),
            sortable: true,
            sortAccessor: (m) => nameInLocale(m.name_i18n, currentUserLocale),
          },
          {
            key: 'type',
            header: t('common.type'),
            render: (m) => <MethodologyTypeBadge type={m.methodology_type} />,
            width: '16%',
          },
          {
            key: 'active_version',
            header: t('methodology.column.active_version'),
            render: (m) =>
              m.active_version_status ? (
                <span className="inline-flex items-center gap-2">
                  <span className="text-text-secondary tabular-nums text-xs">
                    {t('methodology.version_label', { number: m.active_version_number ?? '?' })}
                  </span>
                  <MethodologyStatusBadge status={m.active_version_status} />
                </span>
              ) : (
                <span className="text-text-muted text-xs">{t('methodology.no_active_version')}</span>
              ),
            width: '22%',
          },
          {
            key: 'updated_at',
            header: t('methodology.column.updated_at'),
            render: (m) => {
              const ts = m.updated_at ?? m.created_at;
              return (
                <span className="text-xs text-text-secondary tabular-nums">
                  {ts ? new Date(ts).toLocaleDateString(currentUserLocale) : ''}
                </span>
              );
            },
            width: '12%',
            sortable: true,
            sortAccessor: (m) => m.updated_at ?? m.created_at ?? '',
          },
          {
            key: 'actions',
            header: '',
            render: (m) => (
              <div className="flex items-center justify-end gap-1">
                <PermissionGate permission={PERMISSIONS.METHODOLOGY_EDIT}>
                  <Button
                    variant="ghost"
                    size="sm"
                    aria-label={t('common.edit')}
                    data-testid={`methodology-${m.code}-edit`}
                    onClick={() => setEditTarget(m)}
                  >
                    <Pencil size={14} />
                  </Button>
                  <Button
                    variant="ghost"
                    size="sm"
                    aria-label={t('common.archive')}
                    data-testid={`methodology-${m.code}-archive`}
                    onClick={() => setArchiveTarget(m)}
                  >
                    <Archive size={14} className="text-danger-700" />
                  </Button>
                </PermissionGate>
                <Button
                  variant="ghost"
                  size="sm"
                  data-testid={`methodology-${m.code}-open`}
                  onClick={() =>
                    m.latest_version_id &&
                    navigate(
                      `/app/projects/${projectId}/methodology/${m.id}/versions/${m.latest_version_id}/edit`,
                    )
                  }
                >
                  {t('methodology.open')}
                </Button>
              </div>
            ),
            width: '18%',
            className: 'text-right',
          },
        ]}
      />

      <MethodologyTemplatePicker
        open={pickerOpen}
        onCancel={() => setPickerOpen(false)}
        onSelect={handleTemplateSelect}
      />

      <MethodologyCreateDrawer
        open={createOpen}
        projectId={projectId}
        onClose={() => setCreateOpen(false)}
        onSubmit={handleCreateFromScratch}
      />

      <MethodologyMetadataDrawer
        open={!!editTarget}
        methodology={editTarget}
        onClose={() => setEditTarget(null)}
        onSubmit={handleUpdateMetadata}
      />

      <ReasonRequiredDialog
        open={!!archiveTarget}
        title={t('methodology.confirm.archive_methodology_title')}
        body={t('methodology.confirm.archive_methodology_body')}
        onCancel={() => setArchiveTarget(null)}
        onConfirm={async (reason) => {
          const target = archiveTarget;
          setArchiveTarget(null);
          if (target) await archiveMut.mutateAsync({ reason });
        }}
      />
    </div>
  );
}
