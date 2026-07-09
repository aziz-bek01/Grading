import { useTranslation } from 'react-i18next';
import { Breadcrumbs } from '@/shared/components/layout/Breadcrumbs';
import { LoadingState } from '@/shared/components/feedback/LoadingState';
import { ErrorState } from '@/shared/components/feedback/ErrorState';
import { Card } from '@/shared/components/ui/Card';
import { CommentThread } from '@/features/comment/components/CommentThread';
import { FactorTable } from '../components/FactorTable';
import { WeightSumVisualizer } from '../components/WeightSumVisualizer';
import { MethodologyTypeBadge } from '../components/MethodologyTypeBadge';
import { ScoringModeBadge } from '../components/ScoringModeBadge';
import { LockedMethodologyHeader } from '../components/LockedMethodologyHeader';
import { MethodologyVersionPanel } from '../components/MethodologyVersionPanel';
import { MethodologyBuilderHeader } from './MethodologyBuilderHeader';
import { MethodologyBuilderNotices } from './MethodologyBuilderNotices';
import { MethodologyBuilderDialogs } from './MethodologyBuilderDialogs';
import { useMethodologyBuilderState } from './useMethodologyBuilderState';

/**
 * Methodology builder page: header + lifecycle actions, factor table
 * (drag-free up/down reorder), the factor/level editor drawer, version
 * history, and the various lifecycle confirmation dialogs.
 *
 * FE-041 — this page is now a thin orchestrator: ALL state, mutations and
 * handlers live in {@link useMethodologyBuilderState}; the title bar, notice
 * banners and every dialog/drawer each live in their own file. No behaviour,
 * testid or DOM change.
 */
export function MethodologyBuilderPage() {
  const { t } = useTranslation();
  const s = useMethodologyBuilderState();

  if (s.methodologyQuery.isLoading || s.versionQuery.isLoading) return <LoadingState />;
  if (s.methodologyQuery.error || s.versionQuery.error)
    return <ErrorState onRetry={() => s.versionQuery.refetch()} />;
  if (!s.methodology || !s.version) return <ErrorState />;

  const { methodology, version } = s;

  return (
    <div className="space-y-6">
      <Breadcrumbs
        extra={[
          { label: t('nav.methodology'), to: `/app/projects/${s.projectId}/methodology` },
          { label: methodology.name_i18n?.[s.currentLocale] ?? methodology.code },
          { label: t('methodology.version_label', { number: version.version_number }) },
        ]}
      />

      <MethodologyBuilderHeader
        methodology={methodology}
        version={version}
        currentLocale={s.currentLocale}
        onOpenTranslations={s.navigateToTranslations}
        onSaveAsTemplate={() => s.setSaveTemplateOpen(true)}
        onEditMetadata={() => s.setMetadataOpen(true)}
        onApprove={() => s.setApproveOpen(true)}
        onArchive={() => s.setArchiveOpen(true)}
      />

      <MethodologyBuilderNotices
        approvedEditMode={s.approvedEditMode}
        deprecateNotice={s.deprecateNotice}
        onDismissDeprecateNotice={() => s.setDeprecateNotice(null)}
        templateSuccess={s.templateSuccess}
      />

      {s.readOnly ? (
        <LockedMethodologyHeader
          version={version}
          onCreateNewVersion={() => s.setNewVersionOpen(true)}
        />
      ) : null}

      <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
        <main className="lg:col-span-3 space-y-4">
          {!s.readOnly ? (
            <WeightSumVisualizer
              factors={s.factors}
              scoringMode={version.scoring_mode}
              target={s.target}
            />
          ) : null}

          <FactorTable
            factors={s.factors}
            scoringMode={version.scoring_mode}
            currentLocale={s.currentLocale}
            readOnly={s.readOnly}
            onEdit={s.handleEditFactor}
            onRemove={s.handleRemoveFactor}
            onReorder={s.handleReorder}
            onAdd={s.handleNewFactor}
          />
        </main>
        <aside className="space-y-4">
          <MethodologyVersionPanel
            versions={s.versions}
            loading={s.versionsQuery.isLoading}
            activeVersionId={version.id}
            onSelect={s.navigateToVersion}
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

      <MethodologyBuilderDialogs
        methodology={methodology}
        version={version}
        readOnly={s.readOnly}
        editorOpen={s.editorOpen}
        editorFactor={s.editorFactor}
        onCloseEditor={s.handleCloseEditor}
        onFactorSubmit={s.handleFactorSubmit}
        onAddLevel={s.handleAddLevel}
        onUpdateLevel={s.handleUpdateLevel}
        onRemoveLevel={s.handleRemoveLevel}
        onReorderLevel={s.handleReorderLevel}
        approveOpen={s.approveOpen}
        onApproveCancel={() => s.setApproveOpen(false)}
        onApproveConfirm={s.handleApproveConfirm}
        removeFactorTargetOpen={s.removeFactorTarget !== null}
        onRemoveFactorCancel={() => s.setRemoveFactorTarget(null)}
        onRemoveFactorConfirm={s.confirmRemoveFactor}
        removeLevelTargetOpen={s.removeLevelTarget !== null}
        onRemoveLevelCancel={() => s.setRemoveLevelTarget(null)}
        onRemoveLevelConfirm={s.confirmRemoveLevel}
        archiveOpen={s.archiveOpen}
        onArchiveCancel={() => s.setArchiveOpen(false)}
        onArchiveConfirm={s.handleArchiveConfirm}
        approvedEditConfirmOpen={s.approvedEditConfirmOpen}
        onApprovedEditCancel={s.handleApprovedEditCancel}
        onApprovedEditConfirm={s.handleApprovedEditConfirm}
        newVersionOpen={s.newVersionOpen}
        onNewVersionCancel={() => s.setNewVersionOpen(false)}
        onNewVersionConfirm={s.handleCreateNewVersion}
        saveTemplateOpen={s.saveTemplateOpen}
        onSaveTemplateClose={() => s.setSaveTemplateOpen(false)}
        onSaveTemplateSubmit={s.handleSaveAsTemplate}
        metadataOpen={s.metadataOpen}
        onMetadataClose={() => s.setMetadataOpen(false)}
        onMetadataSubmit={s.handleMetadataSubmit}
      />

      <Card title={t('comment.thread_title')} compact>
        <CommentThread entityType="METHODOLOGY_VERSION" entityId={s.versionId} />
      </Card>
    </div>
  );
}
