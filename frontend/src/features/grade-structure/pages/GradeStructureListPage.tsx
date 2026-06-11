import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Archive, BookmarkPlus, Pencil, Plus, Trash2 } from 'lucide-react';
import { Breadcrumbs } from '@/shared/components/layout/Breadcrumbs';
import { DataTable } from '@/shared/components/data-table/DataTable';
import { Button } from '@/shared/components/ui/Button';
import { ConfirmDialog } from '@/shared/components/confirm-dialog/ConfirmDialog';
import { PermissionGate } from '@/shared/components/access/PermissionGate';
import { PERMISSIONS } from '@/shared/types/permissions';
import { useAuthStore } from '@/features/auth/authStore';
import { pickLocalized } from '@/shared/lib/localized';
import { GradeStructureStatusBadge } from '../components/GradeStructureStatusBadge';
import { GradeStructureTypeBadge } from '../components/GradeStructureTypeBadge';
import {
  GradeStructureTemplatePicker,
  type GradeTemplateSelection,
} from '../components/GradeStructureTemplatePicker';
import { GradeStructureMetadataDrawer } from '../components/GradeStructureMetadataDrawer';
import { SaveAsGradeTemplateDrawer } from '../components/SaveAsGradeTemplateDrawer';
import { RenameGradeTemplateDrawer } from '../components/RenameGradeTemplateDrawer';
import {
  useArchiveGradeTemplate,
  useCreateFromTemplate,
  useDeleteGradeStructure,
  useGradeStructures,
  useSaveAsGradeTemplate,
  useUpdateGradeTemplate,
  useUpdateMetadata,
} from '../hooks/useGradeStructure';
import type { Locale } from '@/shared/types/common';
import type {
  GradeStructureListItem,
  GradeStructureTemplate,
  SaveAsGradeTemplatePayload,
  UpdateGradeTemplatePayload,
  UpdateMetadataPayload,
} from '../types';
import { routes } from '@/shared/config/routes';

/**
 * Generate a unique structure-code suffix. Kept module-level (outside render) so
 * the impure clock/RNG never runs during render (react-hooks/purity).
 */
function makeSuffix(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID().slice(0, 8).toUpperCase();
  }
  return Math.random().toString(36).slice(2, 10).toUpperCase();
}

