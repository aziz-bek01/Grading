/**
 * Modal dialog that captures export type + format + (optional) filter params,
 * then calls POST /exports/request.
 *
 * If the chosen export type is in the salary-bearing set, a warning banner is
 * shown indicating that SALARY_EXPORT permission is required server-side.
 */
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { zodResolver } from '@hookform/resolvers/zod';
import { ShieldAlert, X } from 'lucide-react';
import { useRequestExport } from '../hooks/useExports';
import { ExportRequestFormSchema, type ExportRequestFormValues } from '../schemas/exportSchemas';
import type { ExportFormat, ExportJob, ExportType } from '../types';

interface Props {
  projectId: string;
  open: boolean;
  onClose: () => void;
  onCreated?: (job: ExportJob) => void;
}

const ALL_TYPES: ExportType[] = [
  'POSITION_CATALOG',
  'JOB_PROFILES',
  'METHODOLOGY',
  'EVALUATION_MATRIX',
  'GRADE_STRUCTURE',
  'GRADE_PYRAMID',
  'SALARY_RANGES',
  'RED_GREEN_CIRCLE',
  'COMPENSATION_SCENARIOS',
  'REPORT_EXECUTIVE',
];

const ALL_FORMATS: ExportFormat[] = ['XLSX', 'CSV', 'PDF', 'DOCX'];

const SALARY_BEARING = new Set<ExportType>([
  'SALARY_RANGES',
  'RED_GREEN_CIRCLE',
  'COMPENSATION_SCENARIOS',
]);

export function ExportRequestDialog({ projectId, open, onClose, onCreated }: Props) {
  const { t } = useTranslation();
  const [submitting, setSubmitting] = useState(false);
  const mutation = useRequestExport();

  const form = useForm<ExportRequestFormValues>({
    resolver: zodResolver(ExportRequestFormSchema),
    defaultValues: {
      projectId,
      exportType: 'POSITION_CATALOG',
      format: 'XLSX',
      filterParams: '',
    },
  });

  if (!open) return null;
  const watchedType = form.watch('exportType');
  const showSalaryWarning = SALARY_BEARING.has(watchedType);

  const onSubmit = form.handleSubmit(async (vals) => {
    setSubmitting(true);
    try {
      const job = await mutation.mutateAsync({
        ...vals,
        filterParams: vals.filterParams || null,
      });
      onCreated?.(job);
      onClose();
    } finally {
      setSubmitting(false);
    }
  });

  return (
    <div
      role="dialog"
      aria-modal="true"
      className="fixed inset-0 z-50 bg-black/30 flex items-center justify-center p-4"
      data-testid="export-request-dialog"
    >
      <div className="bg-surface rounded-lg shadow-lg w-full max-w-md max-h-[90vh] overflow-y-auto">
        <header className="flex items-center justify-between p-4 border-b border-border">
          <h2 className="text-lg font-semibold">{t('export.request.title')}</h2>
          <button
            type="button"
            onClick={onClose}
            className="p-1 rounded hover:bg-divider"
            aria-label={t('common.close')}
          >
            <X size={16} />
          </button>
        </header>
        <form onSubmit={onSubmit} className="p-4 space-y-3">
          <label className="block text-sm">
            <span className="block mb-1 text-text-secondary">{t('export.request.type')}</span>
            <select
              {...form.register('exportType')}
              className="w-full border border-border rounded px-2 py-1.5 bg-surface"
              data-testid="export-request-type"
            >
              {ALL_TYPES.map((typ) => (
                <option key={typ} value={typ}>
                  {t(`export.type.${typ}`)}
                </option>
              ))}
            </select>
          </label>
          <label className="block text-sm">
            <span className="block mb-1 text-text-secondary">{t('export.request.format')}</span>
            <select
              {...form.register('format')}
              className="w-full border border-border rounded px-2 py-1.5 bg-surface"
              data-testid="export-request-format"
            >
              {ALL_FORMATS.map((f) => (
                <option key={f} value={f}>
                  {t(`export.format.${f}`)}
                </option>
              ))}
            </select>
          </label>
          <label className="block text-sm">
            <span className="block mb-1 text-text-secondary">
              {t('export.request.filter_params')}
            </span>
            <textarea
              {...form.register('filterParams')}
              rows={2}
              className="w-full border border-border rounded px-2 py-1.5 bg-surface text-sm"
              placeholder={t('export.request.filter_placeholder')}
            />
          </label>

          {showSalaryWarning ? (
            <div
              role="alert"
              className="flex items-start gap-2 p-2 rounded border border-salary-sensitive/30 bg-salary-sensitive-bg text-salary-sensitive text-xs"
              data-testid="export-salary-warning"
            >
              <ShieldAlert size={14} aria-hidden className="mt-0.5" />
              <span>{t('export.request.salary_warning')}</span>
            </div>
          ) : null}

          <div className="flex justify-end gap-2 pt-2">
            <button
              type="button"
              onClick={onClose}
              className="px-3 py-1.5 rounded-md border border-border text-sm"
              disabled={submitting}
            >
              {t('common.cancel')}
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="px-3 py-1.5 rounded-md bg-primary-500 text-text-inverse text-sm disabled:opacity-50"
              data-testid="export-request-submit"
            >
              {submitting ? t('common.loading') : t('export.request.submit')}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
