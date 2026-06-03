/**
 * CSV export button for the audit table (D-1 FE).
 *
 * Builds the CSV CLIENT-SIDE from already-loaded events — there is no
 * backend `/audit/export` yet. When the backend grows that endpoint, swap
 * the implementation but keep the same props.
 *
 * Security:
 *   - The button is gated by EXPORT_GENERAL upstream (the page wraps it
 *     in <PermissionGate />). We do not enforce permission here — UI gate
 *     is for visibility only; the backend remains source of truth.
 *   - The CSV never includes salary data (audit feed does not carry any).
 *     Salary masking remains the responsibility of the source store.
 */
import { useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { Download } from 'lucide-react';
import { Button } from '@/shared/components/ui/Button';
import type { AuditEvent } from '../types/auditTypes';

interface AuditCsvExportButtonProps {
  events: AuditEvent[];
  /** When true (default), include reason + metadata as JSON-encoded cells. */
  includeMetadata?: boolean;
  /** Optional filename override. */
  filename?: string;
}

const CSV_HEADERS = [
  'id',
  'created_at',
  'action',
  'tenant_id',
  'actor_user_id',
  'actor_name',
  'entity_type',
  'entity_id',
  'reason',
  'ip_address',
  'correlation_id',
  'hash_current',
  'hash_previous',
  'metadata',
];

function escapeCsv(value: unknown): string {
  if (value === null || value === undefined) return '';
  const s = typeof value === 'string' ? value : JSON.stringify(value);
  if (s.includes(',') || s.includes('"') || s.includes('\n')) {
    return `"${s.replace(/"/g, '""')}"`;
  }
  return s;
}

function eventToRow(e: AuditEvent, includeMetadata: boolean): string {
  return [
    e.id,
    e.createdAt,
    e.action,
    e.tenantId ?? '',
    e.actorUserId ?? '',
    e.actorName ?? '',
    e.entityType ?? '',
    e.entityId ?? '',
    e.reason ?? '',
    e.ipAddress ?? '',
    e.correlationId ?? '',
    e.hashCurrent ?? '',
    e.hashPrevious ?? '',
    includeMetadata ? (e.metadata ?? e.after ?? e.before ?? null) : '',
  ]
    .map(escapeCsv)
    .join(',');
}

export function AuditCsvExportButton({
  events,
  includeMetadata = true,
  filename,
}: AuditCsvExportButtonProps) {
  const { t } = useTranslation();

  const onClick = useCallback(() => {
    const body = events.map((e) => eventToRow(e, includeMetadata)).join('\n');
    const csv = `${CSV_HEADERS.join(',')}\n${body}`;
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    const stamp = new Date().toISOString().slice(0, 19).replace(/[:T]/g, '-');
    a.download = filename ?? `audit-${stamp}.csv`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
  }, [events, includeMetadata, filename]);

  return (
    <Button
      variant="secondary"
      size="sm"
      leadingIcon={<Download size={14} />}
      onClick={onClick}
      disabled={events.length === 0}
      data-testid="audit-export-csv"
    >
      {t('audit.export_csv')}
    </Button>
  );
}
