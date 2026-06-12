import { useTranslation } from 'react-i18next';
import { Card } from '@/shared/components/ui/Card';
import { formatDateSafe } from '@/shared/lib/dates';
import { shortId } from '@/shared/lib/shortId';
import type { ApprovalDecision } from '../types';

interface Props {
  decisions?: ApprovalDecision[];
}

export function ApprovalDecisionsList({ decisions }: Props) {
  const { t, i18n } = useTranslation();
  // Defensive: `decisions` is synthesized by the adapter and always an array,
  // but guard against an unexpected undefined so .length / .map cannot throw.
  const list = decisions ?? [];
  if (list.length === 0) {
    return (
      <Card title={t('approval.decisions_title')} compact>
        <p className="text-sm text-text-secondary">{t('approval.decisions_empty')}</p>
      </Card>
    );
  }
  return (
    <Card title={t('approval.decisions_title')} compact>
      <ol className="space-y-3">
        {list.map((d) => (
          <li key={d.id} className="border-l-2 border-border-strong pl-3">
            <div className="flex items-center justify-between">
              <span className="text-sm font-medium text-text-primary">
                {t(`approval.decisionType.${d.decision}`)}
              </span>
              <span className="text-xs text-text-muted">
                {formatDateSafe(d.decidedAt, i18n.language, t('common.dash'))}
              </span>
            </div>
            <div className="text-xs text-text-secondary mt-0.5">
              {d.decidedByName ?? shortId(d.decidedByUserId)}
            </div>
            {d.reason ? (
              <p className="mt-1 text-sm text-text-primary whitespace-pre-wrap">{d.reason}</p>
            ) : null}
            {d.notes ? (
              <p className="mt-1 text-sm text-text-secondary italic whitespace-pre-wrap">
                {d.notes}
              </p>
            ) : null}
          </li>
        ))}
      </ol>
    </Card>
  );
}
