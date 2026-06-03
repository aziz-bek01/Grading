import { useTranslation } from 'react-i18next';
import { cn } from '@/shared/lib/cn';
import type { GradeStructureType } from '../types';

const TYPE_KEY: Record<GradeStructureType, string> = {
  GRADE_14: 'gradeStructure.type.grade_14',
  GRADE_16: 'gradeStructure.type.grade_16',
  CUSTOM: 'gradeStructure.type.custom',
};

export function GradeStructureTypeBadge({
  type,
  className,
}: {
  type: GradeStructureType;
  className?: string;
}) {
  const { t } = useTranslation();
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 text-xs font-medium px-2 py-0.5 rounded-full border',
        'bg-info-50 text-info-600 border-info-500/30',
        className,
      )}
      data-testid={`grade-structure-type-${type}`}
    >
      {t(TYPE_KEY[type])}
    </span>
  );
}
