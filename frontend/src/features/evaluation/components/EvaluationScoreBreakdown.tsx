import { useTranslation } from 'react-i18next';
import { pickLocalized } from '@/shared/lib/localized';
import type { MethodologyVersion } from '@/features/methodology/types';
import type { EvaluationScore } from '../types';

interface Props {
  version: MethodologyVersion;
  scores: EvaluationScore[];
}

/**
 * Per-factor breakdown card. Shows the selected level + raw factor
 * contribution. Displayed values come straight from the backend
 * EvaluationScore — these are the official numbers, NOT the preview.
 */
export function EvaluationScoreBreakdown({ version, scores }: Props) {
  const { t, i18n } = useTranslation();
  const scoreMap = new Map(scores.map((s) => [s.factor_id, s]));
  const factors = [...version.factors].sort(
    (a, b) => a.sort_order - b.sort_order,
  );
  const total = scores.reduce((acc, s) => acc + Number(s.raw_factor_score), 0);

  return (
    <div
      className="rounded-lg border border-border bg-surface p-4"
      data-testid="score-breakdown"
    >
      <h3 className="text-sm font-semibold text-text-primary mb-3">
        {t('evaluation.breakdown.title')}
      </h3>
      <table className="w-full text-sm">
        <thead>
          <tr className="text-left text-xs uppercase text-text-muted border-b border-border">
            <th scope="col" className="py-1 px-2">
              {t('evaluation.breakdown.col_factor')}
            </th>
            <th scope="col" className="py-1 px-2 text-right">
              {t('evaluation.breakdown.col_weight')}
            </th>
            <th scope="col" className="py-1 px-2">
              {t('evaluation.breakdown.col_level')}
            </th>
            <th scope="col" className="py-1 px-2 text-right">
              {t('evaluation.breakdown.col_raw')}
            </th>
          </tr>
        </thead>
        <tbody>
          {factors.map((f) => {
            const score = scoreMap.get(f.id);
            const level = score
              ? f.levels.find((lvl) => lvl.id === score.factor_level_id)
              : null;
            return (
              <tr
                key={f.id}
                className="border-b border-divider"
                data-testid={`breakdown-row-${f.code}`}
              >
                <td className="py-1 px-2">
                  <span className="font-mono text-xs text-text-muted">
                    {f.code}
                  </span>{' '}
                  {pickLocalized(f.name_i18n, i18n.language)}
                </td>
                <td className="py-1 px-2 text-right tabular-nums">
                  {f.weight}
                </td>
                <td className="py-1 px-2">
                  {level
                    ? `${level.code} — ${pickLocalized(level.label_i18n, i18n.language)}`
                    : '—'}
                </td>
                <td className="py-1 px-2 text-right tabular-nums">
                  {score ? Number(score.raw_factor_score).toFixed(2) : '—'}
                </td>
              </tr>
            );
          })}
          <tr className="border-t border-border-strong">
            <td className="py-2 px-2 font-semibold" colSpan={3}>
              {t('evaluation.breakdown.row_total')}
            </td>
            <td
              className="py-2 px-2 text-right font-semibold tabular-nums"
              data-testid="breakdown-total"
            >
              {total.toFixed(2)}
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  );
}
