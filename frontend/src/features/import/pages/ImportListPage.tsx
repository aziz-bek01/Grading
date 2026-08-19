import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Plus, Download, ChevronDown } from 'lucide-react';
import { LoadingState } from '@/shared/components/feedback/LoadingState';
import { EmptyState } from '@/shared/components/feedback/EmptyState';
import { ErrorState } from '@/shared/components/feedback/ErrorState';
import { Button } from '@/shared/components/ui/Button';
import { InlineBanner } from '@/shared/components/ui/InlineBanner';
import { formatDateSafe } from '@/shared/lib/dates';
import { useImports } from '../hooks/useImports';
import { ImportStatusBadge } from '../components/ImportStatusBadge';
import { ImportTemplateBadge } from '../components/ImportTemplateBadge';
import { downloadTemplate } from '../api/templateDownload';
import type { ImportBatchStatus, ImportTemplateCode } from '../types';

const ALL_STATUSES: ImportBatchStatus[] = [
  'UPLOADED',
  'SCANNING',
  'SCAN_FAILED',
  'PARSING',
  'VALIDATING',
  'VALIDATION_FAILED',
  'READY_FOR_REVIEW',
  'READY_TO_COMMIT',
  'COMMITTING',
  'COMMITTED',
  'PARTIALLY_COMMITTED',
  'FAILED',
  'CANCELLED',
  'ARCHIVED',
];

const ALL_TEMPLATES: ImportTemplateCode[] = [
  'ORG_STRUCTURE_V1',
  'POSITION_CATALOG_V1',
  'JOB_PROFILE_V1',
  'METHODOLOGY_FACTORS_V1',
  'GRADE_BANDS_V1',
];

