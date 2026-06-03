import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Breadcrumbs } from '@/shared/components/layout/Breadcrumbs';
import { LoadingState } from '@/shared/components/feedback/LoadingState';
import { ErrorState } from '@/shared/components/feedback/ErrorState';
import { EmptyState } from '@/shared/components/feedback/EmptyState';
import { Card } from '@/shared/components/ui/Card';
import { Button } from '@/shared/components/ui/Button';
import { PermissionGate } from '@/shared/components/access/PermissionGate';
import { PERMISSIONS } from '@/shared/types/permissions';
import {
  useApproveJobProfile,
  useArchiveJobProfile,
  useCreateJobProfile,
  useCreateJobProfileRevision,
  useJobProfileByPosition,
  useJobProfileRevisions,
  useRequestChanges,
  useSubmitJobProfile,
  useUpdateJobProfile,
} from '../hooks/useJobProfile';
import { JobProfileStatusBadge } from '../components/JobProfileStatusBadge';
import { JobProfileSectionGroup } from '../components/JobProfileSectionGroup';
import { JobProfileFieldEditor } from '../components/JobProfileFieldEditor';
import { JobProfileActionsBar } from '../components/JobProfileActionsBar';
import { JobProfileRevisionHistory } from '../components/JobProfileRevisionHistory';
import { AIRecommendationPanel } from '../components/AIRecommendationPanel';
import { CommentThread } from '@/features/comment/components/CommentThread';
import { ApprovalStatusBadge } from '@/features/approval/components/ApprovalStatusBadge';
import { useApprovalRequests } from '@/features/approval/hooks/useApprovals';
import type {
  JobProfile,
  JobProfileLongTextFieldKey,
  JobProfilePatch,
} from '../types';
import { routes } from '@/shared/config/routes';

const SECTIONS: { key: string; titleKey: string; fields: JobProfileLongTextFieldKey[] }[] = [
  {
    key: 'role',
    titleKey: 'jobProfile.section.role',
    fields: ['purpose_i18n', 'main_duties_i18n', 'responsibility_area_i18n', 'authority_i18n', 'kpi_expected_results_i18n'],
  },
  {
    key: 'requirements',
    titleKey: 'jobProfile.section.requirements',
    fields: ['education_requirements_i18n', 'experience_requirements_i18n', 'knowledge_skills_i18n'],
  },
  {
    key: 'interactions',
    titleKey: 'jobProfile.section.interactions',
    fields: ['internal_interactions_i18n', 'external_interactions_i18n'],
  },
  {
    key: 'working_context',
    titleKey: 'jobProfile.section.working_context',
    fields: ['working_conditions_i18n', 'documents_regulations_i18n'],
  },
];

const AUTO_SAVE_DEBOUNCE_MS = 30_000;

function emptyFromProfile(p: JobProfile): JobProfilePatch {
  return {
    purpose_i18n: p.purpose_i18n,
    main_duties_i18n: p.main_duties_i18n,
    responsibility_area_i18n: p.responsibility_area_i18n,
    authority_i18n: p.authority_i18n,
    kpi_expected_results_i18n: p.kpi_expected_results_i18n,
    education_requirements_i18n: p.education_requirements_i18n,
    experience_requirements_i18n: p.experience_requirements_i18n,
    knowledge_skills_i18n: p.knowledge_skills_i18n,
    internal_interactions_i18n: p.internal_interactions_i18n,
    external_interactions_i18n: p.external_interactions_i18n,
    working_conditions_i18n: p.working_conditions_i18n,
    documents_regulations_i18n: p.documents_regulations_i18n,
    actualization_date: p.actualization_date ?? '',
  };
}

