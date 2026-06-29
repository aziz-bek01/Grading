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
import { Controller, useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { zodResolver } from '@hookform/resolvers/zod';
import { X } from 'lucide-react';
import { ApiError } from '@/shared/api/apiError';
import { DateRangeFields } from '@/shared/components/form/DateRangeFields';
import { EvaluatorPicker } from '@/features/evaluation/components/panel/EvaluatorPicker';
import { useRequestReport } from '../hooks/useReports';
import {
  RequestReportSchema,
  supportsEvaluationFilter,
  type RequestReportFormValues,
} from '../schemas/reportSchemas';
import type { Report, ReportFormat, ReportType } from '../types';
import { REPORT_FORMAT_AVAILABILITY } from '../types';
import { MethodologyVersionSelect } from './MethodologyVersionSelect';

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
  const [serverError, setServerError] = useState<string | null>(null);
  const mutation = useRequestReport();

  const form = useForm<RequestReportFormValues>({
    resolver: zodResolver(RequestReportSchema),
    defaultValues: {
      projectId,
      reportType: 'GRADE_DISTRIBUTION',
      format: 'PDF',
      filterParams: '',
      evaluationFilter: {
        methodologyVersionIds: [],
        dateFrom: '',
        dateTo: '',
        evaluatorUserIds: [],
      },
    },
  });

  const watchedType = form.watch('reportType');
  const watchedFormat = form.watch('format');
  const availableFormats = REPORT_FORMAT_AVAILABILITY[watchedType] ?? [];
  const showEvaluationFilter = supportsEvaluationFilter(watchedType);

  // Auto-correct an unsupported format when the type changes.
  useEffect(() => {
    if (availableFormats.length === 0) return;
    if (!availableFormats.includes(watchedFormat)) {
      form.setValue('format', availableFormats[0], { shouldValidate: true });
    }
  }, [watchedType, watchedFormat, availableFormats, form]);

  if (!open) return null;

  const onSubmit = form.handleSubmit(
    async (vals) => {
      setSubmitting(true);
      setServerError(null);
      try {
        const report = await mutation.mutateAsync({
          ...vals,
          filterParams: vals.filterParams || null,
          evaluationFilter:
            supportsEvaluationFilter(vals.reportType)
              ? {
                  methodology_version_ids: vals.evaluationFilter?.methodologyVersionIds ?? [],
                  date_from: vals.evaluationFilter?.dateFrom ?? '',
                  date_to: vals.evaluationFilter?.dateTo ?? '',
                  evaluator_user_ids: vals.evaluationFilter?.evaluatorUserIds ?? [],
                }
              : null,
        });
        onCreated?.(report);
        onClose();
      } catch (err) {
        // Surface the failure instead of swallowing it — a request that 4xx/5xx'd
        // previously left the dialog open with NO feedback (looked like "nothing
        // happened"). Map the known request-time filter codes to friendly copy;
        // fall back to a generic message that carries the code + correlation id so
        // the failure is reportable.
        setServerError(describeRequestError(err, t));
      } finally {
        setSubmitting(false);
      }
    },
    (errors) => {
      // Client-side validation blocked the submit BEFORE any network call, so the
      // catch above never runs. Without this the click was a silent no-op when an
      // invalid field has no inline message (e.g. projectId) — which reads exactly
      // as "the request isn't even being created". Name the offending field(s) so
      // the block is visible and reportable.
      const fields = Object.keys(errors);
      setServerError(
        t('report.error.validation_blocked', { fields: fields.join(', ') || '—' }),
      );
    },
  );

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
          {showEvaluationFilter ? (
            <div
              className="space-y-3 border border-border rounded-md p-3"
              data-testid="report-request-evaluation-filters"
            >
              <h3 className="text-sm font-medium text-text-primary">
                {t('report.filter.section_title')}
              </h3>

              <div>
                <span className="block mb-1 text-xs text-text-secondary">
                  {t('report.filter.methodology_label')}
                  <span className="text-danger-700"> *</span>
                </span>
                <Controller
                  control={form.control}
                  name="evaluationFilter.methodologyVersionIds"
                  render={({ field }) => (
                    <MethodologyVersionSelect
                      projectId={projectId}
                      // Exactly one methodology version is required (each defines
                      // its own factors); the array carrier holds 0 or 1 id.
                      value={field.value?.[0] ?? null}
                      onChange={(id) => field.onChange(id ? [id] : [])}
                    />
                  )}
                />
                {form.formState.errors.evaluationFilter?.methodologyVersionIds?.message ? (
                  <span className="block text-xs text-danger-700 mt-1" role="alert">
                    {t(form.formState.errors.evaluationFilter.methodologyVersionIds.message)}
                  </span>
                ) : (
                  <p className="text-xs text-text-muted mt-1">
                    {t('report.filter.methodology_required_hint')}
                  </p>
                )}
              </div>

              <div>
                <span className="block mb-1 text-xs text-text-secondary">
                  {t('report.filter.date_label')}
                </span>
                <Controller
                  control={form.control}
                  name="evaluationFilter"
                  render={({ field }) => (
                    <div className="flex items-center gap-3 flex-wrap">
                      <DateRangeFields
                        value={{
                          from: field.value?.dateFrom || undefined,
                          to: field.value?.dateTo || undefined,
                        }}
                        onChange={(next) =>
                          field.onChange({
                            ...field.value,
                            dateFrom: next.from ?? '',
                            dateTo: next.to ?? '',
                          })
                        }
                        testIdPrefix="report-filter-date"
                        fromLabelKey="report.filter.date_from"
                        toLabelKey="report.filter.date_to"
                      />
                    </div>
                  )}
                />
                {form.formState.errors.evaluationFilter?.dateTo?.message ? (
                  <span className="block text-xs text-danger-700 mt-1" role="alert">
                    {t(form.formState.errors.evaluationFilter.dateTo.message)}
                  </span>
                ) : null}
              </div>

              <div>
                <span className="block mb-1 text-xs text-text-secondary">
                  {t('report.filter.evaluator_label')}
                </span>
                <Controller
                  control={form.control}
                  name="evaluationFilter.evaluatorUserIds"
                  render={({ field }) => (
                    <EvaluatorPicker
                      mode="multi"
                      testIdPrefix="report-filter-evaluator"
                      value={field.value ?? []}
                      onChange={field.onChange}
                    />
                  )}
                />
                <p className="text-xs text-text-muted mt-1">
                  {t('report.filter.evaluator_empty_hint')}
                </p>
              </div>
            </div>
          ) : null}

          {serverError ? (
            <p
              className="text-sm text-danger-700 border border-danger-200 bg-danger-50 rounded-md px-3 py-2"
              role="alert"
              data-testid="report-request-error"
            >
              {serverError}
            </p>
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

/**
 * Map a failed report-request to localized copy. Known request-time filter
 * codes (mirrors {@code EvaluationReportFilterValidator}) get friendly,
 * actionable messages; everything else falls back to a generic message that
 * still carries the backend error code + correlation id so the failure is
 * reportable (the dialog previously swallowed all of this).
 */
function describeRequestError(
  err: unknown,
  t: (key: string, opts?: Record<string, unknown>) => string,
): string {
  if (err instanceof ApiError) {
    if (err.isForbidden()) return t('report.error.permission_denied');
    if (err.code === 'REPORT_FILTER_INVALID_METHODOLOGY') {
      return t('report.error.invalid_methodology');
    }
    if (err.code === 'REPORT_FILTER_METHODOLOGY_REQUIRED') {
      return t('report.error.methodology_required');
    }
    if (err.code === 'REPORT_FILTER_INVALID_DATE_RANGE') {
      return t('report.error.invalid_date_range');
    }
    if (err.code === 'REPORT_FILTER_MALFORMED') {
      return t('report.error.malformed_filter');
    }
    return t('report.error.request_failed', {
      code: err.code,
      ref: err.correlationId ?? err.traceId ?? '—',
    });
  }
  return t('report.error.request_failed', { code: 'NETWORK', ref: '—' });
}
