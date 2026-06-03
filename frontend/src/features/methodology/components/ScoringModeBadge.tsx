import { useTranslation } from 'react-i18next';
import { Calculator, Scale, Sliders } from 'lucide-react';
import { cn } from '@/shared/lib/cn';
import type { ScoringMode } from '../types';

const ICONS: Record<ScoringMode, React.ReactNode> = {
  DIRECT_POINTS: <Calculator size={12} />,
  WEIGHTED_POINTS: <Scale size={12} />,
  WEIGHTED_SCALE: <Sliders size={12} />,
};

const KEYS: Record<ScoringMode, string> = {
  DIRECT_POINTS: 'methodology.scoring_mode_badge.direct_points',
  WEIGHTED_POINTS: 'methodology.scoring_mode_badge.weighted_points',
  WEIGHTED_SCALE: 'methodology.scoring_mode_badge.weighted_scale',
};

export function ScoringModeBadge({
  mode,
  className,
}: {
  mode: ScoringMode;
  className?: string;
}) {
  const { t } = useTranslation();
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 text-xs font-medium px-2 py-0.5 rounded-full border',
        'bg-accent-50 text-accent-700 border-accent-100',
        className,
      )}
      data-testid={`scoring-mode-${mode}`}
    >
      {ICONS[mode]}
      <span>{t(KEYS[mode])}</span>
    </span>
  );
}
