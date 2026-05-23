import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Card } from '@/shared/components/ui/Card';
import { Button } from '@/shared/components/ui/Button';
import { Breadcrumbs } from '@/shared/components/layout/Breadcrumbs';
import { LoadingState } from '@/shared/components/feedback/LoadingState';
import { ErrorState } from '@/shared/components/feedback/ErrorState';
import { EmptyState } from '@/shared/components/feedback/EmptyState';
import { PermissionGate } from '@/shared/components/access/PermissionGate';
import { PERMISSIONS } from '@/shared/types/permissions';
import { usePosition } from '../hooks/usePosition';
import { PositionStatusBadge } from '../components/PositionStatusBadge';
import { JobProfileStatusBadge } from '@/features/job-profiles/components/JobProfileStatusBadge';
import {
  useCreateJobProfile,
  useJobProfileByPosition,
} from '@/features/job-profiles/hooks/useJobProfile';
import {
  useCreateQuestionnaire,
  useQuestionnaires,
  useTemplates,
} from '@/features/job-analysis/hooks/useQuestionnaire';
import { pickLocalized } from '@/shared/lib/localized';
import { cn } from '@/shared/lib/cn';

type Tab = 'overview' | 'job_profile' | 'job_analysis' | 'evaluation' | 'grade' | 'audit';

