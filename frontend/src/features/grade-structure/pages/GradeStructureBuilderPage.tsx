import { useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Check, Archive, BarChart3, Lock } from 'lucide-react';
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
import { GradeStructureStatusBadge } from '../components/GradeStructureStatusBadge';
import { GradeStructureTypeBadge } from '../components/GradeStructureTypeBadge';
import { GradeTable } from '../components/GradeTable';
import { GradeBandValidator } from '../components/GradeBandValidator';
import { GradeRowEditor } from '../components/GradeRowEditor';
import { LockedGradeStructureHeader } from '../components/LockedGradeStructureHeader';
import { ScoreToGradeLookup } from '../components/ScoreToGradeLookup';
import {
  useAddGrade,
  useApprove,
  useArchive,
  useCreateNewVersion,
  useGradeStructure,
  useLock,
  useRemoveGrade,
  useUpdateGrade,
  useUpsertBand,
} from '../hooks/useGradeStructure';
import { routes } from '@/shared/config/routes';
import type { Locale, LocalizedString } from '@/shared/types/common';
import type { Grade } from '../types';

export function GradeStructureBuilderPage() {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const { projectId = '', gradeStructureId = '', versionId } = useParams<{
    projectId: string;
    gradeStructureId: string;
    versionId?: string;
  }>();
  const currentLocale = (useAuthStore((s) => s.user?.locale) ??
    (i18n.language as Locale)) as Locale;

  // versionId in URL is a hint — we always load the structure by id (each
  // version IS a separate grade-structure row in the backend model).
  const effectiveId = versionId ?? gradeStructureId;
  const structureQuery = useGradeStructure(effectiveId);
  const structure = structureQuery.data;

  // Mutations
  const [editorGrade, setEditorGrade] = useState<Grade | null>(null);
  const [editorOpen, setEditorOpen] = useState(false);
  const [approveOpen, setApproveOpen] = useState(false);
  const [lockOpen, setLockOpen] = useState(false);
  const [archiveOpen, setArchiveOpen] = useState(false);
  const [newVersionOpen, setNewVersionOpen] = useState(false);

  const addGradeMut = useAddGrade(effectiveId);
  const updateGradeMut = useUpdateGrade(editorGrade?.id ?? '', effectiveId);
  const removeGradeMut = useRemoveGrade(editorGrade?.id ?? '', effectiveId);
  const upsertBandMut = useUpsertBand(editorGrade?.id ?? '', effectiveId);
  const approveMut = useApprove(effectiveId);
  const lockMut = useLock(effectiveId);
  const archiveMut = useArchive(effectiveId);
  const newVersionMut = useCreateNewVersion(effectiveId);

  const grades = useMemo(() => structure?.grades ?? [], [structure]);
  const readOnly = !structure || structure.status !== 'DRAFT';

  if (structureQuery.isLoading) return <LoadingState />;
  if (structureQuery.error)
    return <ErrorState onRetry={() => structureQuery.refetch()} />;
  if (!structure) return <ErrorState />;

  const handleEditGrade = (g: Grade) => {
    setEditorGrade(g);
    setEditorOpen(true);
  };
  const handleNewGrade = () => {
    setEditorGrade(null);
    setEditorOpen(true);
  };

  const handleGradeSubmit = async (input: {
    grade_number: number;
    name: LocalizedString;
    description?: LocalizedString;
    min_score: number;
    max_score: number;
  }) => {
    let id: string;
    if (editorGrade) {
      await updateGradeMut.mutateAsync({
        grade_number: input.grade_number,
        name: input.name,
        description: input.description,
      });
      id = editorGrade.id;
    } else {
      const created = await addGradeMut.mutateAsync({
        grade_number: input.grade_number,
        name: input.name,
        description: input.description,
        sort_order: grades.length,
      });
      id = created.id;
    }
    // upsert band
    if (id) {
      // Use a fresh mutation with the right id — we cannot rely on
      // editorGrade's closure for new grades.
      await upsertBandMut.mutateAsync({
        min_score: input.min_score,
        max_score: input.max_score,
      });
    }
    setEditorOpen(false);
    setEditorGrade(null);
  };

  const handleRemoveGrade = async (g: Grade) => {
    if (!window.confirm(t('gradeStructure.confirm_remove_grade'))) return;
    setEditorGrade(g);
    await removeGradeMut.mutateAsync();
    setEditorGrade(null);
  };

  const handleCreateNewVersion = async () => {
    setNewVersionOpen(false);
    const created = await newVersionMut.mutateAsync();
    navigate(routes.projectGradeStructureEdit(projectId, created.id));
  };

  return (
    <div className="space-y-6">
      <Breadcrumbs
        extra={[
          { label: t('nav.grades'), to: routes.projectGrades(projectId) },
          { label: structure.name?.[currentLocale] ?? structure.code },
          {
            label: t('gradeStructure.version_label', {
              number: structure.version_number,
            }),
          },
        ]}
      />

      <header
        className="flex items-start justify-between gap-4 flex-wrap"
        data-testid="grade-structure-header"
      >
        <div>
          <div className="flex items-center gap-2 flex-wrap">
            <h1 className="text-2xl text-text-primary">
              {structure.name?.[currentLocale] ?? structure.code}
            </h1>
            <GradeStructureStatusBadge status={structure.status} />
            <GradeStructureTypeBadge type={structure.structure_type} />
          </div>
          <p className="text-sm text-text-secondary mt-1">
            {t('gradeStructure.version_label', { number: structure.version_number })}
            {' · '}
            {t(`gradeStructure.gap_policy.${structure.gap_policy.toLowerCase()}`)}
          </p>
        </div>

        <div className="flex items-center gap-2 flex-wrap">
          <PermissionGate permission={PERMISSIONS.GRADE_READ}>
            <Button
              variant="secondary"
              size="sm"
              leadingIcon={<BarChart3 size={14} />}
              onClick={() =>
                navigate(routes.projectGradePyramid(projectId, structure.id))
              }
              data-testid="grade-structure-open-pyramid"
            >
              {t('gradeStructure.open_pyramid')}
            </Button>
          </PermissionGate>

          {!readOnly ? (
            <>
              <PermissionGate permission={PERMISSIONS.GRADE_STRUCTURE_APPROVE}>
                <Button
                  variant="primary"
                  size="sm"
                  leadingIcon={<Check size={14} />}
                  onClick={() => setApproveOpen(true)}
                  data-testid="grade-structure-action-approve"
                >
                  {t('gradeStructure.actions.approve')}
                </Button>
              </PermissionGate>
              <PermissionGate permission={PERMISSIONS.GRADE_EDIT}>
                <Button
                  variant="ghost"
                  size="sm"
                  leadingIcon={<Archive size={14} />}
                  onClick={() => setArchiveOpen(true)}
                  data-testid="grade-structure-action-archive"
                >
                  {t('common.archive')}
                </Button>
              </PermissionGate>
            </>
          ) : structure.status === 'APPROVED' ? (
            <PermissionGate permission={PERMISSIONS.GRADE_STRUCTURE_LOCK}>
              <Button
                variant="primary"
                size="sm"
                leadingIcon={<Lock size={14} />}
                onClick={() => setLockOpen(true)}
                data-testid="grade-structure-action-lock"
              >
                {t('gradeStructure.actions.lock')}
              </Button>
            </PermissionGate>
          ) : null}
        </div>
      </header>

      {readOnly ? (
        <LockedGradeStructureHeader
          structure={structure}
          onCreateNewVersion={() => setNewVersionOpen(true)}
        />
      ) : null}

      <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
        <main className="lg:col-span-3 space-y-4">
          <GradeBandValidator grades={grades} gapPolicy={structure.gap_policy} />
          <GradeTable
            grades={grades}
            currentLocale={currentLocale}
            readOnly={readOnly}
            onEdit={handleEditGrade}
            onRemove={handleRemoveGrade}
            onAdd={handleNewGrade}
          />
          <ScoreToGradeLookup gradeStructureId={structure.id} grades={grades} />
        </main>
        <aside className="space-y-4">
          <Card compact title={t('gradeStructure.summary_title')}>
            <dl className="text-sm space-y-1">
              <div className="flex justify-between">
                <dt className="text-text-secondary">{t('common.code')}</dt>
                <dd className="font-mono text-xs">{structure.code}</dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-text-secondary">{t('common.type')}</dt>
                <dd>
                  <GradeStructureTypeBadge type={structure.structure_type} />
                </dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-text-secondary">
                  {t('gradeStructure.gap_policy_label')}
                </dt>
                <dd className="text-xs">
                  {t(
                    `gradeStructure.gap_policy.${structure.gap_policy.toLowerCase()}`,
                  )}
                </dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-text-secondary">
                  {t('gradeStructure.column.grades_count')}
                </dt>
                <dd className="tabular-nums text-xs">{grades.length}</dd>
              </div>
            </dl>
          </Card>
        </aside>
      </div>

      <GradeRowEditor
        open={editorOpen}
        grade={editorGrade}
        readOnly={readOnly}
        onClose={() => {
          setEditorOpen(false);
          setEditorGrade(null);
        }}
        onSubmit={handleGradeSubmit}
      />

      <ConfirmDialog
        open={approveOpen}
        title={t('gradeStructure.confirm.approve_title')}
        body={t('gradeStructure.confirm.approve_body')}
        confirmLabel={t('gradeStructure.actions.approve')}
        onCancel={() => setApproveOpen(false)}
        onConfirm={async () => {
          setApproveOpen(false);
          await approveMut.mutateAsync();
        }}
      />

      <ConfirmDialog
        open={lockOpen}
        title={t('gradeStructure.confirm.lock_title')}
        body={t('gradeStructure.confirm.lock_body')}
        confirmLabel={t('gradeStructure.actions.lock')}
        onCancel={() => setLockOpen(false)}
        onConfirm={async () => {
          setLockOpen(false);
          await lockMut.mutateAsync();
        }}
      />

      <ReasonRequiredDialog
        open={archiveOpen}
        title={t('gradeStructure.confirm.archive_title')}
        body={t('gradeStructure.confirm.archive_body')}
        onCancel={() => setArchiveOpen(false)}
        onConfirm={async (reason) => {
          setArchiveOpen(false);
          await archiveMut.mutateAsync({ reason });
        }}
      />

      <ConfirmDialog
        open={newVersionOpen}
        title={t('gradeStructure.confirm.new_version_title')}
        body={t('gradeStructure.confirm.new_version_body')}
        confirmLabel={t('gradeStructure.create_new_version')}
        onCancel={() => setNewVersionOpen(false)}
        onConfirm={handleCreateNewVersion}
      />
    </div>
  );
}