export function JobProfileEditorPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { projectId = '', positionId = '' } = useParams<{ projectId: string; positionId: string }>();

  const profileQuery = useJobProfileByPosition(positionId);
  const revisionsQuery = useJobProfileRevisions(positionId);

  const profile = profileQuery.data ?? null;
  const profileId = profile?.id ?? '';

  const createMut = useCreateJobProfile(positionId);
  const updateMut = useUpdateJobProfile(profileId, positionId);
  const submitMut = useSubmitJobProfile(profileId, positionId);
  const approveMut = useApproveJobProfile(profileId, positionId);
  const requestChangesMut = useRequestChanges(profileId, positionId);
  const archiveMut = useArchiveJobProfile(profileId, positionId);
  const revisionMut = useCreateJobProfileRevision(profileId, positionId);

  const [draft, setDraft] = useState<JobProfilePatch | null>(null);
  const dirtyRef = useRef(false);

  useEffect(() => {
    if (profile) setDraft(emptyFromProfile(profile));
  }, [profile?.id, profile?.status]); // eslint-disable-line react-hooks/exhaustive-deps

  const readOnly = profile?.status !== 'DRAFT';

  const handleFieldChange = useCallback(
    (key: JobProfileLongTextFieldKey, value: JobProfilePatch[JobProfileLongTextFieldKey]) => {
      if (readOnly) return;
      setDraft((prev) => ({ ...(prev ?? {}), [key]: value }));
      dirtyRef.current = true;
    },
    [readOnly],
  );

  // Track the current status in a ref so the interval callback (created once)
  // always observes the latest value — even if status flips DRAFT → UNDER_REVIEW
  // between ticks (F-306).
  const statusRef = useRef(profile?.status);
  useEffect(() => {
    statusRef.current = profile?.status;
  }, [profile?.status]);

  const saveDraft = useCallback(async () => {
    if (!draft || !profileId) return;
    // Re-check at call time: status may have flipped to UNDER_REVIEW/APPROVED
    // while a save was queued (F-306). Backend rejects with 409, but skip the
    // flash error and the wasted PATCH.
    if (statusRef.current !== 'DRAFT') return;
    await updateMut.mutateAsync(draft);
    dirtyRef.current = false;
  }, [draft, profileId, updateMut]);

  // Auto-save every 30s when draft is dirty. The cleanup runs whenever
  // `readOnly` flips (e.g. after Submit transitions DRAFT → UNDER_REVIEW),
  // cancelling any pending tick so no stale PATCH lands on a now-locked profile.
  useEffect(() => {
    if (readOnly) return;
    const interval = window.setInterval(() => {
      if (dirtyRef.current && statusRef.current === 'DRAFT') {
        void saveDraft();
      }
    }, AUTO_SAVE_DEBOUNCE_MS);
    return () => window.clearInterval(interval);
  }, [readOnly, saveDraft]);

  const sectionItems = useMemo(() => SECTIONS, []);

  if (profileQuery.isLoading) return <LoadingState />;
  if (profileQuery.error) return <ErrorState onRetry={() => profileQuery.refetch()} />;

  // No profile yet → empty state with create CTA.
  if (!profile) {
    return (
      <div className="space-y-6">
        <Breadcrumbs extra={[{ label: t('jobProfile.page_title') }]} />
        <Card>
          <EmptyState
            title={t('jobProfile.empty_title')}
            body={t('jobProfile.empty_body')}
            action={
              <PermissionGate permission={PERMISSIONS.JOB_PROFILE_EDIT}>
                <Button
                  onClick={async () => {
                    await createMut.mutateAsync();
                  }}
                  data-testid="create-profile"
                >
                  {t('jobProfile.create_button')}
                </Button>
              </PermissionGate>
            }
          />
        </Card>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <Breadcrumbs
        extra={[
          { label: t('positions.details_title'), to: routes.projectPositionDetail(projectId, positionId) },
          { label: t('jobProfile.page_title') },
        ]}
      />
      <header className="flex items-start justify-between gap-4 flex-wrap">
        <div>
          <div className="flex items-center gap-2 flex-wrap">
            <h1 className="text-2xl text-text-primary">{t('jobProfile.page_title')}</h1>
            <JobProfileStatusBadge status={profile.status} />
            {profile.revision_number > 1 ? (
              <span className="text-xs text-text-muted px-2 py-0.5 rounded-full border border-border">
                {t('jobProfile.revision_number', { number: profile.revision_number })}
              </span>
            ) : null}
            <JobProfileApprovalInline jobProfileId={profile.id} status={profile.status} />
          </div>
          <p className="text-sm text-text-secondary mt-1">{t('jobProfile.page_subtitle')}</p>
        </div>
      </header>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 space-y-4">
          {sectionItems.map((section) => (
            <JobProfileSectionGroup
              key={section.key}
              title={t(section.titleKey)}
              defaultOpen={section.key === 'role'}
            >
              {section.fields.map((fieldKey) => {
                const value =
                  (draft?.[fieldKey] as JobProfilePatch[JobProfileLongTextFieldKey] | undefined) ?? {};
                return (
                  <JobProfileFieldEditor
                    key={fieldKey}
                    fieldKey={fieldKey}
                    label={t(`jobProfile.field.${fieldKey}`)}
                    hint={t(`jobProfile.hint.${fieldKey}`)}
                    value={value ?? {}}
                    onChange={(next) => handleFieldChange(fieldKey, next)}
                    readOnly={readOnly}
                  />
                );
              })}
              {section.key === 'working_context' ? (
                <div className="space-y-2" data-testid="field-actualization_date">
                  <label className="text-sm font-medium text-text-primary">
                    {t('jobProfile.field.actualization_date')}
                  </label>
                  {readOnly ? (
                    <div className="px-3 py-2 rounded-md bg-locked-bg border border-border text-sm">
                      {profile.actualization_date ?? t('common.none')}
                    </div>
                  ) : (
                    <input
                      type="date"
                      value={draft?.actualization_date ?? ''}
                      onChange={(e) => {
                        setDraft((prev) => ({ ...(prev ?? {}), actualization_date: e.target.value }));
                        dirtyRef.current = true;
                      }}
                      className="h-10 px-3 border border-border-strong rounded-md text-sm bg-surface"
                    />
                  )}
                </div>
              ) : null}
            </JobProfileSectionGroup>
          ))}
        </div>

        <aside className="space-y-4">
          <JobProfileActionsBar
            profile={profile}
            saving={updateMut.isPending}
            onSave={saveDraft}
            onSubmit={async () => {
              await saveDraft();
              await submitMut.mutateAsync();
            }}
            onApprove={async () => {
              await approveMut.mutateAsync();
            }}
            onRequestChanges={async (reason) => {
              await requestChangesMut.mutateAsync({ reason });
            }}
            onArchive={async (reason) => {
              await archiveMut.mutateAsync({ reason });
            }}
            onCreateRevision={async () => {
              const newDraft = await revisionMut.mutateAsync();
              // Route refresh — same URL but new id; navigate to same page to refetch.
              navigate(
                `/app/projects/${projectId}/positions/${positionId}/job-profile?revision=${newDraft.id}`,
                { replace: true },
              );
            }}
          />
          <AIRecommendationPanel />
          <JobProfileRevisionHistory
            revisions={revisionsQuery.data?.items}
            loading={revisionsQuery.isLoading}
          />
        </aside>
      </div>

      <Card title={t('comment.thread_title')} compact>
        <CommentThread entityType="JOB_PROFILE" entityId={profile.id} />
      </Card>
    </div>
  );
}

/**
 * Shows the inline approval status badge next to a job profile header.
 * When the profile is UNDER_REVIEW, the backend always has an active
 * approval request — we fetch the latest one and surface its status.
 */
function JobProfileApprovalInline({
  jobProfileId,
  status,
}: {
  jobProfileId: string;
  status: string;
}) {
  const requests = useApprovalRequests({
    entityType: 'JOB_PROFILE',
    entityId: jobProfileId,
  });
  if (status !== 'UNDER_REVIEW') return null;
  const latest = requests.data?.items[0];
  if (!latest) return null;
  return <ApprovalStatusBadge status={latest.status} />;
}
