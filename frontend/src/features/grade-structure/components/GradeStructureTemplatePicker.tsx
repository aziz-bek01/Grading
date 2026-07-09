import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Layers, Layers3, Sparkles, Pencil, Archive } from 'lucide-react';
import { Button } from '@/shared/components/ui/Button';
import { Modal } from '@/shared/components/ui/Modal';
import { PermissionGate } from '@/shared/components/access/PermissionGate';
import { StatusBadge } from '@/shared/components/status/StatusBadge';
import { PERMISSIONS } from '@/shared/types/permissions';
import { cn } from '@/shared/lib/cn';
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
 * GRADE_EDIT; built-ins are read-only. Mirrors the methodology TemplateCatalog.
 */
export function GradeStructureTemplatePicker({
  open,
  ...rest
}: GradeStructureTemplatePickerProps) {
  if (!open) return null;
  return <GradeStructureTemplatePickerBody {...rest} />;
}

interface PickerOption {
  code: string;
  structureType: GradeStructureType;
  title: string;
  body: string;
  icon: React.ReactNode;
  isCustom: boolean;
  template?: GradeStructureTemplate;
}

function GradeStructureTemplatePickerBody({
  locale,
  onCancel,
  onSelect,
  onRenameTemplate,
  onArchiveTemplate,
}: Omit<GradeStructureTemplatePickerProps, 'open'>) {
  const { t, i18n } = useTranslation();
  const [selected, setSelected] = useState<string | null>(null);
  const templatesQuery = useGradeTemplates();
  const activeLocale = (locale ?? (i18n.language as Locale)) as Locale;

  const remote = templatesQuery.data?.items ?? [];
  const useRemote = !templatesQuery.isError && remote.length > 0;

  const options: PickerOption[] = useRemote
    ? buildRemoteOptions(remote, activeLocale, t)
    : FALLBACK_OPTIONS.map((opt) => ({
        code: opt.code,
        structureType: opt.code,
        title: t(opt.titleKey),
        body: t(opt.bodyKey),
        icon: ICONS[opt.code],
        isCustom: false,
      }));

  const selectedOption = options.find((o) => o.code === selected) ?? null;

  return (
    <Modal
      open
      onClose={onCancel}
      labelledBy="gs-template-picker-title"
      size="xl"
      className="bg-surface rounded-xl shadow-lg border border-border p-6"
    >
      <>
        <h2 id="gs-template-picker-title" className="text-lg text-text-primary">
          {t('gradeStructure.template_picker.title')}
        </h2>
        <p className="text-sm text-text-secondary mt-1">
          {t('gradeStructure.template_picker.body')}
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
                    data-testid={`gs-template-option-${opt.code}`}
                    className="flex-1 min-w-0 text-left flex items-start gap-3"
                  >
                    <span className="text-primary-600 mt-0.5" aria-hidden>
                      {opt.icon}
                    </span>
                    <span className="flex-1 min-w-0">
                      <span className="flex items-center gap-2 flex-wrap">
                        <span className="text-sm font-semibold text-text-primary">
                          {opt.title}
                        </span>
                        {opt.isCustom ? (
                          <StatusBadge
                            tone="ai-suggestion"
                            label={t('gradeStructure.template_source.custom')}
                          />
                        ) : (
                          <StatusBadge
                            tone="approved"
                            label={t('gradeStructure.template_source.builtin')}
                          />
                        )}
                      </span>
                      <span className="block text-xs text-text-secondary mt-1">
                        {opt.body}
                      </span>
                    </span>
                  </button>

                  {canManage ? (
                    <PermissionGate permission={PERMISSIONS.GRADE_EDIT}>
                      <span className="flex items-center gap-1 shrink-0">
                        <Button
                          variant="ghost"
                          size="sm"
                          aria-label={t('gradeStructure.manage_templates.rename')}
                          data-testid={`gs-template-${opt.code}-rename`}
                          onClick={() => opt.template && onRenameTemplate?.(opt.template)}
                        >
                          <Pencil size={14} />
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          aria-label={t('gradeStructure.manage_templates.archive')}
                          data-testid={`gs-template-${opt.code}-archive`}
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
                structureType: selectedOption.structureType,
                isCustom: selectedOption.isCustom,
              })
            }
            data-testid="gs-template-picker-continue"
          >
            {t('common.continue')}
          </Button>
        </div>
      </>
    </Modal>
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
): PickerOption[] {
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

  const mapped: PickerOption[] = remote.map((tpl) => {
    const structureType = tpl.structure_type ?? 'CUSTOM';
    const title =
      pickLocalized(tpl.name_i18n, locale) || t(fallbackTitle[structureType] ?? fallbackTitle.CUSTOM);
    const body =
      pickLocalized(tpl.description_i18n, locale) ||
      t(fallbackBody[structureType] ?? fallbackBody.CUSTOM);
    return {
      code: tpl.code,
      structureType,
      title,
      body,
      icon: ICONS[structureType] ?? ICONS.CUSTOM,
      isCustom: !tpl.is_builtin,
      template: tpl,
    };
  });

  // Ensure the empty-from-scratch CUSTOM option is always present.
  if (!mapped.some((o) => o.code === 'CUSTOM')) {
    mapped.push({
      code: 'CUSTOM',
      structureType: 'CUSTOM',
      title: t(fallbackTitle.CUSTOM),
      body: t(fallbackBody.CUSTOM),
      icon: ICONS.CUSTOM,
      isCustom: false,
    });
  }
  return mapped;
}
