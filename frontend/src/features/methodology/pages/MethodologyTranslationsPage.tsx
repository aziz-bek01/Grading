import { useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ArrowLeft } from 'lucide-react';
import { Breadcrumbs } from '@/shared/components/layout/Breadcrumbs';
import { LoadingState } from '@/shared/components/feedback/LoadingState';
import { ErrorState } from '@/shared/components/feedback/ErrorState';
import { Button } from '@/shared/components/ui/Button';
import { FactorTranslationEditor } from '../components/FactorTranslationEditor';
import { useMethodology, useMethodologyVersion, useUpdateFactor } from '../hooks/useMethodology';
import { updateFactor as updateFactorApi } from '../api/methodologyApi';
import type { Locale } from '@/shared/types/common';

export function MethodologyTranslationsPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { projectId = '', methodologyId = '', versionId = '' } = useParams<{
    projectId: string;
    methodologyId: string;
    versionId: string;
  }>();

  const methodologyQuery = useMethodology(methodologyId);
  const versionQuery = useMethodologyVersion(versionId);

  // We use a single mutation hook per cell, so build it once and reuse with target id.
  // Using a placeholder id; we will call updateFactor with proper id via the api directly.
  const updateMut = useUpdateFactor('', versionId, methodologyId);

  if (methodologyQuery.isLoading || versionQuery.isLoading) return <LoadingState />;
  if (methodologyQuery.error || versionQuery.error)
    return <ErrorState onRetry={() => versionQuery.refetch()} />;
  const methodology = methodologyQuery.data;
  const version = versionQuery.data;
  if (!methodology || !version) return <ErrorState />;

  const readOnly = version.status !== 'DRAFT';

  const handleChangeName = async (factorId: string, locale: Locale, value: string) => {
    // The single-hook approach can't bind to dynamic ids; invoke the api directly.
    const factor = version.factors.find((f) => f.id === factorId);
    if (!factor) return;
    await updateFactorApi(factorId, {
      name_i18n: { ...(factor.name_i18n ?? {}), [locale]: value },
    });
    // Touch the placeholder mutation so React Query invalidations still trigger.
    void updateMut;
    versionQuery.refetch();
  };

  return (
    <div className="space-y-6">
      <Breadcrumbs
        extra={[
          { label: t('nav.methodology'), to: `/app/projects/${projectId}/methodology` },
          { label: methodology.code },
          { label: t('methodology.translations_page_title') },
        ]}
      />
      <header className="flex items-start justify-between gap-4 flex-wrap">
        <div>
          <h1 className="text-2xl text-text-primary">
            {t('methodology.translations_page_title')}
          </h1>
          <p className="text-sm text-text-secondary mt-1">
            {t('methodology.translations_page_subtitle')}
          </p>
        </div>
        <Button
          variant="secondary"
          size="sm"
          leadingIcon={<ArrowLeft size={14} />}
          onClick={() =>
            navigate(
              `/app/projects/${projectId}/methodology/${methodologyId}/versions/${versionId}/edit`,
            )
          }
        >
          {t('common.back')}
        </Button>
      </header>

      <FactorTranslationEditor
        factors={version.factors}
        readOnly={readOnly}
        onChangeName={handleChangeName}
      />
    </div>
  );
}
