import { useTranslation } from 'react-i18next';
import { Layers, GanttChartSquare, Sparkles } from 'lucide-react';
import {
  TemplatePicker,
  type TemplatePickerOption,
} from '@/shared/components/template-management/TemplatePicker';
import { PERMISSIONS } from '@/shared/types/permissions';
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
 * Thin wrapper around the shared entity-agnostic `TemplatePicker` (mirrors the
 * grade-structure twin).
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
  onRenameTemplate,
  onArchiveTemplate,
}: Omit<MethodologyTemplatePickerProps, 'open'>) {
  const { t, i18n } = useTranslation();
  const templatesQuery = useMethodologyTemplates();
  const activeLocale = (locale ?? (i18n.language as Locale)) as Locale;

  // Prefer the live catalog; fall back to the static set on error / empty.
  const remote = templatesQuery.data?.items ?? [];
  const useRemote = !templatesQuery.isError && remote.length > 0;

  const options: TemplatePickerOption<TemplateSelection, MethodologyTemplate>[] = useRemote
    ? buildRemoteOptions(remote, activeLocale, t)
    : FALLBACK_OPTIONS.map((opt) => ({
        code: opt.type,
        title: t(opt.titleKey),
        body: t(opt.bodyKey),
        icon: ICONS[opt.type],
        isCustom: false,
        canManage: false,
        selection: { code: opt.type, kind: opt.type, isCustom: false },
      }));

  return (
    <TemplatePicker
      titleId="template-picker-title"
      title={t('methodology.template_picker.title')}
      body={t('methodology.template_picker.body')}
      options={options}
      customBadgeLabel={t('methodology.template_source.custom')}
      builtinBadgeLabel={t('methodology.template_source.builtin')}
      renameLabel={t('methodology.manage_templates.rename')}
      archiveLabel={t('methodology.manage_templates.archive')}
      permission={PERMISSIONS.METHODOLOGY_EDIT}
      onCancel={onCancel}
      onSelect={onSelect}
      onRenameTemplate={onRenameTemplate}
      onArchiveTemplate={onArchiveTemplate}
      testIdPrefix="template"
    />
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
): TemplatePickerOption<TemplateSelection, MethodologyTemplate>[] {
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

  const mapped: TemplatePickerOption<TemplateSelection, MethodologyTemplate>[] = remote.map(
    (tpl) => {
      // `kind` is the registry union for built-ins; tenant CUSTOM templates
      // instantiate a snapshot, so they report kind CUSTOM (not from-scratch).
      const kind: MethodologyType = tpl.is_builtin ? (tpl.code as MethodologyType) : 'CUSTOM';
      const title =
        tpl.name_i18n?.[locale] ??
        tpl.name_i18n?.['ru-RU'] ??
        t(fallbackTitle[kind] ?? fallbackTitle.CUSTOM);
      const body =
        tpl.description_i18n?.[locale] ??
        tpl.description_i18n?.['ru-RU'] ??
        t(
          tpl.factor_count > 0
            ? fallbackBody[kind] ?? fallbackBody.CUSTOM
            : 'methodology.template_picker.custom_body',
        );
      const isCustom = !tpl.is_builtin;
      return {
        code: tpl.code,
        title,
        body,
        icon: ICONS[kind] ?? ICONS.CUSTOM,
        isCustom,
        canManage: isCustom && !!tpl.id,
        selection: { code: tpl.code, kind, isCustom },
        template: tpl,
      };
    },
  );

  // Ensure the empty-from-scratch CUSTOM option is always present.
  if (!mapped.some((o) => o.code === 'CUSTOM')) {
    mapped.push({
      code: 'CUSTOM',
      title: t(fallbackTitle.CUSTOM),
      body: t(fallbackBody.CUSTOM),
      icon: ICONS.CUSTOM,
      isCustom: false,
      canManage: false,
      selection: { code: 'CUSTOM', kind: 'CUSTOM', isCustom: false },
    });
  }
  return mapped;
}
