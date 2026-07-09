/**
 * 4-step Excel import wizard.
 *
 * Step 1: Choose template (permission-gated)
 * Step 2: Upload file (FileDropzone + Zod)
 * Step 3: Review validation — polls every 2s while in-flight, shows progress
 *         indicator + summary + errors table
 * Step 4: Confirm commit (POST /imports/{id}/commit) + success page
 */
import { useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ChevronRight, Check, Download, FileText } from 'lucide-react';
import { useAuthStore } from '@/features/auth/authStore';
import { PERMISSIONS, type PermissionCode } from '@/shared/types/permissions';
import { LoadingState } from '@/shared/components/feedback/LoadingState';
import { ErrorState } from '@/shared/components/feedback/ErrorState';
import { cn } from '@/shared/lib/cn';
import {
  useCancelImport,
  useCommitImport,
  useImport,
  useImportErrors,
  useUploadImport,
} from '../hooks/useImports';
import { downloadTemplate } from '../api/templateDownload';
import { FileDropzone } from '../components/FileDropzone';
import { ImportProgressIndicator } from '../components/ImportProgressIndicator';
import { ImportSummaryCard } from '../components/ImportSummaryCard';
import { ImportErrorsTable } from '../components/ImportErrorsTable';
import type { ImportTemplateCode } from '../types';

interface TemplateRow {
  code: ImportTemplateCode;
  permission: PermissionCode;
  /**
   * Whether the backend can actually COMMIT this template. Only templates with
   * a registered `ImportRowCommitter` (ORG_STRUCTURE_V1, POSITION_CATALOG_V1,
   * JOB_PROFILE_V1, GRADE_BANDS_V1) can be committed; METHODOLOGY_FACTORS_V1
   * has no committer and `CommitImportBatchUseCase` rejects it with
   * `COMMIT_NOT_SUPPORTED`. We keep it visible (so users know it's coming) but
   * non-selectable so nobody completes the whole wizard then hits a hard error.
   */
  commitSupported: boolean;
}

const TEMPLATES: TemplateRow[] = [
  { code: 'ORG_STRUCTURE_V1', permission: PERMISSIONS.ORG_IMPORT, commitSupported: true },
  { code: 'POSITION_CATALOG_V1', permission: PERMISSIONS.POSITION_IMPORT, commitSupported: true },
  { code: 'JOB_PROFILE_V1', permission: PERMISSIONS.POSITION_IMPORT, commitSupported: true },
  { code: 'METHODOLOGY_FACTORS_V1', permission: PERMISSIONS.METHODOLOGY_IMPORT, commitSupported: false },
  { code: 'GRADE_BANDS_V1', permission: PERMISSIONS.GRADE_IMPORT, commitSupported: true },
];

type Step = 1 | 2 | 3 | 4;

export function ImportWizardPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { projectId = '' } = useParams<{ projectId: string }>();
  const user = useAuthStore((s) => s.user);
  const has = (p: PermissionCode) => user?.permissions.includes(p) ?? false;

  const [step, setStep] = useState<Step>(1);
  const [templateCode, setTemplateCode] = useState<ImportTemplateCode | null>(null);
  const [file, setFile] = useState<File | null>(null);
  const [batchId, setBatchId] = useState<string | null>(null);

  const upload = useUploadImport();
  const detail = useImport(batchId ?? undefined, { pollWhileInFlight: true });
  const errors = useImportErrors(batchId ?? undefined, { page: 0, size: 200 });
  const commit = useCommitImport(batchId ?? '');
  const cancel = useCancelImport(batchId ?? '');

  const allowedTemplates = useMemo(
    () => TEMPLATES.filter((t) => has(t.permission)),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [user?.permissions],
  );

  // Once the batch is committed, auto-advance to step 4. Done during render via
  // a one-shot state guard (React's "adjust state when external data changes"
  // pattern) instead of a setState-in-effect. The guard ensures we advance only
  // on the transition into the committed state, never on every poll tick.
  const [autoAdvanced, setAutoAdvanced] = useState(false);
  if (
    step === 3 &&
    !commit.isPending &&
    (detail.data?.status === 'COMMITTED' || detail.data?.status === 'PARTIALLY_COMMITTED') &&
    !autoAdvanced
  ) {
    setAutoAdvanced(true);
    setStep(4);
  }

  const onUpload = async () => {
    if (!templateCode || !file) return;
    const res = await upload.mutateAsync({ file, templateCode, projectId });
    setBatchId(res.id);
    setStep(3);
  };

  const onCommit = async () => {
    await commit.mutateAsync();
  };

  const onCancelWizard = async () => {
    if (batchId) await cancel.mutateAsync();
    navigate(`/app/projects/${projectId}/imports`);
  };

  return (
    <section className="p-6 space-y-4" data-testid="import-wizard-page">
      <header>
        <h1 className="text-2xl font-semibold">{t('import.wizard.title')}</h1>
        <p className="text-sm text-text-muted">{t('import.wizard.subtitle')}</p>
      </header>

      <Stepper step={step} />

      {step === 1 ? (
        <StepChooseTemplate
          templates={allowedTemplates}
          selected={templateCode}
          onSelect={setTemplateCode}
          onNext={() => setStep(2)}
        />
      ) : null}

      {step === 2 ? (
        <StepUploadFile
          templateCode={templateCode}
          file={file}
          setFile={setFile}
          uploading={upload.isPending}
          onBack={() => setStep(1)}
          onUpload={onUpload}
        />
      ) : null}

      {step === 3 ? (
        <StepReviewValidation
          batch={detail.data}
          errors={errors.data?.items ?? []}
          loadingDetail={detail.isLoading}
          loadingErrors={errors.isLoading}
          errorDetail={detail.isError}
          onRetryDetail={() => detail.refetch()}
          onContinue={() => setStep(4)}
          onCancel={onCancelWizard}
        />
      ) : null}

      {step === 4 ? (
        <StepConfirmCommit
          batch={detail.data}
          onCommit={onCommit}
          committing={commit.isPending}
          onClose={() => navigate(`/app/projects/${projectId}/imports`)}
        />
      ) : null}
    </section>
  );
}