export function PositionDetailsPage() {
  const { t, i18n } = useTranslation();
  const { projectId = '', positionId = '' } = useParams<{ projectId: string; positionId: string }>();
  const [tab, setTab] = useState<Tab>('overview');
  const position = usePosition(positionId);
  const profileQuery = useJobProfileByPosition(positionId);
  const questionnairesQuery = useQuestionnaires(positionId);
  const templatesQuery = useTemplates();
  const createProfile = useCreateJobProfile(positionId);
  const createQuestionnaire = useCreateQuestionnaire(positionId);

  if (position.isLoading) return <LoadingState />;
  if (position.error) return <ErrorState onRetry={() => position.refetch()} />;
  if (!position.data) return <EmptyState />;

  const p = position.data;
  const jobProfileHref = `/app/projects/${projectId}/positions/${positionId}/job-profile`;

  const tabs: { key: Tab; label: string }[] = [
    { key: 'overview', label: t('positions.tab_overview') },
    { key: 'job_profile', label: t('positions.tab_job_profile') },
    { key: 'job_analysis', label: t('positions.tab_job_analysis') },
    { key: 'evaluation', label: t('positions.tab_evaluation') },
    { key: 'grade', label: t('positions.tab_grade') },
    { key: 'audit', label: t('positions.tab_audit') },
  ];

  return (
    <div className="space-y-6">
      <Breadcrumbs extra={[{ label: pickLocalized(p.title, i18n.language) }]} />
      <header className="flex items-start justify-between gap-4 flex-wrap">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-2xl text-text-primary">{pickLocalized(p.title, i18n.language)}</h1>
            <PositionStatusBadge status={p.status} />
          </div>
          <p className="text-sm text-text-secondary mt-1">
            {p.code} · {p.function ?? '—'} · {p.job_family ?? '—'} · {p.job_level ?? '—'}
          </p>
        </div>
      </header>

      <div role="tablist" className="border-b border-border flex gap-2 flex-wrap">
        {tabs.map((tInfo) => (
          <button
            key={tInfo.key}
            role="tab"
            aria-selected={tab === tInfo.key}
            onClick={() => setTab(tInfo.key)}
            className={cn(
              'px-3 py-2 text-sm border-b-2 -mb-px',
              tab === tInfo.key
                ? 'border-primary-500 text-primary-700 font-medium'
                : 'border-transparent text-text-secondary hover:text-text-primary',
            )}
          >
            {tInfo.label}
          </button>
        ))}
      </div>

      {tab === 'overview' ? (
        <Card title={t('positions.tab_overview')}>
          <dl className="grid grid-cols-2 gap-y-3 gap-x-6 text-sm">
            <dt className="text-text-secondary">{t('positions.field_function')}</dt>
            <dd className="text-text-primary">{p.function ?? '—'}</dd>
            <dt className="text-text-secondary">{t('positions.field_category')}</dt>
            <dd className="text-text-primary">{p.category ?? '—'}</dd>
            <dt className="text-text-secondary">{t('positions.field_job_family')}</dt>
            <dd className="text-text-primary">{p.job_family ?? '—'}</dd>
            <dt className="text-text-secondary">{t('positions.field_job_level')}</dt>
            <dd className="text-text-primary">{p.job_level ?? '—'}</dd>
          </dl>
        </Card>
      ) : null}

      {tab === 'job_profile' ? (
        <Card title={t('positions.tab_job_profile')}>
          {profileQuery.isLoading ? (
            <LoadingState />
          ) : profileQuery.data ? (
            <div className="flex items-start justify-between gap-4 flex-wrap" data-testid="profile-summary">
              <div className="space-y-1 text-sm">
                <div className="flex items-center gap-2">
                  <JobProfileStatusBadge status={profileQuery.data.status} />
                  <span className="text-xs text-text-muted">
                    {t('jobProfile.revision_number', { number: profileQuery.data.revision_number })}
                  </span>
                </div>
                <p className="text-text-secondary">
                  {t('jobProfile.last_updated', { date: profileQuery.data.updated_at.slice(0, 10) })}
                </p>
              </div>
              <Link to={jobProfileHref}>
                <Button variant="primary" data-testid="open-job-profile">
                  {t('jobProfile.open_editor')}
                </Button>
              </Link>
            </div>
          ) : (
            <EmptyState
              title={t('jobProfile.empty_title')}
              body={t('jobProfile.empty_body')}
              action={
                <PermissionGate permission={PERMISSIONS.JOB_PROFILE_EDIT}>
                  <Button
                    onClick={async () => {
                      await createProfile.mutateAsync();
                    }}
                    data-testid="create-job-profile-cta"
                  >
                    {t('jobProfile.create_button')}
                  </Button>
                </PermissionGate>
              }
            />
          )}
        </Card>
      ) : null}

      {tab === 'job_analysis' ? (
        <Card
          title={t('positions.tab_job_analysis')}
          action={
            <PermissionGate permission={PERMISSIONS.JOB_PROFILE_EDIT}>
              <select
                aria-label={t('jobAnalysis.new_questionnaire')}
                className="h-9 px-3 border border-border-strong rounded-md text-sm bg-surface"
                defaultValue=""
                onChange={async (e) => {
                  const code = e.target.value;
                  if (!code) return;
                  await createQuestionnaire.mutateAsync(code);
                  e.target.value = '';
                }}
                data-testid="new-questionnaire-select"
              >
                <option value="" disabled>
                  {t('jobAnalysis.new_questionnaire')}
                </option>
                {(templatesQuery.data?.items ?? []).map((tpl) => (
                  <option key={tpl.code} value={tpl.code}>
                    {pickLocalized(tpl.name, i18n.language)} ({tpl.question_count})
                  </option>
                ))}
              </select>
            </PermissionGate>
          }
        >
          {questionnairesQuery.isLoading ? (
            <LoadingState />
          ) : (questionnairesQuery.data?.items.length ?? 0) === 0 ? (
            <EmptyState
              title={t('jobAnalysis.empty_title')}
              body={t('jobAnalysis.empty_body')}
            />
          ) : (
            <ul className="divide-y divide-divider" data-testid="questionnaire-list">
              {questionnairesQuery.data!.items.map((q) => {
                const href = `/app/projects/${projectId}/positions/${positionId}/questionnaire/${q.id}`;
                return (
                  <li key={q.id} className="py-3 flex items-center justify-between gap-4">
                    <div>
                      <Link to={href} className="text-sm font-medium text-text-primary hover:underline">
                        {pickLocalized(q.name, i18n.language)}
                      </Link>
                      <p className="text-xs text-text-muted">
                        {q.template_code} · {q.answered_count}/{q.required_count}
                      </p>
                    </div>
                    <Link to={href} className="text-sm text-primary-600 hover:underline">
                      {t('common.edit')}
                    </Link>
                  </li>
                );
              })}
            </ul>
          )}
        </Card>
      ) : null}

      {tab === 'evaluation' ? (
        <Card>
          <EmptyState body={t('positions.stub_evaluation')} />
        </Card>
      ) : null}
      {tab === 'grade' ? (
        <Card>
          <EmptyState body={t('positions.stub_grade')} />
        </Card>
      ) : null}
      {tab === 'audit' ? (
        <PermissionGate
          permission={PERMISSIONS.AUDIT_READ}
          fallback={
            <Card>
              <EmptyState body={t('states.no_access_body')} title={t('states.no_access_title')} />
            </Card>
          }
        >
          <Card title={t('audit.log')}>
            <EmptyState body={t('states.empty_body')} />
          </Card>
        </PermissionGate>
      ) : null}
    </div>
  );
}
