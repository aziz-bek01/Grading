import { useTranslation } from 'react-i18next';
import { Card } from '@/shared/components/ui/Card';
import type { ApprovalDecision } from '../types';

interface Props {
  decisions: ApprovalDecision[];
}

export function ApprovalDecisionsList({ decisions }: Props) {
  const { t, i18n } = useTranslation();
  if (decisions.length === 0) {
    return (
      <Card title={t('approval.decisions_title')} compact>
        <p className="text-sm text-text-secondary">{t('approval.decisions_empty')}</p>
      </Card>
    );
  }
  return (
    <Card title={t('approval.decisions_title')} compact>
      <ol className="space-y-3">
        {decisions.map((d) => (
          <li key={d.id} className="border-l-2 border-border-strong pl-3">
            <div className="flex items-center justify-between">
              <span className="text-sm font-medium text-text-primary">
                {t(`approval.decisionType.${d.decision}`)}
              </span>
              <span className="text-xs text-text-muted">
                {new Date(d.decidedAt).toLocaleString(i18n.language)}
              </span>
            </div>
            <div className="text-xs text-text-secondary mt-0.5">
              {d.decidedByName ?? d.decidedByUserId}
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
