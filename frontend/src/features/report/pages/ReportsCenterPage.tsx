/**
 * Reports Center — landing page for MVP 2 Phase 3.
 *
 * Replaces the previous SOON placeholder. Lists reports for the active
 * project with filters (status / type / format), an in-permission "+
 * Request report" CTA gated by REPORT_CREATE, and per-row actions
 * (Download via SignedDownloadButton if GENERATED, Cancel via mutation
 * while still pre-GENERATED).
 *
 * Permissions:
 *   - REPORT_READ guards the route itself (router.tsx).
 *   - REPORT_CREATE shows / hides the "+ Request report" button.
 *   - REPORT_EXPORT is required server-side to fetch the signed download
 *     URL; we still show the button for GENERATED reports and let the
 *     backend reject (defence in depth).
 */
import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Plus, ShieldAlert } from 'lucide-react';
import { useAuthStore } from '@/features/auth/authStore';
import { PERMISSIONS } from '@/shared/types/permissions';
import {
  useCancelReport,
  useReports,
} from '../hooks/useReports';
import { ReportStatusBadge } from '../components/ReportStatusBadge';
import { ReportTypeBadge } from '../components/ReportTypeBadge';
import { ReportFormatBadge } from '../components/ReportFormatBadge';
import { ReportRequestDialog } from '../components/ReportRequestDialog';
import { ReportSignedDownloadButton } from '../components/ReportSignedDownloadButton';
import {
  REPORT_DOWNLOADABLE_STATUSES,
  REPORT_IN_FLIGHT_STATUSES,
  type Report,
  type ReportFormat,
  type ReportStatus,
  type ReportType,
} from '../types';

const ALL_STATUSES: ReportStatus[] = [
  'REQUESTED',
  'QUEUED',
  'GENERATING',
  'GENERATED',
  'FAILED',
  'DOWNLOADED',
  'EXPIRED',
  'CANCELLED',
];
const ALL_TYPES: ReportType[] = [
  'GRADE_DISTRIBUTION',
  'POSITION_CATALOG',
  'EVALUATION_SUMMARY',
  'METHODOLOGY_SPEC',
  'AUDIT_SUMMARY',
  'EXECUTIVE_SUMMARY',
];
const ALL_FORMATS: ReportFormat[] = ['PDF', 'DOCX', 'XLSX'];

const PAGE_SIZE = 200; // backend MAX_PAGE_SIZE

