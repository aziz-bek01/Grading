import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Plus, Download, ChevronDown } from 'lucide-react';
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
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { projectId = '' } = useParams<{ projectId: string }>();
  const [status, setStatus] = useState<ImportBatchStatus | ''>('');
  const [templateCode, setTemplateCode] = useState<ImportTemplateCode | ''>('');
  const query = useImports({
    projectId,
    status: status || undefined,
    templateCode: templateCode || undefined,
    page: 0,
    size: 50,
  });

  return (
    <section className="p-6 space-y-4" data-testid="import-list-page">
      <header className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold">{t('import.list.title')}</h1>
          <p className="text-sm text-text-muted">{t('import.list.subtitle')}</p>
        </div>
        <div className="flex items-center gap-2">
          <TemplateDownloadDropdown />
          <button
            type="button"
            onClick={() => navigate(`/app/projects/${projectId}/imports/new`)}
            className="inline-flex items-center gap-2 px-3 py-2 rounded-md bg-primary-500 text-text-inverse hover:bg-primary-700 text-sm"
            data-testid="import-list-new"
          >
            <Plus size={16} /> {t('import.list.new')}
          </button>
        </div>
      </header>

      <div className="flex flex-wrap gap-3">
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
      </div>

      {query.isLoading ? (
        <div className="text-sm text-text-muted">{t('states.loading')}</div>
      ) : !query.data || query.data.items.length === 0 ? (
        <div className="text-sm text-text-muted py-6">{t('import.list.empty')}</div>
      ) : (
        <div className="border border-border rounded-md overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-divider text-text-secondary text-xs uppercase">
              <tr>
                <th className="text-left px-3 py-2">{t('import.col.filename')}</th>
                <th className="text-left px-3 py-2">{t('import.col.template')}</th>
                <th className="text-left px-3 py-2">{t('import.col.status')}</th>
                <th className="text-right px-3 py-2">{t('import.col.rows')}</th>
                <th className="text-right px-3 py-2">{t('import.col.errors')}</th>
                <th className="text-left px-3 py-2">{t('import.col.uploaded')}</th>
              </tr>
            </thead>
            <tbody>
              {query.data.items.map((b) => (
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
                    {new Date(b.uploadedAt).toLocaleString()}
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
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="inline-flex items-center gap-2 px-3 py-2 rounded-md border border-border text-sm hover:bg-divider"
        data-testid="template-download-toggle"
      >
        <Download size={14} aria-hidden /> {t('import.template.dropdown_label')}
        <ChevronDown size={14} aria-hidden />
      </button>
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
