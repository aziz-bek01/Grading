import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Plus, Pencil, Archive, FilePlus2, BookmarkPlus } from 'lucide-react';
import { Breadcrumbs } from '@/shared/components/layout/Breadcrumbs';
import { DataTable } from '@/shared/components/data-table/DataTable';
import { FilterBar } from '@/shared/components/data-table/FilterBar';
import { Button } from '@/shared/components/ui/Button';
import { PermissionGate } from '@/shared/components/access/PermissionGate';
import { ConfirmDialog } from '@/shared/components/confirm-dialog/ConfirmDialog';
import { ReasonRequiredDialog } from '@/shared/components/confirm-dialog/ReasonRequiredDialog';
import { PERMISSIONS } from '@/shared/types/permissions';
import { useAuthStore } from '@/features/auth/authStore';
import { MethodologyTypeBadge } from '../components/MethodologyTypeBadge';
import { MethodologyStatusBadge } from '../components/MethodologyStatusBadge';
import {
  MethodologyTemplatePicker,
  type TemplateSelection,
} from '../components/MethodologyTemplatePicker';
import {
  MethodologyMetadataDrawer,
  type MethodologyMetadataPatch,
} from '../components/MethodologyMetadataDrawer';
import { MethodologyCreateDrawer } from '../components/MethodologyCreateDrawer';
import { SaveAsTemplateDrawer } from '../components/SaveAsTemplateDrawer';
import { RenameTemplateDrawer } from '../components/RenameTemplateDrawer';
import {
  useArchiveCustomTemplate,
  useArchiveMethodology,
  useCreateMethodology,
  useCreateMethodologyFromTemplate,
  useMethodologies,
  useSaveMethodologyAsTemplate,
  useUpdateCustomTemplate,
  useUpdateMethodology,
} from '../hooks/useMethodology';
import type { Locale, LocalizedString } from '@/shared/types/common';
import type {
  Methodology,
  MethodologyCreatePayload,
  MethodologyTemplate,
  SaveAsTemplatePayload,
  UpdateTemplatePayload,
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
  // Epic E — save-as-template + manage custom templates.
  const [saveTemplateTarget, setSaveTemplateTarget] = useState<Methodology | null>(null);
  const [renameTemplateTarget, setRenameTemplateTarget] = useState<MethodologyTemplate | null>(null);
  const [archiveTemplateTarget, setArchiveTemplateTarget] = useState<MethodologyTemplate | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const createFromTemplateMut = useCreateMethodologyFromTemplate(projectId);
  const createFromScratchMut = useCreateMethodology(projectId);
  const updateMut = useUpdateMethodology(editTarget?.id ?? '', projectId);
  const archiveMut = useArchiveMethodology(archiveTarget?.id ?? '', projectId);
  const saveTemplateMut = useSaveMethodologyAsTemplate(saveTemplateTarget?.id ?? '');
  const renameTemplateMut = useUpdateCustomTemplate(renameTemplateTarget?.id ?? '');
  const archiveTemplateMut = useArchiveCustomTemplate(archiveTemplateTarget?.id ?? '');

  // Auto-dismiss the success banner so it behaves like a transient toast.
  useEffect(() => {
    if (!successMessage) return;
    const id = window.setTimeout(() => setSuccessMessage(null), 5000);
    return () => window.clearTimeout(id);
  }, [successMessage]);

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

  // Template flow. The empty-from-scratch CUSTOM option routes to the
  // from-scratch drawer; built-in AND tenant CUSTOM templates instantiate a
  // snapshot via /methodologies/from-template (the backend accepts custom codes).
  const handleTemplateSelect = async (selection: TemplateSelection) => {
    setPickerOpen(false);
    // The empty-from-scratch path is the built-in CUSTOM option (no snapshot).
    if (selection.code === 'CUSTOM' && !selection.isCustom) {
      setCreateOpen(true);
      return;
    }
    const code = `M-${Date.now().toString(36).toUpperCase()}`;
    const defaultName =
      selection.kind === 'CLASSIC_8_FACTOR'
        ? t('methodology.template_picker.default_name_classic')
        : selection.kind === 'EXTENDED_11_CRITERIA'
          ? t('methodology.template_picker.default_name_extended')
          : t('methodology.template_picker.default_name_custom');
    const created = await createFromTemplateMut.mutateAsync({
      project_id: projectId,
      code,
      name_i18n: { 'ru-RU': defaultName },
      methodology_type: selection.kind,
      // Send the concrete template code (built-in registry OR tenant CUSTOM).
      source_template_code: selection.code,
    });
    deepLinkToVersion(created);
  };

  // Epic E — snapshot a methodology into a reusable CUSTOM template.
  const handleSaveAsTemplate = async (payload: SaveAsTemplatePayload) => {
    await saveTemplateMut.mutateAsync(payload);
    setSaveTemplateTarget(null);
    setSuccessMessage(
      t('methodology.save_as_template.success', { code: payload.code }),
    );
  };

  const handleRenameTemplate = async (payload: UpdateTemplatePayload) => {
    await renameTemplateMut.mutateAsync(payload);
    setRenameTemplateTarget(null);
    setSuccessMessage(t('methodology.manage_templates.rename_success'));
  };

  // From-scratch flow (F7). Errors (duplicate code) bubble back to the drawer.
  const handleCreateFromScratch = async (payload: MethodologyCreatePayload) => {
    const created = await createFromScratchMut.mutateAsync(payload);
    setCreateOpen(false);
    deepLinkToVersion(created);
  };

  // List-page mode: only the container metadata (name/description) is editable —
  // the drawer is invoked WITHOUT `version`/`editable`, so `patch.version` is
  // always undefined here.
  const handleUpdateMetadata = async (patch: MethodologyMetadataPatch) => {
    await updateMut.mutateAsync(patch.methodology);
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
        {successMessage ? (
          <div
            role="status"
            className="w-full order-last rounded-md border border-success-500/30 bg-success-50 px-4 py-3 text-sm text-success-700"
            data-testid="methodology-template-success"
          >
            {successMessage}
          </div>
        ) : null}
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
                <PermissionGate permission={PERMISSIONS.METHODOLOGY_CREATE}>
                  <Button
                    variant="ghost"
                    size="sm"
                    aria-label={t('methodology.save_as_template.action')}
                    title={t('methodology.save_as_template.action')}
                    data-testid={`methodology-${m.code}-save-template`}
                    onClick={() => setSaveTemplateTarget(m)}
                  >
                    <BookmarkPlus size={14} />
                  </Button>
                </PermissionGate>
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
            width: '22%',
            className: 'text-right',
          },
        ]}
      />

      <MethodologyTemplatePicker
        open={pickerOpen}
        onCancel={() => setPickerOpen(false)}
        onSelect={handleTemplateSelect}
        onRenameTemplate={(tpl) => {
          setPickerOpen(false);
          setRenameTemplateTarget(tpl);
        }}
        onArchiveTemplate={(tpl) => {
          setPickerOpen(false);
          setArchiveTemplateTarget(tpl);
        }}
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

      {/* Epic E — save methodology as reusable CUSTOM template. */}
      <SaveAsTemplateDrawer
        open={!!saveTemplateTarget}
        methodology={saveTemplateTarget}
        onClose={() => setSaveTemplateTarget(null)}
        onSubmit={handleSaveAsTemplate}
      />

      {/* Epic E — rename a CUSTOM template (built-ins never reach here). */}
      <RenameTemplateDrawer
        open={!!renameTemplateTarget}
        template={renameTemplateTarget}
        onClose={() => setRenameTemplateTarget(null)}
        onSubmit={handleRenameTemplate}
      />

      {/* Epic E — archive a CUSTOM template (removes it from the picker). */}
      <ConfirmDialog
        open={!!archiveTemplateTarget}
        destructive
        title={t('methodology.manage_templates.archive_confirm_title')}
        body={t('methodology.manage_templates.archive_confirm_body')}
        confirmLabel={t('methodology.manage_templates.archive')}
        onCancel={() => setArchiveTemplateTarget(null)}
        onConfirm={async () => {
          const target = archiveTemplateTarget;
          setArchiveTemplateTarget(null);
          if (target?.id) {
            await archiveTemplateMut.mutateAsync();
            setSuccessMessage(t('methodology.manage_templates.archive_success'));
          }
        }}
      />
    </div>
  );
}
