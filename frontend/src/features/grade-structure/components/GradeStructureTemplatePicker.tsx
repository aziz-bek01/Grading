import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Layers, Layers3, Sparkles } from 'lucide-react';
import { Button } from '@/shared/components/ui/Button';
import { cn } from '@/shared/lib/cn';
import type { GradeStructureTemplateCode } from '../types';

interface GradeStructureTemplatePickerProps {
  open: boolean;
  onCancel: () => void;
  onSelect: (template: GradeStructureTemplateCode) => void;
}

const OPTIONS: {
  code: GradeStructureTemplateCode;
  icon: React.ReactNode;
  titleKey: string;
  bodyKey: string;
}[] = [
  {
    code: 'GRADE_14',
    icon: <Layers size={20} />,
    titleKey: 'gradeStructure.type.grade_14',
    bodyKey: 'gradeStructure.template_picker.grade_14_body',
  },
  {
    code: 'GRADE_16',
    icon: <Layers3 size={20} />,
    titleKey: 'gradeStructure.type.grade_16',
    bodyKey: 'gradeStructure.template_picker.grade_16_body',
  },
  {
    code: 'CUSTOM',
    icon: <Sparkles size={20} />,
    titleKey: 'gradeStructure.type.custom',
    bodyKey: 'gradeStructure.template_picker.custom_body',
  },
];

export function GradeStructureTemplatePicker({ open, ...rest }: GradeStructureTemplatePickerProps) {
  // Mount fresh while open so the selection starts cleared each time.
  if (!open) return null;
  return <GradeStructureTemplatePickerBody {...rest} />;
}

function GradeStructureTemplatePickerBody({
  onCancel,
  onSelect,
}: Omit<GradeStructureTemplatePickerProps, 'open'>) {
  const { t } = useTranslation();
  const [selected, setSelected] = useState<GradeStructureTemplateCode | null>(null);
  const dialogRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onCancel();
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [onCancel]);

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="gs-template-picker-title"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
      onClick={(e) => {
        if (e.target === e.currentTarget) onCancel();
      }}
    >
      <div
        ref={dialogRef}
        className="bg-surface rounded-xl shadow-lg border border-border w-full max-w-xl p-6"
      >
        <h2 id="gs-template-picker-title" className="text-lg text-text-primary">
          {t('gradeStructure.template_picker.title')}
        </h2>
        <p className="text-sm text-text-secondary mt-1">
          {t('gradeStructure.template_picker.body')}
        </p>

        <ul className="mt-4 space-y-2" role="radiogroup">
          {OPTIONS.map((opt) => {
            const active = selected === opt.code;
            return (
              <li key={opt.code}>
                <button
                  type="button"
                  role="radio"
                  aria-checked={active}
                  onClick={() => setSelected(opt.code)}
                  data-testid={`gs-template-option-${opt.code}`}
                  className={cn(
                    'w-full text-left rounded-lg border p-3 flex items-start gap-3',
                    active
                      ? 'border-primary-500 bg-primary-50'
                      : 'border-border bg-surface hover:bg-divider',
                  )}
                >
                  <span className="text-primary-600 mt-0.5" aria-hidden>
                    {opt.icon}
                  </span>
                  <span className="flex-1 min-w-0">
                    <span className="block text-sm font-semibold text-text-primary">
                      {t(opt.titleKey)}
                    </span>
                    <span className="block text-xs text-text-secondary mt-1">
                      {t(opt.bodyKey)}
                    </span>
                  </span>
                </button>
              </li>
            );
          })}
        </ul>

        <div className="flex justify-end gap-2 mt-6">
          <Button variant="secondary" onClick={onCancel}>
            {t('common.cancel')}
          </Button>
          <Button
            disabled={!selected}
            onClick={() => selected && onSelect(selected)}
            data-testid="gs-template-picker-continue"
          >
            {t('common.continue')}
          </Button>
        </div>
      </div>
    </div>
  );
}
