import { Fragment, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Lock, TriangleAlert } from 'lucide-react';
import { LoadingState } from '@/shared/components/feedback/LoadingState';
import { ErrorState } from '@/shared/components/feedback/ErrorState';
import { NoAccessState } from '@/shared/components/feedback/NoAccessState';
import { PERMISSIONS } from '@/shared/types/permissions';
import { usePermission } from '@/features/auth/usePermission';
import { ApiError } from '@/shared/api/apiError';
import { pickLocalized } from '@/shared/lib/localized';
import { shortId } from '@/shared/lib/shortId';
import { cn } from '@/shared/lib/cn';
import { usePanelResult } from '../../hooks/usePanels';
import {
  isPanelAveraged,
  type PanelResult,
  type PanelStatus,
} from '../../panelTypes';
import { PanelRoleChip } from './PanelRoleChip';

interface Props {
  panelId: string;
  /** Panel status — drives the locked placeholder before AVERAGED. */
  status: PanelStatus;
  /**
   * Optional already-fetched result (e.g. the CEO detail summary). When set the
   * component renders it directly and skips the gated query — used so the
   * approval detail page can reuse the SAME table without a second request.
   */
  result?: PanelResult;
  /** Show the assigned-grade badge slot (CEO summary). Rendered by the parent. */
  gradeBadge?: React.ReactNode;
}

function fmt(n: number | null | undefined, digits = 2): string {
  if (n === null || n === undefined || Number.isNaN(n)) return '—';
  return Number(n).toFixed(digits);
}

/**
 * Averaged panel result (FE-5 / UX 6.4 + 6.5 shared summary).
 *
 * VISIBILITY GATE: renders the table ONLY when the panel is AVERAGED+ AND the
 * viewer holds CAMPAIGN_RESULTS_VIEW. Before that it shows a locked placeholder
 * ("Average shown once all evaluators finish") — NEVER a partial average. The
 * gated `usePanelResult` query only fires once the gate passes, so the BE 404
 * (collecting) / 403 (no permission) paths are not even hit in the happy flow.
 *
 * The displayed average is the BE-STORED official value (raw scale 4, displayed
 * scale 2) — labelled "official", consistent with the "preview only / backend
 * is source of truth" honesty convention. Per factor it shows the bold Average,
 * a per-evaluator mini-row (toggle), and a disagreement indicator (icon +
 * numeric range, never color-only — a11y).
 */
