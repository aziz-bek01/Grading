import { useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Pencil, Archive } from 'lucide-react';
import { Button } from '@/shared/components/ui/Button';
import { Modal } from '@/shared/components/ui/Modal';
import { PermissionGate } from '@/shared/components/access/PermissionGate';
import { StatusBadge } from '@/shared/components/status/StatusBadge';
import { cn } from '@/shared/lib/cn';
import type { PermissionCode } from '@/shared/types/permissions';

/**
 * A single selectable row, already fully resolved by the caller (title/body
 * text, icon, source badge, and the exact `selection` payload to hand back to
 * `onSelect`). Building this list from the live template catalog — including
 * the localized-text fallback chain and any built-in-vs-custom mapping quirks
 * — is entity-specific and stays in each feature's thin wrapper.
 */
export interface TemplatePickerOption<TSelection, TTemplate> {
  /** Stable key — the template code. */
  code: string;
  title: string;
  body: string;
  icon: ReactNode;
  isCustom: boolean;
  /**
   * Whether this row offers rename/archive (still gated by `permission` on
   * top of this). Computed by the caller — mirrors each feature's original
   * `isCustom && !!template?.id` guard (a CUSTOM row with no persisted
   * template, e.g. the synthesized from-scratch option, never manages).
   */
  canManage: boolean;
  /** The exact value passed to `onSelect` when this row is confirmed. */
  selection: TSelection;
  /** The source template row (only for live/custom rows — drives manage actions). */
  template?: TTemplate;
}

export interface TemplatePickerProps<TSelection, TTemplate> {
  /** id of the `<h2>` heading, wired to the Modal's `aria-labelledby`. */
  titleId: string;
  title: string;
  body: string;
  options: TemplatePickerOption<TSelection, TTemplate>[];
  customBadgeLabel: string;
  builtinBadgeLabel: string;
  renameLabel: string;
  archiveLabel: string;
  /** Permission gating the rename/archive manage actions on CUSTOM rows. */
  permission: PermissionCode | PermissionCode[];
  onCancel: () => void;
  onSelect: (selection: TSelection) => void;
  /** Open the rename drawer for a CUSTOM template (manage flow). */
  onRenameTemplate?: (template: TTemplate) => void;
  /** Open the archive confirm for a CUSTOM template (manage flow). */
  onArchiveTemplate?: (template: TTemplate) => void;
  /** testid namespace — e.g. `gs-template` / `template`. Builds
   *  `${prefix}-option-${code}`, `${prefix}-picker-continue`,
   *  `${prefix}-${code}-rename`, `${prefix}-${code}-archive`. */
  testIdPrefix: string;
}

/**
 * Modal that asks "Which template do you want to start from?" — the shared
 * shell behind both `GradeStructureTemplatePicker` and
 * `MethodologyTemplatePicker`. Entity-agnostic: the catalog fetch, the
 * built-in-fallback option set, and the localized-text resolution stay in the
 * per-feature wrapper; this component only owns the interactive rendering
 * (radiogroup selection, source badges, manage actions, continue/cancel).
 *
 * Rendered only while the picker is open (the caller unmounts it on close),
 * so `selected` always starts cleared on a fresh open — no `open` prop here.
 */
export function TemplatePicker<TSelection, TTemplate>({
  titleId,
  title,
  body,
  options,
  customBadgeLabel,
  builtinBadgeLabel,
  renameLabel,
  archiveLabel,
  permission,
  onCancel,
  onSelect,
  onRenameTemplate,
  onArchiveTemplate,
  testIdPrefix,
}: TemplatePickerProps<TSelection, TTemplate>) {
  const { t } = useTranslation();
  const [selected, setSelected] = useState<string | null>(null);
  const selectedOption = options.find((o) => o.code === selected) ?? null;

  return (
    <Modal
      open
      onClose={onCancel}
      labelledBy={titleId}
      size="xl"
      className="bg-surface rounded-xl shadow-lg border border-border p-6"
    >
      <>
        <h2 id={titleId} className="text-lg text-text-primary">
          {title}
        </h2>
        <p className="text-sm text-text-secondary mt-1">{body}</p>

        <ul className="mt-4 space-y-2 max-h-[60vh] overflow-y-auto" role="radiogroup">
          {options.map((opt) => {
            const active = selected === opt.code;
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
                    data-testid={`${testIdPrefix}-option-${opt.code}`}
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
                          <StatusBadge tone="ai-suggestion" label={customBadgeLabel} />
                        ) : (
                          <StatusBadge tone="approved" label={builtinBadgeLabel} />
                        )}
                      </span>
                      <span className="block text-xs text-text-secondary mt-1">{opt.body}</span>
                    </span>
                  </button>

                  {opt.canManage ? (
                    <PermissionGate permission={permission}>
                      <span className="flex items-center gap-1 shrink-0">
                        <Button
                          variant="ghost"
                          size="sm"
                          aria-label={renameLabel}
                          data-testid={`${testIdPrefix}-${opt.code}-rename`}
                          onClick={() => opt.template && onRenameTemplate?.(opt.template)}
                        >
                          <Pencil size={14} />
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          aria-label={archiveLabel}
                          data-testid={`${testIdPrefix}-${opt.code}-archive`}
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
            onClick={() => selectedOption && onSelect(selectedOption.selection)}
            data-testid={`${testIdPrefix}-picker-continue`}
          >
            {t('common.continue')}
          </Button>
        </div>
      </>
    </Modal>
  );
}