export function ReportsCenterPage() {
  const { t } = useTranslation();
  const { projectId = '' } = useParams<{ projectId: string }>();
  const user = useAuthStore((s) => s.user);
  const canCreate = !!user?.permissions.includes(PERMISSIONS.REPORT_CREATE);

  const [dialogOpen, setDialogOpen] = useState(false);
  const [statusFilter, setStatusFilter] = useState<ReportStatus | ''>('');
  const [typeFilter, setTypeFilter] = useState<ReportType | ''>('');
  const [formatFilter, setFormatFilter] = useState<ReportFormat | ''>('');

  const query = useReports({
    projectId,
    status: statusFilter || undefined,
    type: typeFilter || undefined,
    format: formatFilter || undefined,
    page: 0,
    size: PAGE_SIZE,
  });

  return (
    <section className="p-6 space-y-4" data-testid="reports-center-page">
      <header className="flex items-center justify-between gap-2 flex-wrap">
        <div>
          <h1 className="text-2xl font-semibold">{t('report.page_title')}</h1>
          <p className="text-sm text-text-muted">{t('report.page_subtitle')}</p>
        </div>
        {canCreate ? (
          <button
            type="button"
            onClick={() => setDialogOpen(true)}
            className="inline-flex items-center gap-2 px-3 py-2 rounded-md bg-primary-500 text-text-inverse hover:bg-primary-700 text-sm"
            data-testid="report-new-button"
          >
            <Plus size={16} /> {t('report.new_report')}
          </button>
        ) : null}
      </header>

      <div className="flex flex-wrap items-center gap-2">
        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value as ReportStatus | '')}
          className="text-sm border border-border rounded px-2 py-1.5 bg-surface"
          data-testid="report-filter-status"
          aria-label={t('report.filter_status')}
        >
          <option value="">{t('report.filter_status_all')}</option>
          {ALL_STATUSES.map((s) => (
            <option key={s} value={s}>
              {t(`report.status.${s}`)}
            </option>
          ))}
        </select>
        <select
          value={typeFilter}
          onChange={(e) => setTypeFilter(e.target.value as ReportType | '')}
          className="text-sm border border-border rounded px-2 py-1.5 bg-surface"
          data-testid="report-filter-type"
          aria-label={t('report.filter_type')}
        >
          <option value="">{t('report.filter_type_all')}</option>
          {ALL_TYPES.map((ty) => (
            <option key={ty} value={ty}>
              {t(`report.type.${ty}`)}
            </option>
          ))}
        </select>
        <select
          value={formatFilter}
          onChange={(e) => setFormatFilter(e.target.value as ReportFormat | '')}
          className="text-sm border border-border rounded px-2 py-1.5 bg-surface"
          data-testid="report-filter-format"
          aria-label={t('report.filter_format')}
        >
          <option value="">{t('report.filter_format_all')}</option>
          {ALL_FORMATS.map((f) => (
            <option key={f} value={f}>
              {t(`report.format.${f}`)}
            </option>
          ))}
        </select>
      </div>

      {query.isLoading ? (
        <div className="text-sm text-text-muted">{t('states.loading')}</div>
      ) : !query.data || query.data.items.length === 0 ? (
        <div className="text-sm text-text-muted py-6">{t('report.empty')}</div>
      ) : (
        <div className="border border-border rounded-md overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-divider text-text-secondary text-xs uppercase">
              <tr>
                <th className="text-left px-3 py-2">{t('report.col_title')}</th>
                <th className="text-left px-3 py-2">{t('report.col_type')}</th>
                <th className="text-left px-3 py-2">{t('report.col_format')}</th>
                <th className="text-left px-3 py-2">{t('report.col_status')}</th>
                <th className="text-left px-3 py-2">{t('report.col_contains_salary')}</th>
                <th className="text-left px-3 py-2">{t('report.col_created_by')}</th>
                <th className="text-left px-3 py-2">{t('report.col_created_at')}</th>
                <th className="text-left px-3 py-2">{t('report.col_expires_at')}</th>
                <th className="text-right px-3 py-2">{t('common.actions')}</th>
              </tr>
            </thead>
            <tbody>
              {query.data.items.map((report) => (
                <Row key={report.id} report={report} />
              ))}
            </tbody>
          </table>
        </div>
      )}

      <ReportRequestDialog
        open={dialogOpen}
        projectId={projectId}
        onClose={() => setDialogOpen(false)}
      />
    </section>
  );
}

function Row({ report }: { report: Report }) {
  const { t } = useTranslation();
  const cancelMut = useCancelReport(report.id);
  const canCancel = REPORT_IN_FLIGHT_STATUSES.has(report.status);
  const canDownload = REPORT_DOWNLOADABLE_STATUSES.has(report.status);

  return (
    <tr className="border-t border-border hover:bg-divider" data-testid="report-row">
      <td className="px-3 py-2 text-text-primary">{report.title ?? '—'}</td>
      <td className="px-3 py-2">
        <ReportTypeBadge type={report.reportType} />
      </td>
      <td className="px-3 py-2">
        <ReportFormatBadge format={report.format} />
      </td>
      <td className="px-3 py-2">
        <ReportStatusBadge status={report.status} />
      </td>
      <td className="px-3 py-2">
        {report.containsSalaryData ? (
          <span
            className="inline-flex items-center gap-1 text-xs px-2 py-0.5 rounded border border-salary-sensitive/30 bg-salary-sensitive-bg text-salary-sensitive"
            data-testid="report-contains-salary-chip"
          >
            <ShieldAlert size={12} aria-hidden /> {t('report.contains_salary')}
          </span>
        ) : (
          <span className="text-text-muted text-xs">—</span>
        )}
      </td>
      <td className="px-3 py-2 text-text-muted text-xs">{report.requestedBy ?? '—'}</td>
      <td className="px-3 py-2 text-text-muted">{new Date(report.requestedAt).toLocaleString()}</td>
      <td className="px-3 py-2 text-text-muted">
        {report.expiresAt ? new Date(report.expiresAt).toLocaleString() : '—'}
      </td>
      <td className="px-3 py-2 text-right">
        <div className="flex items-center justify-end gap-2">
          {canDownload ? <ReportSignedDownloadButton reportId={report.id} /> : null}
          {canCancel ? (
            <button
              type="button"
              onClick={() => cancelMut.mutate()}
              disabled={cancelMut.isPending}
              className="text-xs px-2 py-1 rounded border border-border hover:bg-divider"
              data-testid="report-cancel-button"
            >
              {t('common.cancel')}
            </button>
          ) : null}
        </div>
      </td>
    </tr>
  );
}
