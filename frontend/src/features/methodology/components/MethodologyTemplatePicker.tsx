import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Layers, GanttChartSquare, Sparkles, Pencil, Archive } from 'lucide-react';
import { Button } from '@/shared/components/ui/Button';
import { PermissionGate } from '@/shared/components/access/PermissionGate';
import { StatusBadge } from '@/shared/components/status/StatusBadge';
import { PERMISSIONS } from '@/shared/types/permissions';
import { cn } from '@/shared/lib/cn';
import { useMethodologyTemplates } from '../hooks/useMethodology';
import type { Locale } from '@/shared/types/common';
import type { MethodologyTemplate, MethodologyType } from '../types';

/**
 * The picker's selection result. Always carries the concrete `template_code`
 * (built-in registry code OR a tenant CUSTOM code) so the create flow can send
 * the exact code to /methodologies/from-template. `kind` keeps the legacy
 * MethodologyType union for the three built-ins (used to route CUSTOM →
 * from-scratch and to pick a default name); custom templates report `kind:
 * 'CUSTOM'` because they instantiate a snapshot, not the empty-from-scratch path.
 */
export interface TemplateSelection {
  /** The concrete template code sent on the wire (built-in or tenant CUSTOM). */
  code: string;
  /** Legacy union — drives default-name + the CUSTOM→from-scratch branch. */
  kind: MethodologyType;
  /** True for tenant CUSTOM templates (instantiate snapshot, never from-scratch). */
  isCustom: boolean;
}

