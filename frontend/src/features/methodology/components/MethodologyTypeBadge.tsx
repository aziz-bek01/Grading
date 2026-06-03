import { useTranslation } from 'react-i18next';
import { Layers, GanttChartSquare, Sparkles } from 'lucide-react';
import { cn } from '@/shared/lib/cn';
import type { MethodologyType } from '../types';

const ICONS: Record<MethodologyType, React.ReactNode> = {
  CLASSIC_8_FACTOR: <Layers size={12} />,
  EXTENDED_11_CRITERIA: <GanttChartSquare size={12} />,
  CUSTOM: <Sparkles size={12} />,
};

const KEYS: Record<MethodologyType, string> = {
  CLASSIC_8_FACTOR: 'methodology.type.classic_8_factor',
  EXTENDED_11_CRITERIA: 'methodology.type.extended_11_criteria',
  CUSTOM: 'methodology.type.custom',
};

export function MethodologyTypeBadge({
  type,
  className,
}: {
  type: MethodologyType;
  className?: string;
}) {
  const { t } = useTranslation();
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 text-xs font-medium px-2 py-0.5 rounded-full border',
        'bg-primary-50 text-primary-700 border-primary-200',
        className,
      )}
      data-testid={`methodology-type-${type}`}
    >
      {ICONS[type]}
      <span>{t(KEYS[type])}</span>
    </span>
  );
}
