import { useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Languages, Check, Archive } from 'lucide-react';
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
  useReorderFactors,
  useUpdateFactor,
  useUpdateFactorLevel,
} from '../hooks/useMethodology';
import type { Locale } from '@/shared/types/common';
import type { Factor, FactorLevel } from '../types';

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

  // Editor state
  const [editorFactor, setEditorFactor] = useState<Factor | null>(null);
  const [editorOpen, setEditorOpen] = useState(false);
  const [approveOpen, setApproveOpen] = useState(false);
  const [archiveOpen, setArchiveOpen] = useState(false);
  const [newVersionOpen, setNewVersionOpen] = useState(false);

  // Mutations
  const addFactorMut = useAddFactor(versionId, methodologyId);
  const updateFactorMut = useUpdateFactor(editorFactor?.id ?? '', versionId, methodologyId);
  const removeFactorMut = useRemoveFactor(editorFactor?.id ?? '', versionId, methodologyId);
  const reorderMut = useReorderFactors(versionId, methodologyId);
  const addLevelMut = useAddFactorLevel(editorFactor?.id ?? '', versionId, methodologyId);
  const updateLevelMut = useUpdateFactorLevel(versionId, methodologyId);
  const removeLevelMut = useRemoveFactorLevel(versionId, methodologyId);

  const approveMut = useApproveVersion(versionId, methodologyId, projectId);
  const archiveMut = useArchiveVersion(versionId, methodologyId, projectId);
  const newVersionMut = useCreateNewVersion(methodologyId, projectId);

  const factors = useMemo(() => version?.factors ?? [], [version]);
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

  const handleEditFactor = (f: Factor) => {
    setEditorFactor(f);
    setEditorOpen(true);
  };

  const handleNewFactor = () => {
    setEditorFactor(null);
    setEditorOpen(true);
  };

  const handleFactorSubmit = async (patch: {
    code: string;
    name_i18n: import('@/shared/types/common').LocalizedString;
    description_i18n?: import('@/shared/types/common').LocalizedString;
    weight: number;
    max_points: number;
    required: boolean;
  }) => {
    if (editorFactor) {
      await updateFactorMut.mutateAsync(patch);
    } else {
      await addFactorMut.mutateAsync({
        ...patch,
        sort_order: factors.length,
      });
    }
    setEditorOpen(false);
  };

  const handleRemoveFactor = async (f: Factor) => {
    if (!window.confirm(t('methodology.confirm_remove_factor'))) return;
    setEditorFactor(f);
    await removeFactorMut.mutateAsync();
    setEditorFactor(null);
  };

  const handleReorder = async (f: Factor, direction: 'up' | 'down') => {
    const sorted = [...factors].sort((a, b) => a.sort_order - b.sort_order);
    const idx = sorted.findIndex((x) => x.id === f.id);
    const swapIdx = direction === 'up' ? idx - 1 : idx + 1;
    if (swapIdx < 0 || swapIdx >= sorted.length) return;
    const next = [...sorted];
    [next[idx], next[swapIdx]] = [next[swapIdx], next[idx]];
    await reorderMut.mutateAsync({
      order: next.map((x, i) => ({ id: x.id, sort_order: i })),
    });
  };

  // Level CRUD wired through the editor (needs the editor factor's id).
  const handleAddLevel = async (next: Omit<FactorLevel, 'id' | 'factor_id'>) => {
    if (!editorFactor) return;
    await addLevelMut.mutateAsync(next);
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

          {!readOnly ? (
            <>
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
        onClose={() => setEditorOpen(false)}
        onSubmit={handleFactorSubmit}
        onAddLevel={handleAddLevel}
        onUpdateLevel={handleUpdateLevel}
        onRemoveLevel={handleRemoveLevel}
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

      <Card title={t('comment.thread_title')} compact>
        <CommentThread entityType="METHODOLOGY_VERSION" entityId={versionId} />
      </Card>
    </div>
  );
}
