import { useTranslation } from 'react-i18next';
import { Layers, Layers3, Sparkles } from 'lucide-react';
import {
  TemplatePicker,
  type TemplatePickerOption,
} from '@/shared/components/template-management/TemplatePicker';
import { PERMISSIONS } from '@/shared/types/permissions';
import { pickLocalized } from '@/shared/lib/localized';
import { useGradeTemplates } from '../hooks/useGradeStructure';
import type { Locale } from '@/shared/types/common';
import type { GradeStructureTemplate, GradeStructureType } from '../types';

/**
 * The picker's selection result. Always carries the concrete `code` (built-in
 * registry code OR a tenant CUSTOM template code) sent to /from-template.
 * `isCustom` flags tenant CUSTOM templates (snapshot instantiation).
 */
export interface GradeTemplateSelection {
  code: string;
  /** The structure_type, used for the default-name heuristic. */
  structureType: GradeStructureType;
  isCustom: boolean;
}

interface GradeStructureTemplatePickerProps {
  open: boolean;
  locale?: Locale;
  onCancel: () => void;
  onSelect: (selection: GradeTemplateSelection) => void;
  /** Open the rename drawer for a CUSTOM template (manage flow). */
  onRenameTemplate?: (template: GradeStructureTemplate) => void;
  /** Open the archive confirm for a CUSTOM template (manage flow). */
  onArchiveTemplate?: (template: GradeStructureTemplate) => void;
}

const ICONS: Record<GradeStructureType, React.ReactNode> = {
  GRADE_14: <Layers size={20} />,
  GRADE_16: <Layers3 size={20} />,
  CUSTOM: <Sparkles size={20} />,
};

/** Static fallback used when the templates endpoint is unavailable. */
const FALLBACK_OPTIONS: {
  code: GradeStructureType;
  titleKey: string;
  bodyKey: string;
}[] = [
  {
    code: 'GRADE_14',
    titleKey: 'gradeStructure.type.grade_14',
    bodyKey: 'gradeStructure.template_picker.grade_14_body',
  },
  {
    code: 'GRADE_16',
    titleKey: 'gradeStructure.type.grade_16',
    bodyKey: 'gradeStructure.template_picker.grade_16_body',
  },
  {
    code: 'CUSTOM',
    titleKey: 'gradeStructure.type.custom',
    bodyKey: 'gradeStructure.template_picker.custom_body',
  },
];

/**
 * Modal that asks "Which grade-structure template do you want to start from?".
 *
 * Data-driven from GET /grade-structure-templates (BE-9): built-ins ∪ tenant
 * CUSTOM templates. Falls back to the static option set when the call fails / is
 * loading so the picker is never empty. CUSTOM (from-scratch) is always offered.
 *
 * Each custom template carries a "Custom" badge; built-ins carry a "Built-in"
 * badge. For CUSTOM templates the picker offers Rename / Archive, gated by
 * GRADE_EDIT; built-ins are read-only. Thin wrapper around the shared
 * entity-agnostic `TemplatePicker` (mirrors the methodology twin).
 */
export function GradeStructureTemplatePicker({
  open,
  ...rest
}: GradeStructureTemplatePickerProps) {
  if (!open) return null;
  return <GradeStructureTemplatePickerBody {...rest} />;
}

function GradeStructureTemplatePickerBody({
  locale,
  onCancel,
  onSelect,
  onRenameTemplate,
  onArchiveTemplate,
}: Omit<GradeStructureTemplatePickerProps, 'open'>) {
  const { t, i18n } = useTranslation();
  const templatesQuery = useGradeTemplates();
  const activeLocale = (locale ?? (i18n.language as Locale)) as Locale;

  const remote = templatesQuery.data?.items ?? [];
  const useRemote = !templatesQuery.isError && remote.length > 0;

  const options: TemplatePickerOption<GradeTemplateSelection, GradeStructureTemplate>[] =
    useRemote
      ? buildRemoteOptions(remote, activeLocale, t)
      : FALLBACK_OPTIONS.map((opt) => ({
          code: opt.code,
          title: t(opt.titleKey),
          body: t(opt.bodyKey),
          icon: ICONS[opt.code],
          isCustom: false,
          canManage: false,
          selection: { code: opt.code, structureType: opt.code, isCustom: false },
        }));

  return (
    <TemplatePicker
      titleId="gs-template-picker-title"
      title={t('gradeStructure.template_picker.title')}
      body={t('gradeStructure.template_picker.body')}
      options={options}
      customBadgeLabel={t('gradeStructure.template_source.custom')}
      builtinBadgeLabel={t('gradeStructure.template_source.builtin')}
      renameLabel={t('gradeStructure.manage_templates.rename')}
      archiveLabel={t('gradeStructure.manage_templates.archive')}
      permission={PERMISSIONS.GRADE_EDIT}
      onCancel={onCancel}
      onSelect={onSelect}
      onRenameTemplate={onRenameTemplate}
      onArchiveTemplate={onArchiveTemplate}
      testIdPrefix="gs-template"
    />
  );
}

/**
 * Map the live template catalog to picker options. Uses the catalog's localized
 * name/description when present, falling back to the static i18n strings (so a
 * sparse backend translation never leaves a blank row). The empty-from-scratch
 * CUSTOM option is appended if the backend doesn't return it.
 */
function buildRemoteOptions(
  remote: GradeStructureTemplate[],
  locale: Locale,
  t: (key: string) => string,
): TemplatePickerOption<GradeTemplateSelection, GradeStructureTemplate>[] {
  const fallbackTitle: Record<GradeStructureType, string> = {
    GRADE_14: 'gradeStructure.type.grade_14',
    GRADE_16: 'gradeStructure.type.grade_16',
    CUSTOM: 'gradeStructure.type.custom',
  };
  const fallbackBody: Record<GradeStructureType, string> = {
    GRADE_14: 'gradeStructure.template_picker.grade_14_body',
    GRADE_16: 'gradeStructure.template_picker.grade_16_body',
    CUSTOM: 'gradeStructure.template_picker.custom_body',
  };

  const mapped: TemplatePickerOption<GradeTemplateSelection, GradeStructureTemplate>[] =
    remote.map((tpl) => {
      const structureType = tpl.structure_type ?? 'CUSTOM';
      const title =
        pickLocalized(tpl.name_i18n, locale) ||
        t(fallbackTitle[structureType] ?? fallbackTitle.CUSTOM);
      const body =
        pickLocalized(tpl.description_i18n, locale) ||
        t(fallbackBody[structureType] ?? fallbackBody.CUSTOM);
      const isCustom = !tpl.is_builtin;
      return {
        code: tpl.code,
        title,
        body,
        icon: ICONS[structureType] ?? ICONS.CUSTOM,
        isCustom,
        canManage: isCustom && !!tpl.id,
        selection: { code: tpl.code, structureType, isCustom },
        template: tpl,
      };
    });

  // Ensure the empty-from-scratch CUSTOM option is always present.
  if (!mapped.some((o) => o.code === 'CUSTOM')) {
    mapped.push({
      code: 'CUSTOM',
      title: t(fallbackTitle.CUSTOM),
      body: t(fallbackBody.CUSTOM),
      icon: ICONS.CUSTOM,
      isCustom: false,
      canManage: false,
      selection: { code: 'CUSTOM', structureType: 'CUSTOM', isCustom: false },
    });
  }
  return mapped;
}
