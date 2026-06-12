import { useTranslation } from 'react-i18next';
import { cn } from '@/shared/lib/cn';
import type { EvaluatorRole } from '../../panelTypes';

interface Props {
  role: EvaluatorRole;
  /** Prefix the label with "You:" (the evaluator's own seat chip). */
  self?: boolean;
  className?: string;
}

/**
 * Compact evaluator-role chip. Mandatory-trio roles use the navy/primary tone;
 * ADDITIONAL uses a neutral tone so the three required seats read as official.
 *
 * Localization note (AC-17 / FE-8): the Uzbek-Cyrillic labels ("Ташқи эксперт")
 * are longer than English ("External expert"); the role COLUMN that hosts these
 * gives a min-width — the chip itself wraps gracefully.
 */
export function PanelRoleChip({ role, self, className }: Props) {
  const { t } = useTranslation();
  const label = t(`panel.role.${role}`);
  const text = self ? t('panel.role.self_prefix', { role: label }) : label;
  return (
    <span
      data-testid={`panel-role-chip-${role}`}
      data-role={role}
      className={cn(
        'inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium border whitespace-nowrap',
        role === 'ADDITIONAL'
          ? 'bg-divider text-text-secondary border-border-strong'
          : 'bg-primary-50 text-primary-700 border-primary-500/30',
        className,
      )}
    >
      {text}
    </span>
  );
}