export function PanelAveragedResult({ panelId, status, result, gradeBadge }: Props) {
  const { t, i18n } = useTranslation();
  const { can } = usePermission();
  const canViewResults = can(PERMISSIONS.CAMPAIGN_RESULTS_VIEW);
  const [showEvaluators, setShowEvaluators] = useState(false);

  const averaged = isPanelAveraged(status);
  const gatePasses = averaged && canViewResults;
  // Only fetch when the gate passes AND no result was injected by the parent.
  const query = usePanelResult(
    result ? undefined : panelId,
    gatePasses && !result,
  );

  // Locked placeholder before AVERAGED — never a partial average (UX 6.4).
  if (!averaged) {
    return (
      <div
        data-testid="panel-result-locked"
        className="rounded-lg border border-border bg-divider/40 p-6 flex items-start gap-3"
      >
        <Lock size={18} className="text-text-muted shrink-0 mt-0.5" aria-hidden />
        <div>
          <h3 className="text-sm font-semibold text-text-primary">
            {t('panel.result.locked_title')}
          </h3>
          <p className="text-sm text-text-secondary mt-1">
            {t('panel.result.locked_body')}
          </p>
        </div>
      </div>
    );
  }

  // Averaged but viewer lacks CAMPAIGN_RESULTS_VIEW — deny-by-default, the blind
  // does not lift just because the panel finished (AC-6).
  if (!canViewResults) {
    return (
      <NoAccessState
        title={t('panel.result.no_access_title')}
        body={t('panel.result.no_access_body')}
      />
    );
  }

  const data = result ?? query.data;
  if (!result) {
    if (query.isLoading) return <LoadingState />;
    if (query.error) {
      const err = query.error;
      if (err instanceof ApiError && (err.isForbidden() || err.isNotFound())) {
        return (
          <NoAccessState
            title={t('panel.result.no_access_title')}
            body={t('panel.result.no_access_body')}
          />
        );
      }
      const correlationId = err instanceof ApiError ? err.correlationId : undefined;
      return <ErrorState correlationId={correlationId} onRetry={() => query.refetch()} />;
    }
  }
  if (!data) return null;

  return (
    <div
      className="rounded-lg border border-border bg-surface p-4 space-y-3"
      data-testid="panel-averaged-result"
    >
      <div className="flex items-center justify-between gap-3 flex-wrap">
        <div className="flex items-center gap-3 flex-wrap">
          <h3 className="text-sm font-semibold text-text-primary">
            {t('panel.result.title')}
          </h3>
          <span className="text-xs text-text-muted">
            {t('panel.result.evaluator_count', { count: data.evaluator_count })}
          </span>
          {gradeBadge}
        </div>
        <button
          type="button"
          onClick={() => setShowEvaluators((v) => !v)}
          data-testid="panel-result-toggle-evaluators"
          aria-pressed={showEvaluators}
          className="text-xs text-primary-600 hover:underline"
        >
          {t('panel.result.toggle_evaluators')}
        </button>
      </div>

      <table className="w-full text-sm">
        <thead>
          <tr className="text-left text-xs uppercase text-text-muted border-b border-border">
            <th scope="col" className="py-1 px-2">{t('panel.result.col_factor')}</th>
            <th scope="col" className="py-1 px-2 text-right">{t('panel.result.col_average')}</th>
            <th scope="col" className="py-1 px-2 text-right">{t('panel.result.col_disagreement')}</th>
          </tr>
        </thead>
        <tbody>
          {data.factor_averages.map((fa) => {
            const disagree = fa.disagreement_range;
            const hasDisagreement = disagree != null && disagree > 0;
            return (
              <Fragment key={fa.factor_id}>
                <tr
                  className="border-b border-divider"
                  data-testid={`panel-result-row-${fa.factor_id}`}
                >
                  <td className="py-1.5 px-2">
                    {pickLocalized(fa.factor_name_i18n, i18n.language)}
                  </td>
                  {/* Bold = the official average (UX 6.4). */}
                  <td
                    className="py-1.5 px-2 text-right font-semibold tabular-nums"
                    data-testid={`panel-result-avg-${fa.factor_id}`}
                  >
                    {fmt(fa.avg_raw_factor_score, 4)}
                  </td>
                  <td className="py-1.5 px-2 text-right tabular-nums">
                    {disagree == null ? (
                      <span className="text-text-muted">—</span>
                    ) : (
                      <span
                        className={cn(
                          'inline-flex items-center gap-1 justify-end',
                          hasDisagreement ? 'text-warning-700' : 'text-text-muted',
                        )}
                        data-testid={`panel-result-disagreement-${fa.factor_id}`}
                        title={t('panel.result.disagreement_tooltip', {
                          range: fmt(disagree, 4),
                        })}
                      >
                        {hasDisagreement ? (
                          <TriangleAlert size={12} aria-hidden />
                        ) : null}
                        {fmt(disagree, 4)}
                      </span>
                    )}
                  </td>
                </tr>
                {showEvaluators ? (
                  <tr
                    data-testid={`panel-result-evaluators-${fa.factor_id}`}
                    className="bg-divider/20"
                  >
                    <td colSpan={3} className="py-1.5 px-2">
                      <ul className="flex flex-wrap gap-x-4 gap-y-1 text-xs">
                        {fa.per_evaluator.map((pe) => (
                          <li
                            key={pe.evaluator_user_id}
                            className="inline-flex items-center gap-1.5"
                          >
                            <PanelRoleChip role={pe.evaluator_role} />
                            <span className="text-text-secondary">
                              {pe.evaluator_name ?? shortId(pe.evaluator_user_id)}
                            </span>
                            <span className="font-medium tabular-nums text-text-primary">
                              {fmt(pe.raw_factor_score, 4)}
                            </span>
                          </li>
                        ))}
                      </ul>
                    </td>
                  </tr>
                ) : null}
              </Fragment>
            );
          })}
          <tr className="border-t border-border-strong">
            <td className="py-2 px-2 font-semibold">
              {t('panel.result.row_total')}
            </td>
            <td
              className="py-2 px-2 text-right font-semibold tabular-nums"
              data-testid="panel-result-total"
            >
              {fmt(data.displayed_total_score, 2)}
            </td>
            <td className="py-2 px-2" />
          </tr>
        </tbody>
      </table>

      <p className="text-xs text-text-muted" data-testid="panel-result-official-note">
        {t('panel.result.official_note')}
      </p>
    </div>
  );
}
