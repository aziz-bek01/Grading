/**
 * Drawer that shows a single audit event in full detail (D-1 FE).
 *
 * Surfaces:
 *   - action / actor / timestamp / correlation id
 *   - hash chain badge (current + previous)
 *   - reason / metadata / before / after as readable JSON
 *
 * Layout reuses the shared <DrawerForm /> shell but customises actions
 * so the only footer button is "Close" (no submit).
 *
 * Hash-chain badge honesty (MVP1-E10-1 — updates the TRUST-1 badge):
 *   `GET /api/v1/audit/integrity` now exists, so the badge is no longer a
 *   blanket "Not independently verified". It reads the SAME shared cache
 *   slot the "Verify integrity" panel (`AuditIntegrityPanel`) writes to via
 *   `useAuditIntegrityStatus` (read-only, never auto-fetches) and classifies
 *   it through the SAME `summarizeAuditIntegrity` helper, so this badge can
 *   only ever claim evidence the backend actually returned THIS session —
 *   never a permanent/persisted claim, and never more than was verified
 *   (a bounded/partial run still reads as partial here, not "all intact").
 *   Before any verification has been run, it stays the original honest
 *   "Not independently verified" state.
 */
import { useTranslation } from 'react-i18next';
import { Button } from '@/shared/components/ui/Button';
import { DrawerForm } from '@/shared/components/data-table/DrawerForm';
import { StatusBadge, type StatusTone } from '@/shared/components/status/StatusBadge';
import { shortId } from '@/shared/lib/shortId';
import { formatDateSafe } from '@/shared/lib/dates';
import { ActionIcon } from './AuditEventRow';
import { actionIconKind } from './auditActionIcon';
import { useAuditIntegrityStatus } from '../hooks/useAuditIntegrity';
import { summarizeAuditIntegrity } from '../lib/auditIntegritySummary';
import type { AuditEvent } from '../types/auditTypes';

interface AuditDetailsDrawerProps {
  event: AuditEvent | null;
  open: boolean;
  onClose: () => void;
}

function formatJson(value: unknown): string {
  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return String(value);
  }
}

interface HashChainBadgeContent {
  tone: StatusTone;
  label: string;
  hint: string;
}

/**
 * Derives the badge tone/label/hint from the shared `summarizeAuditIntegrity`
 * classification — see the module doc for the honesty contract. `t`/`locale`
 * are passed in rather than calling `useTranslation` again so this stays a
 * plain function (no extra hook instance) callable from the render body.
 */
function hashChainBadgeContent(
  summary: ReturnType<typeof summarizeAuditIntegrity>,
  t: (key: string, opts?: Record<string, unknown>) => string,
  locale: string,
): HashChainBadgeContent {
  switch (summary.kind) {
    case 'intact':
      return {
        tone: 'approved',
        label: t('audit.details.hash_chain_verified', {
          verified: summary.result.independentlyVerifiedCount,
          chain: summary.result.chainLength,
          time: formatDateSafe(summary.result.verifiedAt, locale),
        }),
        hint: t('audit.details.hash_chain_verified_hint', {
          legacy: summary.result.legacyUnverifiableCount,
        }),
      };
    case 'partial':
      return {
        tone: 'incomplete',
        label: t('audit.details.hash_chain_partial', {
          checked: summary.result.checkedCount,
          chain: summary.result.chainLength,
          time: formatDateSafe(summary.result.verifiedAt, locale),
        }),
        hint: t('audit.details.hash_chain_partial_hint'),
      };
    case 'broken':
      return {
        tone: 'needs-attention',
        label: t('audit.hash_chain_broken'),
        hint: t('audit.details.hash_chain_broken_hint', {
          time: formatDateSafe(summary.result.verifiedAt, locale),
        }),
      };
    case 'empty':
      return {
        tone: 'draft',
        label: t('audit.details.hash_chain_empty_evidence', {
          time: formatDateSafe(summary.result.verifiedAt, locale),
        }),
        hint: t('audit.hash_chain_unverified_hint'),
      };
    case 'not_verified':
    default:
      return {
        tone: 'draft',
        label: t('audit.hash_chain_unverified'),
        hint: t('audit.hash_chain_unverified_hint'),
      };
  }
}

