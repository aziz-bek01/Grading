import { useTranslation } from 'react-i18next';
import { pickLocalized } from '@/shared/lib/localized';
import { cn } from '@/shared/lib/cn';
import type { Factor } from '@/features/methodology/types';
import type { CalibrationEvent } from '../types';

interface Props {
  events: CalibrationEvent[];
  factors: Factor[];
}

function formatDelta(delta: number): { text: string; tone: 'pos' | 'neg' | 'zero' } {
  if (delta > 0) return { text: `+${delta.toFixed(2)}`, tone: 'pos' };
  if (delta < 0) return { text: `${delta.toFixed(2)}`, tone: 'neg' };
  return { text: '0.00', tone: 'zero' };
}

/**
 * Committee-view calibration history.
 *
 * Columns: factor | original | adjusted | Δ (color-coded) | reason |
 * decided by | decided at. Read-only — every row was already audited
 * server-side as SCORE_CALIBRATED.
 */
export function CalibrationTable({ events, factors }: Props) {
  const { t, i18n } = useTranslation();
  const factorMap = new Map(factors.map((f) => [f.id, f]));

  if (events.length === 0) {
    return (
      <p
        className="text-sm text-text-muted"
        data-testid="calibration-table-empty"
      >
        {t('evaluation.calibration.empty')}
      </p>
    );
  }

  return (
    <table
      className="w-full text-sm border-collapse"
      data-testid="calibration-table"
    >
      <thead>
        <tr className="text-left text-xs uppercase text-text-muted border-b border-border">
          <th className="py-2 px-2">{t('evaluation.calibration.col_factor')}</th>
          <th className="py-2 px-2 text-right">
            {t('evaluation.calibration.col_original')}
          </th>
          <th className="py-2 px-2 text-right">
            {t('evaluation.calibration.col_adjusted')}
          </th>
          <th className="py-2 px-2 text-right">
            {t('evaluation.calibration.col_delta')}
          </th>
          <th className="py-2 px-2">{t('evaluation.calibration.col_reason')}</th>
          <th className="py-2 px-2">
            {t('evaluation.calibration.col_decided_by')}
          </th>
          <th className="py-2 px-2">{t('evaluation.calibration.col_when')}</th>
        </tr>
      </thead>
      <tbody>
        {events.map((ev) => {
          const factor = factorMap.get(ev.factor_id);
          const delta = formatDelta(ev.delta);
          return (
            <tr
              key={ev.id}
              data-testid={`calibration-row-${ev.id}`}
              className="border-b border-divider align-top"
            >
              <td className="py-2 px-2 font-medium">
                {factor ? (
                  <>
                    <span className="font-mono text-xs text-text-muted">
                      {factor.code}
                    </span>{' '}
                    {pickLocalized(factor.name_i18n, i18n.language)}
                  </>
                ) : (
                  ev.factor_id
                )}
              </td>
              <td className="py-2 px-2 text-right tabular-nums">
                {ev.original_score.toFixed(2)}
              </td>
              <td className="py-2 px-2 text-right tabular-nums">
                {ev.adjusted_score.toFixed(2)}
              </td>
              <td
                className={cn(
                  'py-2 px-2 text-right tabular-nums font-medium',
                  delta.tone === 'pos' && 'text-success-700',
                  delta.tone === 'neg' && 'text-danger-600',
                  delta.tone === 'zero' && 'text-text-muted',
                )}
                data-testid={`calibration-delta-${ev.id}`}
                data-tone={delta.tone}
              >
                {delta.text}
              </td>
              <td className="py-2 px-2 text-text-secondary max-w-[20rem]">
                {ev.reason}
              </td>
              <td className="py-2 px-2 text-text-muted text-xs">
                {ev.decided_by ?? t('common.unknown_actor')}
              </td>
              <td className="py-2 px-2 text-text-muted text-xs">
                {ev.decided_at.slice(0, 16).replace('T', ' ')}
              </td>
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}
