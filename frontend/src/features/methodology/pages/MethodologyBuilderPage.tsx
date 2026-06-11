import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Languages, Check, Archive, BookmarkPlus, Pencil } from 'lucide-react';
import { Breadcrumbs } from '@/shared/components/layout/Breadcrumbs';
import { LoadingState } from '@/shared/components/feedback/LoadingState';
import { ErrorState } from '@/shared/components/feedback/ErrorState';
import { Card } from '@/shared/components/ui/Card';
import { Button } from '@/shared/components/ui/Button';
import { ConfirmDialog } from '@/shared/components/confirm-dialog/ConfirmDialog';
import { ReasonRequiredDialog } from '@/shared/components/confirm-dialog/ReasonRequiredDialog';
import { PermissionGate } from '@/shared/components/access/PermissionGate';
import { PERMISSIONS } from '@/shared/types/permissions';
import { useAuthStore } from '@/features/auth/authStore';
import { FactorTable } from '../components/FactorTable';
import { CommentThread } from '@/features/comment/components/CommentThread';
import { FactorEditor } from '../components/FactorEditor';
import { WeightSumVisualizer } from '../components/WeightSumVisualizer';
import { MethodologyStatusBadge } from '../components/MethodologyStatusBadge';
import { MethodologyTypeBadge } from '../components/MethodologyTypeBadge';
import { ScoringModeBadge } from '../components/ScoringModeBadge';
import { LockedMethodologyHeader } from '../components/LockedMethodologyHeader';
import { MethodologyVersionPanel } from '../components/MethodologyVersionPanel';
import { SaveAsTemplateDrawer } from '../components/SaveAsTemplateDrawer';
import {
  MethodologyMetadataDrawer,
  type MethodologyMetadataPatch,
} from '../components/MethodologyMetadataDrawer';
import {
  useAddFactor,
  useAddFactorLevel,
  useApproveVersion,
  useArchiveVersion,
  useCreateNewVersion,
  useMethodology,
  useMethodologyVersion,
  useMethodologyVersions,
  useRemoveFactor,
  useRemoveFactorLevel,
  useReorderFactorLevels,
  useReorderFactors,
  useSaveMethodologyAsTemplate,
  useUpdateFactor,
  useUpdateFactorLevel,
  useUpdateMethodology,
  useUpdateMethodologyVersionMetadata,
} from '../hooks/useMethodology';
import type { Locale } from '@/shared/types/common';
import type { Factor, FactorLevel, SaveAsTemplatePayload } from '../types';

