/**
 * "Verify integrity" panel (MVP1-E10-1) — lives on the existing Audit Reader
 * screen (`AuditListPage`), the surface the TRUST-1 work first introduced the
 * honest hash-chain badge on.
 *
 * REUSE CONTRACT (kod takrorlanmasin — nothing here is reimplemented):
 *   - Permission gate: shared `PermissionGate` + `PERMISSIONS.AUDIT_READ` —
 *     the SAME gate primitive every other permission-scoped action in this
 *     app uses. The backend independently enforces `AUDIT_READ` (403
 *     otherwise); this is UX-only, never the security boundary.
 *   - Data fetching: `useVerifyAuditIntegrity` / `useAuditIntegrityStatus`
 *     (`../hooks/useAuditIntegrity`) — TanStack Query, mirroring the
 *     mutation-writes-a-shared-cache-slot pattern already used by
 *     `useAdvanceWorkflow`/`useWorkflowProgress`.
 *   - Result classification: `summarizeAuditIntegrity` / `breakTypeLabelKey`
 *     (`../lib/auditIntegritySummary`) — the ONE place that turns a raw
 *     `AuditIntegrityResult` into an honest render decision. The per-event
 *     hash-chain badge in `AuditDetailsDrawer` uses the SAME helper so the
 *     two surfaces can never disagree about what a result means.
 *   - UI primitives: `Card`, `Button`, `InlineBanner`, `Tooltip`,
 *     `StatusBadge` — all shared components, no new visual primitives.
 *   - Error surfacing: mirrors the `ApiError` status→i18n-key resolver
 *     pattern used by `ReportSignedDownloadButton`/`CeoInlineSignOffCell`.
 *
 * Honesty contract (do not weaken — see `auditIntegritySummary.ts`):
 *   - `INTACT` + `bounded=false` → full pass, rendered green.
 *   - `INTACT` + `bounded=true` → rendered as PARTIAL (amber), never a plain
 *     green "all intact" — the tail beyond `max_rows` was not checked.
 *   - `BROKEN` → red, with the first offending row's break type + hashes.
 *   - `EMPTY` → neutral "nothing to verify yet".
 *   - The frontend total is never presented as more certain than the
 *     backend actually reported.
 */
import { useTranslation } from 'react-i18next';
import { Loader2, ShieldCheck } from 'lucide-react';
import { Button } from '@/shared/components/ui/Button';
import { Card } from '@/shared/components/ui/Card';
import { InlineBanner } from '@/shared/components/ui/InlineBanner';
import { Tooltip } from '@/shared/components/ui/Tooltip';
import { StatusBadge } from '@/shared/components/status/StatusBadge';
import { PermissionGate } from '@/shared/components/access/PermissionGate';
import { PERMISSIONS } from '@/shared/types/permissions';
import { ApiError } from '@/shared/api/apiError';
import { formatDateSafe } from '@/shared/lib/dates';
import { shortId } from '@/shared/lib/shortId';
import { useAuditIntegrityStatus, useVerifyAuditIntegrity } from '../hooks/useAuditIntegrity';
import { breakTypeLabelKey, summarizeAuditIntegrity } from '../lib/auditIntegritySummary';
import type { AuditIntegrityBreak } from '../types/auditTypes';

/** Maps a caught error (typically an `ApiError`) to a localized message key. */
function errorMessageKey(err: unknown): string {
  if (err instanceof ApiError && err.status === 403) return 'audit.integrity.error_forbidden';
  return 'audit.integrity.error';
}

function TruncatedHash({ value }: { value: string | null }) {
  if (!value) return <span className="text-text-primary">—</span>;
  return (
    <Tooltip content={value}>
      <span
        className="font-mono text-text-primary cursor-help underline decoration-dotted"
        tabIndex={0}
        title={value}
      >
        {shortId(value)}…
      </span>
    </Tooltip>
  );
}

function BreakDetails({ brk }: { brk: AuditIntegrityBreak }) {
  const { t, i18n } = useTranslation();
  const labelKey = breakTypeLabelKey(brk.breakType);
  return (
    <dl
      data-testid="audit-integrity-first-break"
      className="grid grid-cols-1 sm:grid-cols-2 gap-x-4 gap-y-2 text-xs rounded-md border border-danger-500/30 bg-danger-50 p-3"
    >
      <dt className="text-text-secondary">{t('audit.integrity.break.row')}</dt>
      <dd className="font-mono text-text-primary" title={brk.rowId}>
        {shortId(brk.rowId)}
      </dd>
      <dt className="text-text-secondary">{t('audit.integrity.break.detected_at')}</dt>
      <dd className="text-text-primary">{formatDateSafe(brk.createdAt, i18n.language)}</dd>
      <dt className="text-text-secondary">{t('audit.integrity.break.type')}</dt>
      <dd className="text-text-primary" data-testid="audit-integrity-break-type">
        {labelKey ? t(labelKey) : brk.breakType}
      </dd>
      <dt className="text-text-secondary">{t('audit.integrity.break.expected_hash')}</dt>
      <dd>
        <TruncatedHash value={brk.expectedHash} />
      </dd>
      <dt className="text-text-secondary">{t('audit.integrity.break.actual_hash')}</dt>
      <dd>
        <TruncatedHash value={brk.actualHash} />
      </dd>
    </dl>
  );
}

