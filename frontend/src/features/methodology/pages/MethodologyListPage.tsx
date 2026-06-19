import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Plus, Pencil, Archive, FilePlus2, BookmarkPlus, RotateCcw } from 'lucide-react';
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
import { MethodologyContainerStatusBadge } from '../components/MethodologyContainerStatusBadge';
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
  useInProgressCount,
  useMethodologies,
  useRestoreMethodology,
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
  const [showArchived, setShowArchived] = useState(false);
  const [pickerOpen, setPickerOpen] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<Methodology | null>(null);
  const [archiveTarget, setArchiveTarget] = useState<Methodology | null>(null);
  const [restoreTarget, setRestoreTarget] = useState<Methodology | null>(null);
  // Epic E — save-as-template + manage custom templates.
  const [saveTemplateTarget, setSaveTemplateTarget] = useState<Methodology | null>(null);
  const [renameTemplateTarget, setRenameTemplateTarget] = useState<MethodologyTemplate | null>(null);
  const [archiveTemplateTarget, setArchiveTemplateTarget] = useState<MethodologyTemplate | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const createFromTemplateMut = useCreateMethodologyFromTemplate(projectId);
  const createFromScratchMut = useCreateMethodology(projectId);
  const updateMut = useUpdateMethodology(editTarget?.id ?? '', projectId);
  const archiveMut = useArchiveMethodology(archiveTarget?.id ?? '', projectId);
  const restoreMut = useRestoreMethodology(restoreTarget?.id ?? '', projectId);
  const saveTemplateMut = useSaveMethodologyAsTemplate(saveTemplateTarget?.id ?? '');
  const renameTemplateMut = useUpdateCustomTemplate(renameTemplateTarget?.id ?? '');
  const archiveTemplateMut = useArchiveCustomTemplate(archiveTemplateTarget?.id ?? '');

  // Lazy in-progress count query — only fetches when the deactivate dialog opens.
  const inProgressCountQuery = useInProgressCount(archiveTarget?.id, {
    enabled: !!archiveTarget,
  });

  // Auto-dismiss the success banner so it behaves like a transient toast.
  useEffect(() => {
    if (!successMessage) return;
    const id = window.setTimeout(() => setSuccessMessage(null), 5000);
    return () => window.clearTimeout(id);
  }, [successMessage]);

  const filtered = useMemo(() => {
    // Managers can toggle to see archived (ARCHIVED) methodologies for reactivation.
    // By default, only ACTIVE methodologies are shown.
    const scoped = showArchived ? items : items.filter((m) => m.status !== 'ARCHIVED');
    return typeFilter ? scoped.filter((m) => m.methodology_type === typeFilter) : scoped;
  }, [items, typeFilter, showArchived]);

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
        <div className="flex items-center gap-2 flex-wrap">
          <PermissionGate permission={PERMISSIONS.METHODOLOGY_EDIT}>
            <label className="inline-flex items-center gap-1.5 text-sm text-text-secondary cursor-pointer">
              <input
                type="checkbox"
                checked={showArchived}
                onChange={(e) => setShowArchived(e.target.checked)}
                data-testid="methodology-show-archived"
                className="h-4 w-4 accent-primary-500"
              />
              {t('methodology.show_inactive')}
            </label>
          </PermissionGate>
          <PermissionGate permission={PERMISSIONS.METHODOLOGY_CREATE}>
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
          </PermissionGate>
        </div>
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
            width: '10%',
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
            width: '14%',
          },
          {
            key: 'container_status',
            header: t('common.status'),
            render: (m) => <MethodologyContainerStatusBadge status={m.status} />,
            width: '10%',
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
            width: '18%',
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
            width: '10%',
            sortable: true,
            sortAccessor: (m) => m.updated_at ?? m.created_at ?? '',
          },
          {
            key: 'actions',
            header: '',
            render: (m) => (
              <div className="flex items-center justify-end gap-1">
                <PermissionGate permission={PERMISSIONS.METHODOLOGY_CREATE}>
                  {m.status !== 'ARCHIVED' ? (
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
                  ) : null}
                </PermissionGate>
                <PermissionGate permission={PERMISSIONS.METHODOLOGY_EDIT}>
                  {m.status !== 'ARCHIVED' ? (
                    <>
                      <Button
                        variant="ghost"
                        size="sm"
                        aria-label={t('common.edit')}
                        data-testid={`methodology-${m.code}-edit`}
                        onClick={() => setEditTarget(m)}
                      >
                        <Pencil size={14} />
                      </Button>
                      {/* Deactivate (ACTIVE → ARCHIVED) */}
                      <Button
                        variant="ghost"
                        size="sm"
                        aria-label={t('methodology.deactivate.action')}
                        title={t('methodology.deactivate.action')}
                        data-testid={`methodology-${m.code}-deactivate`}
                        onClick={() => setArchiveTarget(m)}
                      >
                        <Archive size={14} className="text-danger-700" />
                      </Button>
                    </>
                  ) : (
                    /* Reactivate (ARCHIVED → ACTIVE) */
                    <Button
                      variant="ghost"
                      size="sm"
                      aria-label={t('methodology.reactivate.action')}
                      title={t('methodology.reactivate.action')}
                      data-testid={`methodology-${m.code}-reactivate`}
                      onClick={() => setRestoreTarget(m)}
                    >
                      <RotateCcw size={14} className="text-success-700" />
                    </Button>
                  )}
                </PermissionGate>
                {m.status !== 'ARCHIVED' ? (
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
                ) : null}
              </div>
            ),
            width: '24%',
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

      {/* PART 2 — Deactivate (ACTIVE → ARCHIVED) methodology dialog */}
      <ReasonRequiredDialog
        open={!!archiveTarget}
        title={
          archiveTarget
            ? t('methodology.deactivate.title', {
                name: nameInLocale(archiveTarget.name_i18n, currentUserLocale),
              })
            : ''
        }
        body={
          inProgressCountQuery.isLoading
            ? t('common.loading')
            : t('methodology.deactivate.body', {
                count: inProgressCountQuery.data ?? 0,
              })
        }
        onCancel={() => setArchiveTarget(null)}
        onConfirm={async (reason) => {
          // Do NOT clear archiveTarget before the mutation: archiveMut is bound to
          // archiveTarget?.id at render time, so resetting first re-renders the hook
          // with an empty id and the POST goes to /methodologies//archive (401).
          // Mutate first (target id still set), then close on success.
          if (!archiveTarget) return;
          try {
            await archiveMut.mutateAsync({ reason });
            setArchiveTarget(null);
          } catch {
            // Keep the dialog open so the failure is not silent.
          }
        }}
      />

      {/* PART 2 — Reactivate (ARCHIVED → ACTIVE) methodology dialog */}
      <ReasonRequiredDialog
        open={!!restoreTarget}
        title={
          restoreTarget
            ? t('methodology.reactivate.title', {
                name: nameInLocale(restoreTarget.name_i18n, currentUserLocale),
              })
            : ''
        }
        body={t('methodology.reactivate.body')}
        onCancel={() => setRestoreTarget(null)}
        onConfirm={async (reason) => {
          // Same fix as deactivate: restoreMut is bound to restoreTarget?.id, so
          // mutate BEFORE clearing the target (else the POST hits /methodologies//restore).
          if (!restoreTarget) return;
          try {
            await restoreMut.mutateAsync({ reason });
            setRestoreTarget(null);
          } catch {
            // Keep the dialog open so the failure is not silent.
          }
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