function Stepper({ step }: { step: Step }) {
  const { t } = useTranslation();
  const steps: { idx: Step; label: string }[] = [
    { idx: 1, label: t('import.wizard.step1') },
    { idx: 2, label: t('import.wizard.step2') },
    { idx: 3, label: t('import.wizard.step3') },
    { idx: 4, label: t('import.wizard.step4') },
  ];
  return (
    <ol className="flex items-center gap-2 flex-wrap" data-testid="wizard-stepper">
      {steps.map((s, i) => {
        const done = s.idx < step;
        const current = s.idx === step;
        return (
          <li key={s.idx} className="flex items-center gap-2">
            <span
              className={cn(
                'inline-flex items-center justify-center w-6 h-6 rounded-full text-xs font-medium border',
                done
                  ? 'bg-success-50 text-success-700 border-success-500/30'
                  : current
                    ? 'bg-primary-500 text-text-inverse border-primary-500'
                    : 'bg-divider text-text-muted border-border',
              )}
            >
              {done ? <Check size={12} /> : s.idx}
            </span>
            <span
              className={cn(
                'text-sm',
                current ? 'font-medium text-text-primary' : 'text-text-muted',
              )}
            >
              {s.label}
            </span>
            {i < steps.length - 1 ? (
              <ChevronRight size={14} className="text-text-muted" aria-hidden />
            ) : null}
          </li>
        );
      })}
    </ol>
  );
}

