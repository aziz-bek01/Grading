import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Layers, GanttChartSquare, Sparkles } from 'lucide-react';
import { Button } from '@/shared/components/ui/Button';
import { cn } from '@/shared/lib/cn';
import type { Locale } from '@/shared/types/common';
import type { MethodologyType } from '../types';

interface MethodologyTemplatePickerProps {
  open: boolean;
  locale?: Locale;
  onCancel: () => void;
  onSelect: (type: MethodologyType) => void;
}

const OPTIONS: { type: MethodologyType; icon: React.ReactNode; titleKey: string; bodyKey: string }[] = [
  {
    type: 'CLASSIC_8_FACTOR',
    icon: <Layers size={20} />,
    titleKey: 'methodology.type.classic_8_factor',
    bodyKey: 'methodology.template_picker.classic_body',
  },
  {
    type: 'EXTENDED_11_CRITERIA',
    icon: <GanttChartSquare size={20} />,
    titleKey: 'methodology.type.extended_11_criteria',
    bodyKey: 'methodology.template_picker.extended_body',
  },
  {
    type: 'CUSTOM',
    icon: <Sparkles size={20} />,
    titleKey: 'methodology.type.custom',
    bodyKey: 'methodology.template_picker.custom_body',
  },
];

/**
 * Modal that asks "Which methodology template do you want to start from?"
 * before the create drawer opens — per PRD MVP1-E7-1.
 */
export function MethodologyTemplatePicker({ open, ...rest }: MethodologyTemplatePickerProps) {
  // Mount fresh while open so the selection starts cleared each time.
  if (!open) return null;
  return <MethodologyTemplatePickerBody {...rest} />;
}

function MethodologyTemplatePickerBody({
  onCancel,
  onSelect,
}: Omit<MethodologyTemplatePickerProps, 'open'>) {
  const { t } = useTranslation();
  const [selected, setSelected] = useState<MethodologyType | null>(null);
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
      aria-labelledby="template-picker-title"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
      onClick={(e) => {
        if (e.target === e.currentTarget) onCancel();
      }}
    >
      <div ref={dialogRef} className="bg-surface rounded-xl shadow-lg border border-border w-full max-w-xl p-6">
        <h2 id="template-picker-title" className="text-lg text-text-primary">
          {t('methodology.template_picker.title')}
        </h2>
        <p className="text-sm text-text-secondary mt-1">
          {t('methodology.template_picker.body')}
        </p>

        <ul className="mt-4 space-y-2" role="radiogroup">
          {OPTIONS.map((opt) => {
            const active = selected === opt.type;
            return (
              <li key={opt.type}>
                <button
                  type="button"
                  role="radio"
                  aria-checked={active}
                  onClick={() => setSelected(opt.type)}
                  data-testid={`template-option-${opt.type}`}
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
            data-testid="template-picker-continue"
          >
            {t('common.continue')}
          </Button>
        </div>
      </div>
    </div>
  );
}