export function ImportListPage() {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const { projectId = '' } = useParams<{ projectId: string }>();
  const [status, setStatus] = useState<ImportBatchStatus | ''>('');
  const [templateCode, setTemplateCode] = useState<ImportTemplateCode | ''>('');
  // ARCHIVED batches are retention-only noise once cleared by the user — hide
  // them from the default ("All statuses") view so archiving actually clears
  // the clutter, while an explicit toggle keeps them reachable (and the
  // existing status-filter dropdown still lets a user pick ARCHIVED directly).
  const [showArchived, setShowArchived] = useState(false);
  // One-shot flash message forwarded from ImportDetailsPage after a
  // successful archive (the batch is gone from THIS list's default view, so
  // the confirmation has to travel with the navigation instead of staying on
  // a page the user just left).
  const [flashMessage] = useState<string | null>(
    (location.state as { flashMessageKey?: string } | null)?.flashMessageKey
      ? t((location.state as { flashMessageKey: string }).flashMessageKey)
      : null,
  );
  const query = useImports({
    projectId,
    status: status || undefined,
    templateCode: templateCode || undefined,
    page: 0,
    size: 50,
  });
  const items = (query.data?.items ?? []).filter(
    (b) => showArchived || status || b.status !== 'ARCHIVED',
  );

  // Consume the flash message once so a browser back/forward or refresh into
  // this same history entry doesn't keep re-showing it.
  useEffect(() => {
    if (!(location.state as { flashMessageKey?: string } | null)?.flashMessageKey) return;
    navigate(location.pathname, { replace: true, state: null });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <section className="p-6 space-y-4" data-testid="import-list-page">
      <header className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold">{t('import.list.title')}</h1>
          <p className="text-sm text-text-muted">{t('import.list.subtitle')}</p>
        </div>
        <div className="flex items-center gap-2">
          <TemplateDownloadDropdown />
          <Button
            variant="primary"
            size="compact"
            onClick={() => navigate(`/app/projects/${projectId}/imports/new`)}
            leadingIcon={<Plus size={16} />}
            data-testid="import-list-new"
          >
            {t('import.list.new')}
          </Button>
        </div>
      </header>

      {flashMessage ? (
        <InlineBanner variant="success" data-testid="import-list-flash">
          {flashMessage}
        </InlineBanner>
      ) : null}

      <div className="flex flex-wrap items-end gap-3">
        <label className="text-xs">
          <span className="block text-text-muted mb-1">{t('common.status')}</span>
          <select
            className="border border-border rounded px-2 py-1 text-sm bg-surface"
            value={status}
            onChange={(e) => setStatus(e.target.value as ImportBatchStatus | '')}
            data-testid="import-list-filter-status"
          >
            <option value="">{t('common.all')}</option>
            {ALL_STATUSES.map((s) => (
              <option key={s} value={s}>
                {t(`import.status.${s}`)}
              </option>
            ))}
          </select>
        </label>
        <label className="text-xs">
          <span className="block text-text-muted mb-1">{t('import.field.template')}</span>
          <select
            className="border border-border rounded px-2 py-1 text-sm bg-surface"
            value={templateCode}
            onChange={(e) => setTemplateCode(e.target.value as ImportTemplateCode | '')}
            data-testid="import-list-filter-template"
          >
            <option value="">{t('common.all')}</option>
            {ALL_TEMPLATES.map((c) => (
              <option key={c} value={c}>
                {t(`import.template.${c}`)}
              </option>
            ))}
          </select>
        </label>
        <label className="flex items-center gap-1.5 text-xs text-text-secondary pb-1.5">
          <input
            type="checkbox"
            checked={showArchived}
            onChange={(e) => setShowArchived(e.target.checked)}
            data-testid="import-list-show-archived"
          />
          {t('import.list.show_archived')}
        </label>
      </div>

      {query.isError ? (
        <ErrorState onRetry={() => query.refetch()} />
      ) : query.isLoading ? (
        <LoadingState />
      ) : items.length === 0 ? (
        <EmptyState body={t('import.list.empty')} />
      ) : (
        <div className="border border-border rounded-md overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-divider text-text-secondary text-xs uppercase">
              <tr>
                <th scope="col" className="text-left px-3 py-2">{t('import.col.filename')}</th>
                <th scope="col" className="text-left px-3 py-2">{t('import.col.template')}</th>
                <th scope="col" className="text-left px-3 py-2">{t('import.col.status')}</th>
                <th scope="col" className="text-right px-3 py-2">{t('import.col.rows')}</th>
                <th scope="col" className="text-right px-3 py-2">{t('import.col.errors')}</th>
                <th scope="col" className="text-left px-3 py-2">{t('import.col.uploaded')}</th>
              </tr>
            </thead>
            <tbody>
              {items.map((b) => (
                <tr key={b.id} className="border-t border-border hover:bg-divider">
                  <td className="px-3 py-2">
                    <Link
                      to={`/app/projects/${projectId}/imports/${b.id}`}
                      className="text-primary-700 hover:underline"
                      data-testid={`import-row-${b.id}`}
                    >
                      {b.originalFilename}
                    </Link>
                  </td>
                  <td className="px-3 py-2">
                    <ImportTemplateBadge code={b.templateCode} />
                  </td>
                  <td className="px-3 py-2">
                    <ImportStatusBadge status={b.status} />
                  </td>
                  <td className="px-3 py-2 text-right">{b.totalRowCount ?? '—'}</td>
                  <td className="px-3 py-2 text-right">{b.errorRowCount ?? '—'}</td>
                  <td className="px-3 py-2 text-text-muted">
                    {formatDateSafe(b.uploadedAt, i18n.language)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

function TemplateDownloadDropdown() {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  return (
    <div className="relative" data-testid="template-download-dropdown">
      <Button
        variant="secondary"
        size="compact"
        onClick={() => setOpen((v) => !v)}
        leadingIcon={<Download size={14} aria-hidden />}
        trailingIcon={<ChevronDown size={14} aria-hidden />}
        data-testid="template-download-toggle"
      >
        {t('import.template.dropdown_label')}
      </Button>
      {open ? (
        <div
          className="absolute right-0 z-10 mt-1 w-72 rounded-md border border-border bg-surface shadow-md"
          role="menu"
        >
          {ALL_TEMPLATES.map((code) => (
            <div key={code} className="px-2 py-2 border-b border-border last:border-0">
              <div className="text-xs font-medium text-text-primary mb-1">
                {t(`import.template.${code}`)}
              </div>
              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={() => {
                    setOpen(false);
                    void downloadTemplate(code, 'empty');
                  }}
                  data-testid={`template-dropdown-${code}-empty`}
                  className="text-xs px-2 py-1 rounded border border-border hover:bg-divider"
                >
                  {t('import.template.download_empty')}
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setOpen(false);
                    void downloadTemplate(code, 'sample');
                  }}
                  data-testid={`template-dropdown-${code}-sample`}
                  className="text-xs px-2 py-1 rounded text-text-secondary hover:bg-divider"
                >
                  {t('import.template.download_sample')}
                </button>
              </div>
            </div>
          ))}
        </div>
      ) : null}
    </div>
  );
}