function StepChooseTemplate({
  templates,
  selected,
  onSelect,
  onNext,
}: {
  templates: TemplateRow[];
  selected: ImportTemplateCode | null;
  onSelect: (c: ImportTemplateCode) => void;
  onNext: () => void;
}) {
  const { t } = useTranslation();
  return (
    <div className="space-y-3" data-testid="wizard-step-template">
      {templates.length === 0 ? (
        <div className="text-sm text-text-muted p-4 border border-border rounded-md">
          {t('import.wizard.no_templates_available')}
        </div>
      ) : (
        <div className="grid sm:grid-cols-2 gap-3">
          {templates.map((tpl) => (
            <div
              key={tpl.code}
              data-testid={`wizard-template-${tpl.code}`}
              data-commit-supported={tpl.commitSupported}
              className={cn(
                'p-4 rounded-md border-2 transition flex flex-col gap-3',
                !tpl.commitSupported
                  ? 'border-border opacity-70'
                  : selected === tpl.code
                    ? 'border-primary-500 bg-primary-50'
                    : 'border-border hover:border-primary-500',
              )}
            >
              <button
                type="button"
                onClick={() => onSelect(tpl.code)}
                disabled={!tpl.commitSupported}
                aria-disabled={!tpl.commitSupported}
                className="text-left disabled:cursor-not-allowed"
                data-testid={`wizard-template-${tpl.code}-select`}
              >
                <div className="font-medium text-text-primary flex items-center gap-2 flex-wrap">
                  {t(`import.template.${tpl.code}`)}
                  {!tpl.commitSupported ? (
                    <span
                      className="inline-flex items-center text-xs font-medium px-2 py-0.5 rounded-full bg-warning-50 text-warning-700 border border-warning-500/30"
                      data-testid={`wizard-template-${tpl.code}-unsupported`}
                    >
                      {t('import.template_not_supported')}
                    </span>
                  ) : null}
                </div>
                <div className="text-xs text-text-muted mt-1">
                  {tpl.commitSupported
                    ? t(`import.template_desc.${tpl.code}`)
                    : t('import.template_not_supported_hint')}
                </div>
              </button>
              <div className="flex items-center gap-2 flex-wrap">
                <button
                  type="button"
                  onClick={() => void downloadTemplate(tpl.code, 'empty')}
                  data-testid={`wizard-template-${tpl.code}-empty`}
                  className="inline-flex items-center gap-1 text-xs px-2 py-1 rounded border border-border hover:bg-divider"
                >
                  <Download size={12} aria-hidden />
                  {t('import.template.download_empty')}
                </button>
                <button
                  type="button"
                  onClick={() => void downloadTemplate(tpl.code, 'sample')}
                  data-testid={`wizard-template-${tpl.code}-sample`}
                  className="inline-flex items-center gap-1 text-xs px-2 py-1 rounded text-text-secondary hover:bg-divider"
                >
                  <FileText size={12} aria-hidden />
                  {t('import.template.download_sample')}
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
      <div className="flex justify-end">
        <button
          type="button"
          disabled={!selected}
          onClick={onNext}
          className="px-3 py-2 rounded-md bg-primary-500 text-text-inverse text-sm disabled:opacity-50"
          data-testid="wizard-next-1"
        >
          {t('common.continue')}
        </button>
      </div>
    </div>
  );
}

function StepUploadFile({
  templateCode,
  file,
  setFile,
  uploading,
  onBack,
  onUpload,
}: {
  templateCode: ImportTemplateCode | null;
  file: File | null;
  setFile: (f: File | null) => void;
  uploading: boolean;
  onBack: () => void;
  onUpload: () => void;
}) {
  const { t } = useTranslation();
  return (
    <div className="space-y-3" data-testid="wizard-step-upload">
      <div className="text-sm text-text-secondary">
        {t('import.wizard.upload_for_template', {
          template: templateCode ? t(`import.template.${templateCode}`) : '',
        })}
      </div>
      <FileDropzone file={file} onFileChange={setFile} disabled={uploading} />
      <div className="flex items-center justify-between">
        <button
          type="button"
          onClick={onBack}
          className="px-3 py-2 rounded-md border border-border text-sm"
          disabled={uploading}
        >
          {t('common.back')}
        </button>
        <button
          type="button"
          onClick={onUpload}
          disabled={!file || !templateCode || uploading}
          className="px-3 py-2 rounded-md bg-primary-500 text-text-inverse text-sm disabled:opacity-50"
          data-testid="wizard-upload-submit"
        >
          {uploading ? t('common.loading') : t('import.wizard.upload')}
        </button>
      </div>
    </div>
  );
}

function StepReviewValidation({
  batch,
  errors,
  loadingDetail,
  loadingErrors,
  errorDetail,
  onRetryDetail,
  onContinue,
  onCancel,
}: {
  batch: ReturnType<typeof useImport>['data'];
  errors: Parameters<typeof ImportErrorsTable>[0]['errors'];
  loadingDetail: boolean;
  loadingErrors: boolean;
  errorDetail: boolean;
  onRetryDetail: () => void;
  onContinue: () => void;
  onCancel: () => void;
}) {
  const { t } = useTranslation();
  if (errorDetail) {
    return <ErrorState onRetry={onRetryDetail} />;
  }
  if (loadingDetail || !batch) {
    return <LoadingState />;
  }
  const canContinue =
    batch.status === 'READY_FOR_REVIEW' || batch.status === 'READY_TO_COMMIT';
  return (
    <div className="space-y-4" data-testid="wizard-step-review">
      <ImportProgressIndicator status={batch.status} />
      <ImportSummaryCard batch={batch} />
      <ImportErrorsTable errors={errors} loading={loadingErrors} />
      <div className="flex items-center justify-between gap-2">
        <button
          type="button"
          onClick={onCancel}
          className="px-3 py-2 rounded-md border border-border text-sm"
        >
          {t('import.wizard.cancel')}
        </button>
        <button
          type="button"
          onClick={onContinue}
          disabled={!canContinue}
          className="px-3 py-2 rounded-md bg-primary-500 text-text-inverse text-sm disabled:opacity-50"
          data-testid="wizard-continue-commit"
        >
          {t('import.wizard.continue_commit')}
        </button>
      </div>
    </div>
  );
}

function StepConfirmCommit({
  batch,
  onCommit,
  committing,
  onClose,
}: {
  batch: ReturnType<typeof useImport>['data'];
  onCommit: () => void;
  committing: boolean;
  onClose: () => void;
}) {
  const { t } = useTranslation();
  if (!batch) return null;
  const isCommitted = batch.status === 'COMMITTED' || batch.status === 'PARTIALLY_COMMITTED';
  return (
    <div className="space-y-3" data-testid="wizard-step-commit">
      <ImportSummaryCard batch={batch} />
      {!isCommitted ? (
        <div className="flex justify-end">
          <button
            type="button"
            onClick={onCommit}
            disabled={committing}
            className="px-3 py-2 rounded-md bg-primary-500 text-text-inverse text-sm disabled:opacity-50"
            data-testid="wizard-commit-submit"
          >
            {committing
              ? t('common.loading')
              : t('import.wizard.commit_rows', { count: batch.totalRowCount ?? 0 })}
          </button>
        </div>
      ) : (
        <div className="space-y-2">
          <div className="text-sm text-success-700">
            {t('import.wizard.commit_success', { count: batch.committedRowCount ?? 0 })}
          </div>
          <button
            type="button"
            onClick={onClose}
            className="px-3 py-2 rounded-md border border-border text-sm"
          >
            {t('common.close')}
          </button>
        </div>
      )}
    </div>
  );
}
