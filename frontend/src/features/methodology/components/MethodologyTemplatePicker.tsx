import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Layers, GanttChartSquare, Sparkles } from 'lucide-react';
import { Button } from '@/shared/components/ui/Button';
import { cn } from '@/shared/lib/cn';
import { useMethodologyTemplates } from '../hooks/useMethodology';
import type { Locale } from '@/shared/types/common';
import type { MethodologyTemplate, MethodologyType } from '../types';

interface MethodologyTemplatePickerProps {
  open: boolean;
  locale?: Locale;
  onCancel: () => void;
  onSelect: (type: MethodologyType) => void;
}

const ICONS: Record<MethodologyType, React.ReactNode> = {
  CLASSIC_8_FACTOR: <Layers size={20} />,
  EXTENDED_11_CRITERIA: <GanttChartSquare size={20} />,
  CUSTOM: <Sparkles size={20} />,
};

/** Static fallback used when the templates endpoint is unavailable (F8). */
const FALLBACK_OPTIONS: { type: MethodologyType; titleKey: string; bodyKey: string }[] = [
  {
    type: 'CLASSIC_8_FACTOR',
    titleKey: 'methodology.type.classic_8_factor',
    bodyKey: 'methodology.template_picker.classic_body',
  },
  {
    type: 'EXTENDED_11_CRITERIA',
    titleKey: 'methodology.type.extended_11_criteria',
    bodyKey: 'methodology.template_picker.extended_body',
  },
  {
    type: 'CUSTOM',
    titleKey: 'methodology.type.custom',
    bodyKey: 'methodology.template_picker.custom_body',
  },
];

/**
 * Modal that asks "Which methodology template do you want to start from?"
 * before the create flow runs — per PRD MVP1-E7-1.
 *
 * Data-driven from GET /methodology-templates (F8) via useMethodologyTemplates;
 * falls back to the static option set if the call fails / is loading so the
 * picker is never empty. CUSTOM is always offered (the from-scratch path).
 */
export function MethodologyTemplatePicker({ open, ...rest }: MethodologyTemplatePickerProps) {
  // Mount fresh while open so the selection starts cleared each time.
  if (!open) return null;
  return <MethodologyTemplatePickerBody {...rest} />;
}

function MethodologyTemplatePickerBody({
  locale,
  onCancel,
  onSelect,
}: Omit<MethodologyTemplatePickerProps, 'open'>) {
  const { t, i18n } = useTranslation();
  const [selected, setSelected] = useState<MethodologyType | null>(null);
  const dialogRef = useRef<HTMLDivElement>(null);
  const templatesQuery = useMethodologyTemplates();
  const activeLocale = (locale ?? (i18n.language as Locale)) as Locale;

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onCancel();
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [onCancel]);

  // Prefer the live catalog; fall back to the static set on error / empty.
  const remote = templatesQuery.data?.items ?? [];
  const useRemote = !templatesQuery.isError && remote.length > 0;

  const options: {
    type: MethodologyType;
    title: string;
    body: string;
    icon: React.ReactNode;
  }[] = useRemote
    ? buildRemoteOptions(remote, activeLocale, t)
    : FALLBACK_OPTIONS.map((opt) => ({
        type: opt.type,
        title: t(opt.titleKey),
        body: t(opt.bodyKey),
        icon: ICONS[opt.type],
      }));

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
          {options.map((opt) => {
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
                      {opt.title}
                    </span>
                    <span className="block text-xs text-text-secondary mt-1">
                      {opt.body}
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

/**
 * Map the live template catalog to picker options. Uses the catalog's localized
 * name/description when present, falling back to the static i18n strings (so a
 * sparse backend translation never leaves a blank row). CUSTOM is appended if
 * the backend doesn't return it (the from-scratch path is always available).
 */
function buildRemoteOptions(
  remote: MethodologyTemplate[],
  locale: Locale,
  t: (key: string) => string,
) {
  const fallbackBody: Record<MethodologyType, string> = {
    CLASSIC_8_FACTOR: 'methodology.template_picker.classic_body',
    EXTENDED_11_CRITERIA: 'methodology.template_picker.extended_body',
    CUSTOM: 'methodology.template_picker.custom_body',
  };
  const fallbackTitle: Record<MethodologyType, string> = {
    CLASSIC_8_FACTOR: 'methodology.type.classic_8_factor',
    EXTENDED_11_CRITERIA: 'methodology.type.extended_11_criteria',
    CUSTOM: 'methodology.type.custom',
  };

  const mapped = remote.map((tpl) => {
    const type = tpl.code as MethodologyType;
    const title = tpl.name_i18n?.[locale] ?? tpl.name_i18n?.['ru-RU'] ?? t(fallbackTitle[type]);
    const body =
      tpl.description_i18n?.[locale] ??
      tpl.description_i18n?.['ru-RU'] ??
      t(
        tpl.factor_count > 0
          ? fallbackBody[type]
          : 'methodology.template_picker.custom_body',
      );
    return { type, title, body, icon: ICONS[type] ?? ICONS.CUSTOM };
  });

  if (!mapped.some((o) => o.type === 'CUSTOM')) {
    mapped.push({
      type: 'CUSTOM',
      title: t(fallbackTitle.CUSTOM),
      body: t(fallbackBody.CUSTOM),
      icon: ICONS.CUSTOM,
    });
  }
  return mapped;
}