interface MethodologyTemplatePickerProps {
  open: boolean;
  locale?: Locale;
  onCancel: () => void;
  onSelect: (selection: TemplateSelection) => void;
  /** Open the rename drawer for a CUSTOM template (manage flow). */
  onRenameTemplate?: (template: MethodologyTemplate) => void;
  /** Open the archive confirm for a CUSTOM template (manage flow). */
  onArchiveTemplate?: (template: MethodologyTemplate) => void;
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
 * Data-driven from GET /methodology-templates (F8 / Epic E): built-ins ∪ tenant
 * CUSTOM templates. Falls back to the static option set if the call fails / is
 * loading so the picker is never empty. CUSTOM (from-scratch) is always offered.
 *
 * Epic E: each custom template carries a "Custom" badge; built-ins carry a
 * "Built-in" badge. For CUSTOM templates the picker offers Rename / Archive,
 * gated by METHODOLOGY_EDIT; built-ins are read-only (no manage affordances).
 */
export function MethodologyTemplatePicker({ open, ...rest }: MethodologyTemplatePickerProps) {
  // Mount fresh while open so the selection starts cleared each time.
  if (!open) return null;
  return <MethodologyTemplatePickerBody {...rest} />;
}

/** A picker row option, derived from a live template or the static fallback. */
interface PickerOption {
  /** Stable key — the template code. */
  code: string;
  kind: MethodologyType;
  title: string;
  body: string;
  icon: React.ReactNode;
  isCustom: boolean;
  /** The source template (only for live/custom rows — drives manage actions). */
  template?: MethodologyTemplate;
}

function MethodologyTemplatePickerBody({
  locale,
  onCancel,
  onSelect,
  onRenameTemplate,
  onArchiveTemplate,
}: Omit<MethodologyTemplatePickerProps, 'open'>) {
  const { t, i18n } = useTranslation();
  const [selected, setSelected] = useState<string | null>(null);
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

  const options: PickerOption[] = useRemote
    ? buildRemoteOptions(remote, activeLocale, t)
    : FALLBACK_OPTIONS.map((opt) => ({
        code: opt.type,
        kind: opt.type,
        title: t(opt.titleKey),
        body: t(opt.bodyKey),
        icon: ICONS[opt.type],
        isCustom: false,
      }));

  const selectedOption = options.find((o) => o.code === selected) ?? null;

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

        <ul className="mt-4 space-y-2 max-h-[60vh] overflow-y-auto" role="radiogroup">
          {options.map((opt) => {
            const active = selected === opt.code;
            const canManage = opt.isCustom && !!opt.template?.id;
            return (
              <li key={opt.code}>
                <div
                  className={cn(
                    'w-full rounded-lg border p-3 flex items-start gap-3',
                    active
                      ? 'border-primary-500 bg-primary-50'
                      : 'border-border bg-surface hover:bg-divider',
                  )}
                >
                  <button
                    type="button"
                    role="radio"
                    aria-checked={active}
                    onClick={() => setSelected(opt.code)}
                    data-testid={`template-option-${opt.code}`}
                    className="flex-1 min-w-0 text-left flex items-start gap-3"
                  >
                    <span className="text-primary-600 mt-0.5" aria-hidden>
                      {opt.icon}
                    </span>
                    <span className="flex-1 min-w-0">
                      <span className="flex items-center gap-2 flex-wrap">
                        <span className="text-sm font-semibold text-text-primary">{opt.title}</span>
                        {opt.isCustom ? (
                          <StatusBadge
                            tone="ai-suggestion"
                            label={t('methodology.template_source.custom')}
                          />
                        ) : (
                          <StatusBadge
                            tone="approved"
                            label={t('methodology.template_source.builtin')}
                          />
                        )}
                      </span>
                      <span className="block text-xs text-text-secondary mt-1">{opt.body}</span>
                    </span>
                  </button>

                  {canManage ? (
                    <PermissionGate permission={PERMISSIONS.METHODOLOGY_EDIT}>
                      <span className="flex items-center gap-1 shrink-0">
                        <Button
                          variant="ghost"
                          size="sm"
                          aria-label={t('methodology.manage_templates.rename')}
                          data-testid={`template-${opt.code}-rename`}
                          onClick={() => opt.template && onRenameTemplate?.(opt.template)}
                        >
                          <Pencil size={14} />
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          aria-label={t('methodology.manage_templates.archive')}
                          data-testid={`template-${opt.code}-archive`}
                          onClick={() => opt.template && onArchiveTemplate?.(opt.template)}
                        >
                          <Archive size={14} className="text-danger-700" />
                        </Button>
                      </span>
                    </PermissionGate>
                  ) : null}
                </div>
              </li>
            );
          })}
        </ul>

        <div className="flex justify-end gap-2 mt-6">
          <Button variant="secondary" onClick={onCancel}>
            {t('common.cancel')}
          </Button>
          <Button
            disabled={!selectedOption}
            onClick={() =>
              selectedOption &&
              onSelect({
                code: selectedOption.code,
                kind: selectedOption.kind,
                isCustom: selectedOption.isCustom,
              })
            }
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
 * sparse backend translation never leaves a blank row). The built-in CUSTOM
 * (from-scratch) option is appended if the backend doesn't return it.
 */
function buildRemoteOptions(
  remote: MethodologyTemplate[],
  locale: Locale,
  t: (key: string) => string,
): PickerOption[] {
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

  const mapped: PickerOption[] = remote.map((tpl) => {
    // `kind` is the registry union for built-ins; tenant CUSTOM templates
    // instantiate a snapshot, so they report kind CUSTOM (not from-scratch).
    const kind: MethodologyType = tpl.is_builtin
      ? (tpl.code as MethodologyType)
      : 'CUSTOM';
    const title =
      tpl.name_i18n?.[locale] ?? tpl.name_i18n?.['ru-RU'] ?? t(fallbackTitle[kind] ?? fallbackTitle.CUSTOM);
    const body =
      tpl.description_i18n?.[locale] ??
      tpl.description_i18n?.['ru-RU'] ??
      t(tpl.factor_count > 0 ? fallbackBody[kind] ?? fallbackBody.CUSTOM : 'methodology.template_picker.custom_body');
    return {
      code: tpl.code,
      kind,
      title,
      body,
      icon: ICONS[kind] ?? ICONS.CUSTOM,
      isCustom: !tpl.is_builtin,
      template: tpl,
    };
  });

  // Ensure the empty-from-scratch CUSTOM option is always present.
  if (!mapped.some((o) => o.code === 'CUSTOM')) {
    mapped.push({
      code: 'CUSTOM',
      kind: 'CUSTOM',
      title: t(fallbackTitle.CUSTOM),
      body: t(fallbackBody.CUSTOM),
      icon: ICONS.CUSTOM,
      isCustom: false,
    });
  }
  return mapped;
}