export function MethodologyBuilderPage() {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const { projectId = '', methodologyId = '', versionId = '' } = useParams<{
    projectId: string;
    methodologyId: string;
    versionId: string;
  }>();
  const currentLocale = (useAuthStore((s) => s.user?.locale) ?? (i18n.language as Locale)) as Locale;

  const methodologyQuery = useMethodology(methodologyId);
  const versionQuery = useMethodologyVersion(versionId);
  const versionsQuery = useMethodologyVersions(methodologyId);

  const methodology = methodologyQuery.data;
  const version = versionQuery.data;
  // fetchMethodologyVersions now returns a bare array (real-backend contract).
  const versions = versionsQuery.data;

  const factors = useMemo(() => version?.factors ?? [], [version]);

  // Editor state.
  //
  // We track the OPEN factor by ID, not by a captured `Factor` snapshot. The
  // open factor is then DERIVED from the live `factors` list (refreshed by the
  // mutation invalidations). The previous snapshot approach (EPIC-A bug) froze
  // `editorFactor.levels` at open time: a level add/update PATCHed + refetched
  // the version server-side, but the drawer kept rendering the stale snapshot,
  // so the saved level was invisible until the drawer was reopened.
  const [editorFactorId, setEditorFactorId] = useState<string | null>(null);
  const [editorOpen, setEditorOpen] = useState(false);
  const [approveOpen, setApproveOpen] = useState(false);
  const [archiveOpen, setArchiveOpen] = useState(false);
  const [newVersionOpen, setNewVersionOpen] = useState(false);
  // Epic E — save this methodology's version as a reusable CUSTOM template.
  const [saveTemplateOpen, setSaveTemplateOpen] = useState(false);
  const [templateSuccess, setTemplateSuccess] = useState<string | null>(null);
  // Edit methodology/version metadata (name/description/type + scoring/target).
  const [metadataOpen, setMetadataOpen] = useState(false);

  const editorFactor = useMemo(
    () => factors.find((f) => f.id === editorFactorId) ?? null,
    [factors, editorFactorId],
  );

  // Mutations
  const addFactorMut = useAddFactor(versionId, methodologyId);
  const updateFactorMut = useUpdateFactor(editorFactorId ?? '', versionId, methodologyId);
  const removeFactorMut = useRemoveFactor(editorFactorId ?? '', versionId, methodologyId);
  const reorderMut = useReorderFactors(versionId, methodologyId);
  const addLevelMut = useAddFactorLevel(versionId, methodologyId);
  const updateLevelMut = useUpdateFactorLevel(versionId, methodologyId);
  const removeLevelMut = useRemoveFactorLevel(versionId, methodologyId);
  // Level reorder targets the OPEN factor — the factorId is the editor factor
  // (the only context in which a level row's arrows are visible).
  const reorderLevelMut = useReorderFactorLevels(editorFactorId ?? '', versionId, methodologyId);

  const approveMut = useApproveVersion(versionId, methodologyId, projectId);
  const archiveMut = useArchiveVersion(versionId, methodologyId, projectId);
  const newVersionMut = useCreateNewVersion(methodologyId, projectId);
  const saveTemplateMut = useSaveMethodologyAsTemplate(methodologyId);
  // Metadata edit — container (name/description/type) + version (scoring/target).
  const updateMethodologyMut = useUpdateMethodology(methodologyId, projectId);
  const updateVersionMetadataMut = useUpdateMethodologyVersionMetadata(
    versionId,
    methodologyId,
    projectId,
  );

  // Auto-dismiss the "saved as template" banner like a transient toast.
  useEffect(() => {
    if (!templateSuccess) return;
    const id = window.setTimeout(() => setTemplateSuccess(null), 5000);
    return () => window.clearTimeout(id);
  }, [templateSuccess]);

  const readOnly = !version || version.status !== 'DRAFT';

  if (methodologyQuery.isLoading || versionQuery.isLoading) return <LoadingState />;
  if (methodologyQuery.error || versionQuery.error)
    return <ErrorState onRetry={() => versionQuery.refetch()} />;
  if (!methodology || !version) return <ErrorState />;

  const target =
    version.scoring_mode === 'WEIGHTED_POINTS' ? 100 : version.target_total_points ?? 100;

  const handleCreateNewVersion = async () => {
    setNewVersionOpen(false);
    const created = await newVersionMut.mutateAsync(version.id);
    navigate(
      `/app/projects/${projectId}/methodology/${methodologyId}/versions/${created.id}/edit`,
    );
  };

  // Epic E — snapshot this methodology into a reusable CUSTOM template. The
  // drawer surfaces a 409 duplicate-code conflict inline (it re-throws on error).
  const handleSaveAsTemplate = async (payload: SaveAsTemplatePayload) => {
    await saveTemplateMut.mutateAsync(payload);
    setSaveTemplateOpen(false);
    setTemplateSuccess(t('methodology.save_as_template.success', { code: payload.code }));
  };

  const handleEditFactor = (f: Factor) => {
    setEditorFactorId(f.id);
    setEditorOpen(true);
  };

  const handleNewFactor = () => {
    setEditorFactorId(null);
    setEditorOpen(true);
  };

  const handleCloseEditor = () => {
    setEditorOpen(false);
    setEditorFactorId(null);
  };

  const handleFactorSubmit = async (patch: {
    code: string;
    name_i18n: import('@/shared/types/common').LocalizedString;
    description_i18n?: import('@/shared/types/common').LocalizedString;
    weight: number;
    max_points: number;
    required: boolean;
  }) => {
    if (editorFactorId) {
      await updateFactorMut.mutateAsync(patch);
      setEditorOpen(false);
      setEditorFactorId(null);
    } else {
      // Create the factor, then keep the drawer OPEN and switch it to the new
      // factor so the level editor section appears in place — the user can add
      // levels without re-opening (EPIC-A/F1). The returned id binds the derived
      // `editorFactor` once the version refetch lands.
      const created = await addFactorMut.mutateAsync({
        ...patch,
        sort_order: factors.length,
      });
      setEditorFactorId(created.id);
    }
  };

  // removeFactorMut is bound to editorFactorId; point it at the row being
  // removed first so the right factor id is deleted.
  const handleRemoveFactor = async (f: Factor) => {
    if (!window.confirm(t('methodology.confirm_remove_factor'))) return;
    setEditorFactorId(f.id);
    await removeFactorMut.mutateAsync();
    setEditorFactorId(null);
  };

  const handleReorder = async (f: Factor, direction: 'up' | 'down') => {
    const sorted = [...factors].sort((a, b) => a.sort_order - b.sort_order);
    const idx = sorted.findIndex((x) => x.id === f.id);
    const swapIdx = direction === 'up' ? idx - 1 : idx + 1;
    if (swapIdx < 0 || swapIdx >= sorted.length) return;
    const next = [...sorted];
    [next[idx], next[swapIdx]] = [next[swapIdx], next[idx]];
    await reorderMut.mutateAsync({ ordered_ids: next.map((x) => x.id) });
  };

  // Level CRUD wired through the editor (needs the editor factor's id).
  // The factor id travels with the mutate VARS (F2) so a single hook instance
  // targets the right factor even when `editorFactorId` was set in the same
  // tick (no stale empty-string closure no-op).
  const handleAddLevel = async (next: Omit<FactorLevel, 'id' | 'factor_id'>) => {
    if (!editorFactorId) return;
    await addLevelMut.mutateAsync({ factorId: editorFactorId, payload: next });
  };

  const handleUpdateLevel = async (lvl: FactorLevel) => {
    // Pass the real level id with the call so the PATCH targets the right
    // row. Errors propagate to the editor (toast) — never swallowed.
    await updateLevelMut.mutateAsync({ levelId: lvl.id, payload: lvl });
  };

  const handleRemoveLevel = async (lvl: FactorLevel) => {
    if (!window.confirm(t('methodology.confirm_remove_level'))) return;
    await removeLevelMut.mutateAsync(lvl.id);
  };

  // Level reorder (ISSUE 1a) — mirrors handleReorder for factors but on the OPEN
  // factor's levels. Sort by level_order, swap with the neighbour, then send the
  // COMPLETE re-indexed order to POST /factors/{id}/levels/reorder. The hook is
  // bound to editorFactorId so a single instance targets the open factor.
  const handleReorderLevel = async (lvl: FactorLevel, direction: 'up' | 'down') => {
    if (!editorFactor) return;
    const sorted = [...editorFactor.levels].sort((a, b) => a.level_order - b.level_order);
    const idx = sorted.findIndex((x) => x.id === lvl.id);
    const swapIdx = direction === 'up' ? idx - 1 : idx + 1;
    if (swapIdx < 0 || swapIdx >= sorted.length) return;
    const next = [...sorted];
    [next[idx], next[swapIdx]] = [next[swapIdx], next[idx]];
    await reorderLevelMut.mutateAsync({ ordered_ids: next.map((x) => x.id) });
  };

  // Metadata edit (ISSUE 2) — sequence the two distinct PATCH surfaces:
  //   (a) container: name/description (+ methodology_type) -> PATCH /methodologies/{id}
  //   (b) version:   scoring_mode (+ target_total_points)  -> PATCH /methodology-versions/{id}
  // Container first so a METHODOLOGY_TYPE_LOCKED conflict aborts before any
  // version change. Errors re-throw so the drawer surfaces them inline; the
  // drawer closes only on success.
  const handleMetadataSubmit = async (patch: MethodologyMetadataPatch) => {
    await updateMethodologyMut.mutateAsync(patch.methodology);
    if (patch.version) {
      await updateVersionMetadataMut.mutateAsync(patch.version);
    }
    setMetadataOpen(false);
  };

  return (
    <div className="space-y-6">
      <Breadcrumbs
        extra={[
          { label: t('nav.methodology'), to: `/app/projects/${projectId}/methodology` },
          { label: methodology.name_i18n?.[currentLocale] ?? methodology.code },
          { label: t('methodology.version_label', { number: version.version_number }) },
        ]}
      />

      {/* Header bar */}
      <header className="flex items-start justify-between gap-4 flex-wrap" data-testid="methodology-header">
        <div>
          <div className="flex items-center gap-2 flex-wrap">
            <h1 className="text-2xl text-text-primary">
              {methodology.name_i18n?.[currentLocale] ?? methodology.code}
            </h1>
            <MethodologyStatusBadge status={version.status} />
            <MethodologyTypeBadge type={methodology.methodology_type} />
            <ScoringModeBadge mode={version.scoring_mode} />
          </div>
          <p className="text-sm text-text-secondary mt-1">
            {t('methodology.version_label', { number: version.version_number })}
          </p>
        </div>

        <div className="flex items-center gap-2 flex-wrap">
          <PermissionGate permission={PERMISSIONS.METHODOLOGY_READ}>
            <Button
              variant="secondary"
              size="sm"
              leadingIcon={<Languages size={14} />}
              onClick={() =>
                navigate(
                  `/app/projects/${projectId}/methodology/${methodologyId}/versions/${versionId}/translations`,
                )
              }
              data-testid="open-translations"
            >
              {t('methodology.translations_button')}
            </Button>
          </PermissionGate>

          <PermissionGate permission={PERMISSIONS.METHODOLOGY_CREATE}>
            <Button
              variant="secondary"
              size="sm"
              leadingIcon={<BookmarkPlus size={14} />}
              onClick={() => setSaveTemplateOpen(true)}
              data-testid="action-save-as-template"
            >
              {t('methodology.save_as_template.action')}
            </Button>
          </PermissionGate>

          {!readOnly ? (
            <>
              <PermissionGate permission={PERMISSIONS.METHODOLOGY_EDIT}>
                <Button
                  variant="secondary"
                  size="sm"
                  leadingIcon={<Pencil size={14} />}
                  onClick={() => setMetadataOpen(true)}
                  data-testid="action-edit-metadata"
                >
                  {t('methodology.metadata.edit_action')}
                </Button>
              </PermissionGate>
              <PermissionGate permission={PERMISSIONS.METHODOLOGY_APPROVE}>
                <Button
                  variant="primary"
                  size="sm"
                  leadingIcon={<Check size={14} />}
                  onClick={() => setApproveOpen(true)}
                  data-testid="action-approve"
                >
                  {t('methodology.approve_lock')}
                </Button>
              </PermissionGate>
              <PermissionGate permission={PERMISSIONS.METHODOLOGY_EDIT}>
                <Button
                  variant="ghost"
                  size="sm"
                  leadingIcon={<Archive size={14} />}
                  onClick={() => setArchiveOpen(true)}
                  data-testid="action-archive"
                >
                  {t('common.archive')}
                </Button>
              </PermissionGate>
            </>
          ) : null}
        </div>
      </header>

      {templateSuccess ? (
        <div
          role="status"
          className="rounded-md border border-success-500/30 bg-success-50 px-4 py-3 text-sm text-success-700"
          data-testid="builder-template-success"
        >
          {templateSuccess}
        </div>
      ) : null}

      {readOnly ? (
        <LockedMethodologyHeader
          version={version}
          onCreateNewVersion={() => setNewVersionOpen(true)}
        />
      ) : null}

      <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
        <main className="lg:col-span-3 space-y-4">
          {!readOnly ? (
            <WeightSumVisualizer
              factors={factors}
              scoringMode={version.scoring_mode}
              target={target}
            />
          ) : null}

          <FactorTable
            factors={factors}
            scoringMode={version.scoring_mode}
            currentLocale={currentLocale}
            readOnly={readOnly}
            onEdit={handleEditFactor}
            onRemove={handleRemoveFactor}
            onReorder={handleReorder}
            onAdd={handleNewFactor}
          />
        </main>
        <aside className="space-y-4">
          <MethodologyVersionPanel
            versions={versions}
            loading={versionsQuery.isLoading}
            activeVersionId={version.id}
            onSelect={(vid) =>
              navigate(
                `/app/projects/${projectId}/methodology/${methodologyId}/versions/${vid}/edit`,
              )
            }
          />
          <Card compact title={t('methodology.summary_title')}>
            <dl className="text-sm space-y-1">
              <div className="flex justify-between">
                <dt className="text-text-secondary">{t('factor.column.code')}</dt>
                <dd className="font-mono text-xs">{methodology.code}</dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-text-secondary">{t('common.type')}</dt>
                <dd><MethodologyTypeBadge type={methodology.methodology_type} /></dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-text-secondary">{t('methodology.scoring_mode')}</dt>
                <dd><ScoringModeBadge mode={version.scoring_mode} /></dd>
              </div>
            </dl>
          </Card>
        </aside>
      </div>

      <FactorEditor
        open={editorOpen}
        factor={editorFactor}
        scoringMode={version.scoring_mode}
        readOnly={readOnly}
        onClose={handleCloseEditor}
        onSubmit={handleFactorSubmit}
        onAddLevel={handleAddLevel}
        onUpdateLevel={handleUpdateLevel}
        onRemoveLevel={handleRemoveLevel}
        onReorderLevel={handleReorderLevel}
      />

      <ConfirmDialog
        open={approveOpen}
        title={t('methodology.confirm.approve_title')}
        body={t('methodology.confirm.approve_body')}
        confirmLabel={t('methodology.approve_lock')}
        onCancel={() => setApproveOpen(false)}
        onConfirm={async () => {
          setApproveOpen(false);
          await approveMut.mutateAsync();
        }}
      />

      <ReasonRequiredDialog
        open={archiveOpen}
        title={t('methodology.confirm.archive_title')}
        body={t('methodology.confirm.archive_body')}
        onCancel={() => setArchiveOpen(false)}
        onConfirm={async (reason) => {
          setArchiveOpen(false);
          await archiveMut.mutateAsync({ reason });
        }}
      />

      <ConfirmDialog
        open={newVersionOpen}
        title={t('methodology.confirm.new_version_title')}
        body={t('methodology.confirm.new_version_body')}
        confirmLabel={t('methodology.create_new_version')}
        onCancel={() => setNewVersionOpen(false)}
        onConfirm={handleCreateNewVersion}
      />

      <SaveAsTemplateDrawer
        open={saveTemplateOpen}
        methodology={methodology}
        onClose={() => setSaveTemplateOpen(false)}
        onSubmit={handleSaveAsTemplate}
      />

      {/* Edit methodology + DRAFT version metadata. `editable` is true only when
          the version is editable (DRAFT) so APPROVED/LOCKED never expose the
          type/scoring fields — the backend also enforces DRAFT-only. */}
      <MethodologyMetadataDrawer
        open={metadataOpen}
        methodology={methodology}
        version={version}
        editable={!readOnly}
        onClose={() => setMetadataOpen(false)}
        onSubmit={handleMetadataSubmit}
      />

      <Card title={t('comment.thread_title')} compact>
        <CommentThread entityType="METHODOLOGY_VERSION" entityId={versionId} />
      </Card>
    </div>
  );
}
