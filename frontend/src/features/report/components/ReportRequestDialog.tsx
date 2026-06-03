/**
 * Modal dialog that captures report type + format + (optional) filter params,
 * then calls POST /reports/request.
 *
 * Behaviour:
 *   - Format dropdown reflects per-type availability (REPORT_FORMAT_AVAILABILITY).
 *     Unsupported options are disabled (e.g. EXECUTIVE_SUMMARY only allows PDF).
 *   - When the user picks a new type, if the currently-selected format is no
 *     longer supported, we auto-switch to the first available format for that
 *     type so the submit is always valid.
 *   - Submit re-validates against the Zod schema (which encodes the same
 *     availability matrix) — defence in depth.
 */
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { zodResolver } from '@hookform/resolvers/zod';
import { X } from 'lucide-react';
import { useRequestReport } from '../hooks/useReports';
import {
  RequestReportSchema,
  type RequestReportFormValues,
} from '../schemas/reportSchemas';
import type { Report, ReportFormat, ReportType } from '../types';
import { REPORT_FORMAT_AVAILABILITY } from '../types';

interface Props {
  projectId: string;
  open: boolean;
  onClose: () => void;
  onCreated?: (report: Report) => void;
}

const ALL_TYPES: ReportType[] = [
  'GRADE_DISTRIBUTION',
  'POSITION_CATALOG',
  'EVALUATION_SUMMARY',
  'METHODOLOGY_SPEC',
  'AUDIT_SUMMARY',
  'EXECUTIVE_SUMMARY',
];

const ALL_FORMATS: ReportFormat[] = ['PDF', 'DOCX', 'XLSX'];

export function ReportRequestDialog({ projectId, open, onClose, onCreated }: Props) {
  const { t } = useTranslation();
  const [submitting, setSubmitting] = useState(false);
  const mutation = useRequestReport();

  const form = useForm<RequestReportFormValues>({
    resolver: zodResolver(RequestReportSchema),
    defaultValues: {
      projectId,
      reportType: 'GRADE_DISTRIBUTION',
      format: 'PDF',
      filterParams: '',
    },
  });

  const watchedType = form.watch('reportType');
  const watchedFormat = form.watch('format');
  const availableFormats = REPORT_FORMAT_AVAILABILITY[watchedType] ?? [];

  // Auto-correct an unsupported format when the type changes.
  useEffect(() => {
    if (availableFormats.length === 0) return;
    if (!availableFormats.includes(watchedFormat)) {
      form.setValue('format', availableFormats[0], { shouldValidate: true });
    }
  }, [watchedType, watchedFormat, availableFormats, form]);

  if (!open) return null;

  const onSubmit = form.handleSubmit(async (vals) => {
    setSubmitting(true);
    try {
      const report = await mutation.mutateAsync({
        ...vals,
        filterParams: vals.filterParams || null,
      });
      onCreated?.(report);
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
      data-testid="report-request-dialog"
    >
      <div className="bg-surface rounded-lg shadow-lg w-full max-w-md max-h-[90vh] overflow-y-auto">
        <header className="flex items-center justify-between p-4 border-b border-border">
          <h2 className="text-lg font-semibold">{t('report.request_dialog_title')}</h2>
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
            <span className="block mb-1 text-text-secondary">{t('report.field_type')}</span>
            <select
              {...form.register('reportType')}
              className="w-full border border-border rounded px-2 py-1.5 bg-surface"
              data-testid="report-request-type"
            >
              {ALL_TYPES.map((typ) => (
                <option key={typ} value={typ}>
                  {t(`report.type.${typ}`)}
                </option>
              ))}
            </select>
          </label>
          <label className="block text-sm">
            <span className="block mb-1 text-text-secondary">{t('report.field_format')}</span>
            <select
              {...form.register('format')}
              className="w-full border border-border rounded px-2 py-1.5 bg-surface"
              data-testid="report-request-format"
            >
              {ALL_FORMATS.map((f) => {
                const supported = availableFormats.includes(f);
                return (
                  <option key={f} value={f} disabled={!supported}>
                    {t(`report.format.${f}`)}
                    {supported ? '' : ` — ${t('report.format_unavailable')}`}
                  </option>
                );
              })}
            </select>
            {form.formState.errors.format?.message ? (
              <span className="text-xs text-danger-700" role="alert">
                {t(form.formState.errors.format.message)}
              </span>
            ) : null}
          </label>
          <label className="block text-sm">
            <span className="block mb-1 text-text-secondary">
              {t('report.field_filter_params')}
            </span>
            <textarea
              {...form.register('filterParams')}
              rows={2}
              className="w-full border border-border rounded px-2 py-1.5 bg-surface text-sm"
              placeholder={t('report.filter_placeholder')}
            />
          </label>

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
              data-testid="report-request-submit"
            >
              {submitting ? t('common.loading') : t('report.request_submit')}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
