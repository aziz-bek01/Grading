import { useTranslation } from 'react-i18next';
import { StatusBadge } from './StatusBadge';

interface LockedBadgeProps {
  /** "soon" — sidebar locked stubs; "locked" — locked entity. */
  variant?: 'soon' | 'locked';
  label?: string;
}

export function LockedBadge({ variant = 'locked', label }: LockedBadgeProps) {
  const { t } = useTranslation();
  const fallback = variant === 'soon' ? t('nav.soon') : t('status.locked');
  return <StatusBadge tone="locked" label={label ?? fallback} />;
}