export function GradeStructureListPage() {
  const { t, i18n } = useTranslation();
  const { projectId = '' } = useParams<{ projectId: string }>();
  const navigate = useNavigate();
  const currentUserLocale = (useAuthStore((s) => s.user?.locale) ??
    (i18n.language as Locale)) as Locale;

  const query = useGradeStructures({ projectId });
  const items = query.data?.items ?? [];

  const [pickerOpen, setPickerOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<GradeStructureListItem | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<GradeStructureListItem | null>(null);
  // Save-as-template (BE-9): the structure to snapshot is loaded by the drawer's
  // own detail query is not needed — save-as targets by id, name seeds from row.
  const [saveTemplateTarget, setSaveTemplateTarget] =
    useState<GradeStructureListItem | null>(null);
  const [renameTemplateTarget, setRenameTemplateTarget] =
    useState<GradeStructureTemplate | null>(null);
  const [archiveTemplateTarget, setArchiveTemplateTarget] =
    useState<GradeStructureTemplate | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const createMut = useCreateFromTemplate();
  const updateMut = useUpdateMetadata(editTarget?.id ?? '');
  const deleteMut = useDeleteGradeStructure(deleteTarget?.id ?? '');
  const saveTemplateMut = useSaveAsGradeTemplate(saveTemplateTarget?.id ?? '');
  const renameTemplateMut = useUpdateGradeTemplate(renameTemplateTarget?.id ?? '');
  const archiveTemplateMut = useArchiveGradeTemplate(archiveTemplateTarget?.id ?? '');

  useEffect(() => {
    if (!successMessage) return;
    const id = window.setTimeout(() => setSuccessMessage(null), 5000);
    return () => window.clearTimeout(id);
  }, [successMessage]);

  const handleTemplateSelect = async (selection: GradeTemplateSelection) => {
    setPickerOpen(false);
    const code = `GS-${makeSuffix()}`;
    // Seed a name in ALL FOUR locales so the structure is never blank in any UI
    // language (FE-3). Built-in default names use the i18n strings per language.
    const defaultName = (key14: string, key16: string, keyCustom: string) => ({
      'ru-RU': pick(selection.structureType, key14, key16, keyCustom, 'ru-RU'),
      'uz-Cyrl-UZ': pick(selection.structureType, key14, key16, keyCustom, 'uz-Cyrl-UZ'),
      'uz-Latn-UZ': pick(selection.structureType, key14, key16, keyCustom, 'uz-Latn-UZ'),
      'en-US': pick(selection.structureType, key14, key16, keyCustom, 'en-US'),
    });
    const created = await createMut.mutateAsync({
      template_code: selection.code,
      project_id: projectId,
      code,
      name_i18n: defaultName(
        'gradeStructure.template_picker.default_name_14',
        'gradeStructure.template_picker.default_name_16',
        'gradeStructure.template_picker.default_name_custom',
      ),
    });
    navigate(routes.projectGradeStructureEdit(projectId, created.id));
  };

  function pick(
    type: GradeTemplateSelection['structureType'],
    key14: string,
    key16: string,
    keyCustom: string,
    locale: Locale,
  ): string {
    const key = type === 'GRADE_14' ? key14 : type === 'GRADE_16' ? key16 : keyCustom;
    return t(key, { lng: locale });
  }

  const handleUpdateMetadata = async (patch: UpdateMetadataPayload) => {
    await updateMut.mutateAsync(patch);
    setEditTarget(null);
    setSuccessMessage(t('gradeStructure.metadata.save_success'));
  };

  const handleSaveAsTemplate = async (payload: SaveAsGradeTemplatePayload) => {
    await saveTemplateMut.mutateAsync(payload);
    setSaveTemplateTarget(null);
    setSuccessMessage(t('gradeStructure.save_as_template.success', { code: payload.code }));
  };

  const handleRenameTemplate = async (payload: UpdateGradeTemplatePayload) => {
    await renameTemplateMut.mutateAsync(payload);
    setRenameTemplateTarget(null);
    setSuccessMessage(t('gradeStructure.manage_templates.rename_success'));
  };

  return (
    <div className="space-y-6">
      <Breadcrumbs extra={[{ label: t('nav.grades') }]} />
      <header className="flex items-start justify-between gap-4 flex-wrap">
        <div>
          <h1 className="text-2xl text-text-primary">
            {t('gradeStructure.list_page_title')}
          </h1>
          <p className="text-sm text-text-secondary mt-1">
            {t('gradeStructure.list_page_subtitle')}
          </p>
        </div>
        {successMessage ? (
          <div
            role="status"
            className="w-full order-last rounded-md border border-success-500/30 bg-success-50 px-4 py-3 text-sm text-success-700"
            data-testid="grade-structure-success"
          >
            {successMessage}
          </div>
        ) : null}
        <PermissionGate permission={PERMISSIONS.GRADE_EDIT}>
          <Button
            onClick={() => setPickerOpen(true)}
            leadingIcon={<Plus size={16} />}
            data-testid="grade-structure-list-new"
          >
            {t('gradeStructure.new_structure')}
          </Button>
        </PermissionGate>
      </header>

      <DataTable<GradeStructureListItem>
        rows={items}
        rowKey={(s) => s.id}
        loading={query.isLoading}
        searchPredicate={(s, q) =>
          (s.code ?? '').toLowerCase().includes(q) ||
          Object.values(s.name_i18n ?? {}).some((v) =>
            (v as string).toLowerCase().includes(q),
          )
        }
        emptyTitle={t('gradeStructure.empty_title')}
        emptyBody={t('gradeStructure.empty_body')}
        columns={[
          {
            key: 'code',
            header: t('common.code'),
            render: (s) => (
              <span className="font-mono text-xs text-text-secondary">{s.code}</span>
            ),
            width: '12%',
            sortable: true,
            sortAccessor: (s) => s.code,
          },
          {
            key: 'name',
            header: t('common.name'),
            render: (s) => (
              <span className="text-text-primary font-medium">
                {pickLocalized(s.name_i18n, currentUserLocale) || '—'}
              </span>
            ),
            sortable: true,
            sortAccessor: (s) => pickLocalized(s.name_i18n, currentUserLocale),
          },
          {
            key: 'type',
            header: t('common.type'),
            render: (s) => <GradeStructureTypeBadge type={s.structure_type} />,
            width: '12%',
          },
          {
            key: 'status',
            header: t('common.status'),
            render: (s) => <GradeStructureStatusBadge status={s.status} />,
            width: '11%',
          },
          {
            key: 'grades_count',
            header: t('gradeStructure.column.grades_count'),
            render: (s) => (
              <span className="text-text-secondary tabular-nums text-xs">
                {s.grade_count ?? 0}
              </span>
            ),
            width: '8%',
          },
          {
            key: 'version',
            header: t('gradeStructure.column.version'),
            render: (s) => (
              <span className="text-text-secondary tabular-nums text-xs">
                {t('gradeStructure.version_label', { number: s.version_number })}
              </span>
            ),
            width: '10%',
          },
          {
            key: 'updated_at',
            header: t('gradeStructure.column.updated_at'),
            render: (s) => {
              const ts = s.updated_at ?? s.created_at;
              return (
                <span className="text-xs text-text-secondary tabular-nums">
                  {ts ? new Date(ts).toLocaleDateString(currentUserLocale) : '—'}
                </span>
              );
            },
            width: '12%',
            sortable: true,
            sortAccessor: (s) => s.updated_at ?? s.created_at ?? '',
          },
          {
            key: 'actions',
            header: '',
            render: (s) => (
              <div className="flex items-center justify-end gap-1">
                <PermissionGate permission={PERMISSIONS.GRADE_EDIT}>
                  <Button
                    variant="ghost"
                    size="sm"
                    aria-label={t('gradeStructure.save_as_template.action')}
                    title={t('gradeStructure.save_as_template.action')}
                    data-testid={`grade-structure-${s.code}-save-template`}
                    onClick={() => setSaveTemplateTarget(s)}
                  >
                    <BookmarkPlus size={14} />
                  </Button>
                </PermissionGate>
                {s.status === 'DRAFT' ? (
                  <PermissionGate permission={PERMISSIONS.GRADE_EDIT}>
                    <Button
                      variant="ghost"
                      size="sm"
                      aria-label={t('common.edit')}
                      data-testid={`grade-structure-${s.code}-edit`}
                      onClick={() => setEditTarget(s)}
                    >
                      <Pencil size={14} />
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      aria-label={t('common.delete')}
                      data-testid={`grade-structure-${s.code}-delete`}
                      onClick={() => setDeleteTarget(s)}
                    >
                      <Trash2 size={14} className="text-danger-700" />
                    </Button>
                  </PermissionGate>
                ) : (
                  <PermissionGate permission={PERMISSIONS.GRADE_EDIT}>
                    <span
                      className="inline-flex items-center text-text-muted px-1.5"
                      title={t('gradeStructure.archive_only_hint')}
                      data-testid={`grade-structure-${s.code}-archive-only`}
                    >
                      <Archive size={14} />
                    </span>
                  </PermissionGate>
                )}
                <Button
                  variant="ghost"
                  size="sm"
                  data-testid={`grade-structure-${s.code}-open`}
                  onClick={() =>
                    navigate(routes.projectGradeStructureEdit(projectId, s.id))
                  }
                >
                  {t('gradeStructure.open')}
                </Button>
              </div>
            ),
            width: '20%',
            className: 'text-right',
          },
        ]}
      />

      <GradeStructureTemplatePicker
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

      <GradeStructureMetadataDrawer
        open={!!editTarget}
        structure={editTarget}
        onClose={() => setEditTarget(null)}
        onSubmit={handleUpdateMetadata}
      />

      {/* BE-9 — save structure as reusable CUSTOM template. */}
      <SaveAsGradeTemplateDrawer
        open={!!saveTemplateTarget}
        structure={
          saveTemplateTarget
            ? { ...saveTemplateTarget, grades: saveTemplateTarget.grades ?? [] }
            : null
        }
        onClose={() => setSaveTemplateTarget(null)}
        onSubmit={handleSaveAsTemplate}
      />

      {/* BE-9 — rename a CUSTOM template (built-ins never reach here). */}
      <RenameGradeTemplateDrawer
        open={!!renameTemplateTarget}
        template={renameTemplateTarget}
        onClose={() => setRenameTemplateTarget(null)}
        onSubmit={handleRenameTemplate}
      />

      {/* BE-4 — DRAFT-only hard delete. */}
      <ConfirmDialog
        open={!!deleteTarget}
        destructive
        title={t('gradeStructure.confirm.delete_title')}
        body={t('gradeStructure.confirm.delete_body')}
        confirmLabel={t('common.delete')}
        onCancel={() => setDeleteTarget(null)}
        onConfirm={async () => {
          const target = deleteTarget;
          setDeleteTarget(null);
          if (target) {
            await deleteMut.mutateAsync();
            setSuccessMessage(t('gradeStructure.confirm.delete_success'));
          }
        }}
      />

      {/* BE-9 — archive a CUSTOM template (removes it from the picker). */}
      <ConfirmDialog
        open={!!archiveTemplateTarget}
        destructive
        title={t('gradeStructure.manage_templates.archive_confirm_title')}
        body={t('gradeStructure.manage_templates.archive_confirm_body')}
        confirmLabel={t('gradeStructure.manage_templates.archive')}
        onCancel={() => setArchiveTemplateTarget(null)}
        onConfirm={async () => {
          const target = archiveTemplateTarget;
          setArchiveTemplateTarget(null);
          if (target?.id) {
            await archiveTemplateMut.mutateAsync();
            setSuccessMessage(t('gradeStructure.manage_templates.archive_success'));
          }
        }}
      />
    </div>
  );
}
