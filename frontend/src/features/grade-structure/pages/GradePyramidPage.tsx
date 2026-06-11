import { useMemo } from 'react';
import { useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Breadcrumbs } from '@/shared/components/layout/Breadcrumbs';
import { LoadingState } from '@/shared/components/feedback/LoadingState';
import { ErrorState } from '@/shared/components/feedback/ErrorState';
import { Card } from '@/shared/components/ui/Card';
import { useAuthStore } from '@/features/auth/authStore';
import { pickLocalized } from '@/shared/lib/localized';
import { GradePyramid } from '../components/GradePyramid';
import {
  useGradePyramid,
  useGradeStructure,
} from '../hooks/useGradeStructure';
import { routes } from '@/shared/config/routes';
import type { Locale } from '@/shared/types/common';

export function GradePyramidPage() {
  const { t, i18n } = useTranslation();
  const { projectId = '', gradeStructureId = '' } = useParams<{
    projectId: string;
    gradeStructureId: string;
  }>();
  const currentLocale = (useAuthStore((s) => s.user?.locale) ??
    (i18n.language as Locale)) as Locale;
  const structure = useGradeStructure(gradeStructureId);
  const pyramid = useGradePyramid(gradeStructureId);

  const totals = useMemo(() => {
    const rows = pyramid.data?.rows ?? [];
    const positions = rows.reduce((acc, r) => acc + r.position_count, 0);
    const evaluations = rows.reduce((acc, r) => acc + r.evaluation_count, 0);
    return { positions, evaluations, grades: rows.length };
  }, [pyramid.data]);

  if (structure.isLoading || pyramid.isLoading) return <LoadingState />;
  if (structure.error || pyramid.error)
    return <ErrorState onRetry={() => pyramid.refetch()} />;
  if (!structure.data) return <ErrorState />;

  return (
    <div className="space-y-6">
      <Breadcrumbs
        extra={[
          { label: t('nav.grades'), to: routes.projectGrades(projectId) },
          {
            label:
              pickLocalized(structure.data.name_i18n, currentLocale) ||
              structure.data.code,
          },
          { label: t('gradeStructure.pyramid.title') },
        ]}
      />

      <header>
        <h1 className="text-2xl text-text-primary">
          {t('gradeStructure.pyramid.title')}
        </h1>
        <p className="text-sm text-text-secondary mt-1">
          {t('gradeStructure.pyramid.subtitle')}
        </p>
      </header>

      <div className="grid grid-cols-1 lg:grid-cols-[1fr_18rem] gap-6">
        <Card title={t('gradeStructure.pyramid.title')}>
          <GradePyramid rows={pyramid.data?.rows ?? []} />
        </Card>
        <aside className="space-y-3">
          <Card compact title={t('gradeStructure.pyramid.summary_title')}>
            <dl className="text-sm space-y-1">
              <div className="flex justify-between">
                <dt className="text-text-secondary">
                  {t('gradeStructure.pyramid.total_grades')}
                </dt>
                <dd className="tabular-nums">{totals.grades}</dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-text-secondary">
                  {t('gradeStructure.pyramid.total_positions')}
                </dt>
                <dd className="tabular-nums">{totals.positions}</dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-text-secondary">
                  {t('gradeStructure.pyramid.total_evaluations')}
                </dt>
                <dd className="tabular-nums">{totals.evaluations}</dd>
              </div>
            </dl>
            <p className="text-[10px] text-text-muted mt-3">
              {t('gradeStructure.pyramid.salary_hidden_note')}
            </p>
          </Card>
        </aside>
      </div>
    </div>
  );
}