export function AuditDetailsDrawer({ event, open, onClose }: AuditDetailsDrawerProps) {
  const { t, i18n } = useTranslation();
  const integrityStatus = useAuditIntegrityStatus();
  const hashChainBadge = hashChainBadgeContent(
    summarizeAuditIntegrity(integrityStatus.data),
    t,
    i18n.language,
  );

  if (!event) {
    return (
      <DrawerForm
        open={open}
        title={t('audit.details.title')}
        onClose={onClose}
        customActions={
          <Button variant="secondary" onClick={onClose} type="button">
            {t('common.close')}
          </Button>
        }
      >
        <p className="text-sm text-text-secondary">{t('audit.details.empty')}</p>
      </DrawerForm>
    );
  }

  const kind = actionIconKind(event.action);

  return (
    <DrawerForm
      open={open}
      title={t(`audit.action.${event.action}`, { defaultValue: event.action })}
      subtitle={formatDateSafe(event.createdAt, i18n.language)}
      onClose={onClose}
      customActions={
        <Button variant="secondary" onClick={onClose} type="button">
          {t('common.close')}
        </Button>
      }
    >
      <div className="space-y-4">
        <div className="flex items-center gap-2 text-sm text-text-primary">
          <ActionIcon kind={kind} size={16} />
          <span className="font-mono text-xs text-text-muted">{event.action}</span>
        </div>

        <dl className="grid grid-cols-1 sm:grid-cols-2 gap-x-4 gap-y-2 text-sm">
          <dt className="text-text-secondary">{t('audit.details.actor')}</dt>
          {/* T4: prefer BE display name; else a SHORT id (full UUID kept in the
              title= tooltip as a copy affordance); else a localized placeholder. */}
          <dd className="text-text-primary">
            {event.actorName ? (
              event.actorName
            ) : event.actorUserId ? (
              <span className="font-mono text-xs" title={event.actorUserId}>
                {shortId(event.actorUserId)}
              </span>
            ) : (
              t('common.unknown_user')
            )}
          </dd>
          <dt className="text-text-secondary">{t('audit.details.entity')}</dt>
          {/* T4: localized entity-type label + SHORT id; full UUID only in the
              title= tooltip (copy affordance). */}
          <dd className="text-text-primary">
            {event.entityType ? (
              <span title={event.entityId ?? undefined}>
                {t(`audit.entity_type.${event.entityType}`, {
                  defaultValue: event.entityType,
                })}
                {event.entityId ? (
                  <span className="font-mono text-xs text-text-muted">
                    {' '}
                    · {shortId(event.entityId)}
                  </span>
                ) : (
                  ''
                )}
              </span>
            ) : (
              '—'
            )}
          </dd>
          <dt className="text-text-secondary">{t('audit.details.tenant')}</dt>
          <dd className="text-text-primary">{event.tenantId ?? '—'}</dd>
          <dt className="text-text-secondary">{t('audit.details.correlation_id')}</dt>
          <dd className="text-text-primary font-mono text-xs">{event.correlationId ?? '—'}</dd>
          <dt className="text-text-secondary">{t('audit.details.ip')}</dt>
          <dd className="text-text-primary font-mono text-xs">{event.ipAddress ?? '—'}</dd>
          <dt className="text-text-secondary">{t('audit.details.user_agent')}</dt>
          <dd className="text-text-primary text-xs break-all">{event.userAgent ?? '—'}</dd>
        </dl>

        {/* Hash chain — the badge reflects ONLY earned evidence: it stays the
            neutral/muted "Not independently verified" disclosure until the
            user runs "Verify integrity" (AuditIntegrityPanel, MVP1-E10-1) at
            least once this session, then upgrades to the exact evidence the
            backend returned (see `hashChainBadgeContent` above). It never
            claims more than the last verification actually covered. */}
        <div className="rounded-md border border-border bg-divider/30 p-3 text-xs">
          <div className="flex items-center justify-between mb-1">
            <span className="text-text-secondary uppercase tracking-wide">
              {t('audit.details.hash_chain')}
            </span>
            <span title={hashChainBadge.hint} data-testid="audit-hash-chain-badge">
              <StatusBadge tone={hashChainBadge.tone} label={hashChainBadge.label} />
            </span>
          </div>
          <div className="font-mono text-xs space-y-1">
            <div>
              <span className="text-text-muted">prev: </span>
              <span className="text-text-primary">{event.hashPrevious ?? '—'}</span>
            </div>
            <div>
              <span className="text-text-muted">cur:  </span>
              <span className="text-text-primary">{event.hashCurrent ?? '—'}</span>
            </div>
          </div>
        </div>

        {event.reason ? (
          <div>
            <h4 className="text-sm font-medium text-text-primary mb-1">
              {t('audit.details.reason')}
            </h4>
            <p className="text-sm text-text-secondary whitespace-pre-wrap">{event.reason}</p>
          </div>
        ) : null}

        {event.metadata ? (
          <details open className="text-sm">
            <summary className="cursor-pointer text-text-primary font-medium mb-1">
              {t('audit.details.metadata')}
            </summary>
            <pre className="mt-2 bg-divider/30 border border-border rounded-md p-3 text-xs overflow-x-auto font-mono">
              {formatJson(event.metadata)}
            </pre>
          </details>
        ) : null}

        {event.before ? (
          <details className="text-sm">
            <summary className="cursor-pointer text-text-primary font-medium mb-1">
              {t('audit.details.before')}
            </summary>
            <pre className="mt-2 bg-divider/30 border border-border rounded-md p-3 text-xs overflow-x-auto font-mono">
              {formatJson(event.before)}
            </pre>
          </details>
        ) : null}

        {event.after ? (
          <details className="text-sm">
            <summary className="cursor-pointer text-text-primary font-medium mb-1">
              {t('audit.details.after')}
            </summary>
            <pre className="mt-2 bg-divider/30 border border-border rounded-md p-3 text-xs overflow-x-auto font-mono">
              {formatJson(event.after)}
            </pre>
          </details>
        ) : null}
      </div>
    </DrawerForm>
  );
}