function VerifiedAtLine({ value }: { value: string | null }) {
  const { t, i18n } = useTranslation();
  return (
    <p className="text-xs text-text-muted" data-testid="audit-integrity-verified-at">
      {t('audit.integrity.verified_at', { time: formatDateSafe(value, i18n.language) })}
    </p>
  );
}

export function AuditIntegrityPanel() {
  const { t } = useTranslation();
  const verifyMutation = useVerifyAuditIntegrity();
  const statusQuery = useAuditIntegrityStatus();

  const summary = summarizeAuditIntegrity(statusQuery.data);

  return (
    <PermissionGate permission={PERMISSIONS.AUDIT_READ}>
      <Card
        title={t('audit.integrity.title')}
        subtitle={t('audit.integrity.subtitle')}
        compact
        action={
          <Button
            variant="secondary"
            size="sm"
            onClick={() => verifyMutation.mutate()}
            disabled={verifyMutation.isPending}
            leadingIcon={
              verifyMutation.isPending ? (
                <Loader2 className="animate-spin" size={14} aria-hidden />
              ) : (
                <ShieldCheck size={14} aria-hidden />
              )
            }
            data-testid="audit-verify-integrity-button"
          >
            {verifyMutation.isPending
              ? t('audit.integrity.action_verifying')
              : t('audit.integrity.action_verify')}
          </Button>
        }
      >
        <div className="space-y-3">
          {verifyMutation.isError ? (
            <InlineBanner variant="warning" data-testid="audit-integrity-error">
              {t(errorMessageKey(verifyMutation.error))}
            </InlineBanner>
          ) : null}

          {!verifyMutation.isError && summary.kind === 'not_verified' ? (
            <p className="text-sm text-text-secondary" data-testid="audit-integrity-not-verified">
              {t('audit.integrity.not_verified')}
            </p>
          ) : null}

          {summary.kind === 'empty' ? (
            <div data-testid="audit-integrity-empty" className="space-y-2">
              <StatusBadge tone="draft" label={t('audit.integrity.status.EMPTY')} />
              <VerifiedAtLine value={summary.result.verifiedAt} />
            </div>
          ) : null}

          {summary.kind === 'intact' ? (
            <div data-testid="audit-integrity-intact" className="space-y-2">
              <StatusBadge tone="approved" label={t('audit.integrity.status.INTACT')} />
              <p className="text-sm text-text-primary">
                {t('audit.integrity.intact_detail', {
                  verified: summary.result.independentlyVerifiedCount,
                  chain: summary.result.chainLength,
                })}
              </p>
              {summary.result.legacyUnverifiableCount > 0 ? (
                <p className="text-sm text-text-secondary">
                  {t('audit.integrity.legacy_detail', {
                    count: summary.result.legacyUnverifiableCount,
                  })}
                </p>
              ) : null}
              <VerifiedAtLine value={summary.result.verifiedAt} />
            </div>
          ) : null}

          {summary.kind === 'partial' ? (
            <div data-testid="audit-integrity-partial" className="space-y-2">
              <StatusBadge tone="incomplete" label={t('audit.integrity.status.PARTIAL')} />
              <p className="text-sm text-text-primary">
                {t('audit.integrity.partial_detail', {
                  checked: summary.result.checkedCount,
                  chain: summary.result.chainLength,
                })}
              </p>
              <p className="text-sm text-text-secondary">
                {t('audit.integrity.partial_hint', { maxRows: summary.result.maxRows })}
              </p>
              <VerifiedAtLine value={summary.result.verifiedAt} />
            </div>
          ) : null}

          {summary.kind === 'broken' ? (
            <div data-testid="audit-integrity-broken" className="space-y-2">
              <StatusBadge tone="needs-attention" label={t('audit.integrity.status.BROKEN')} />
              <p className="text-sm text-text-primary">{t('audit.integrity.broken_detail')}</p>
              {summary.result.firstBreak ? <BreakDetails brk={summary.result.firstBreak} /> : null}
              <VerifiedAtLine value={summary.result.verifiedAt} />
            </div>
          ) : null}
        </div>
      </Card>
    </PermissionGate>
  );
}
