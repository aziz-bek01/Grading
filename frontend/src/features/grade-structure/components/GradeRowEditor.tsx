import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { DrawerForm } from '@/shared/components/data-table/DrawerForm';
import { SUPPORTED_LOCALES, PRIMARY_LOCALE } from '@/shared/types/i18n';
import { cn } from '@/shared/lib/cn';
import type { Locale, LocalizedString } from '@/shared/types/common';
import type { Grade } from '../types';

interface GradeRowEditorProps {
  open: boolean;
  grade: Grade | null; // null => create
  readOnly?: boolean;
  onClose: () => void;
  onSubmit: (input: {
    grade_number: number;
    name: LocalizedString;
    description?: LocalizedString;
    min_score: number;
    max_score: number;
  }) => Promise<void> | void;
}

/**
 * Drawer for editing/creating a single grade. Localised name / description
 * with 4 locale tabs + min/max score (4-decimal precision). When `readOnly`
 * is true (APPROVED/LOCKED/ARCHIVED), inputs become divs.
 */
export function GradeRowEditor({ open, grade, ...rest }: GradeRowEditorProps) {
  // Keyed remount per (open, grade) so the body re-initializes its form state
  // from props instead of resetting via a setState-in-effect.
  if (!open) return null;
  return (
    <GradeRowEditorBody key={`${grade?.id ?? 'new'}`} open={open} grade={grade} {...rest} />
  );
}

function GradeRowEditorBody({
  open,
  grade,
  readOnly,
  onClose,
  onSubmit,
}: GradeRowEditorProps) {
  const { t } = useTranslation();
  const [gradeNumber, setGradeNumber] = useState<number>(grade?.grade_number ?? 1);
  const [name, setName] = useState<LocalizedString>(grade?.name ?? {});
  const [description, setDescription] = useState<LocalizedString>(grade?.description ?? {});
  const [minScore, setMinScore] = useState<string>(
    grade?.band ? String(grade.band.min_score) : '0',
  );
  const [maxScore, setMaxScore] = useState<string>(
    grade?.band ? String(grade.band.max_score) : '0',
  );
  const [tab, setTab] = useState<Locale>(PRIMARY_LOCALE);
  const [error, setError] = useState<string | null>(null);

  const minNum = Number(minScore);
  const maxNum = Number(maxScore);
  const primaryName = (name[PRIMARY_LOCALE] ?? '').trim();

  const valid = useMemo(() => {
    if (!primaryName) return false;
    if (!Number.isFinite(minNum) || minNum < 0) return false;
    if (!Number.isFinite(maxNum) || maxNum < 0) return false;
    if (minNum > maxNum) return false;
    return true;
  }, [primaryName, minNum, maxNum]);

  const handleSubmit = async () => {
    if (!primaryName) {
      setError(t('gradeStructure.row_editor.error_name_required'));
      return;
    }
    if (minNum > maxNum) {
      setError(t('gradeStructure.row_editor.error_min_gt_max'));
      return;
    }
    setError(null);
    await onSubmit({
      grade_number: Number(gradeNumber),
      name,
      description: Object.values(description).some((v) => (v ?? '').trim()) ? description : undefined,
      min_score: minNum,
      max_score: maxNum,
    });
  };

  return (
    <DrawerForm
      open={open}
      title={
        grade
          ? t('gradeStructure.row_editor.edit_title', { number: grade.grade_number })
          : t('gradeStructure.row_editor.create_title')
      }
      onClose={onClose}
      onSubmit={handleSubmit}
      readOnly={readOnly}
      submitLabel={t('common.save')}
    >
      <div className="space-y-4" data-testid="grade-row-editor">
        <div className="grid grid-cols-2 gap-3">
          <label className="text-sm">
            <span className="text-text-secondary">
              {t('gradeStructure.row_editor.grade_number')}
            </span>
            <input
              type="number"
              min={1}
              max={99}
              value={gradeNumber}
              disabled={readOnly}
              onChange={(e) => setGradeNumber(Number(e.target.value))}
              className="mt-1 w-full h-9 px-3 rounded-md border border-border-strong text-sm focus:border-primary-500 focus:outline-none"
              data-testid="grade-row-editor-number"
            />
          </label>
        </div>

        {/* Multilingual name tabs */}
        <div>
          <div className="flex items-center gap-1" role="tablist">
            {SUPPORTED_LOCALES.map((loc) => {
              const filled = !!name[loc]?.trim();
              return (
                <button
                  key={loc}
                  type="button"
                  role="tab"
                  aria-selected={tab === loc}
                  onClick={() => setTab(loc)}
                  data-testid={`grade-row-editor-tab-${loc}`}
                  className={cn(
                    'inline-flex items-center gap-1 text-xs px-2 py-1 rounded border',
                    tab === loc
                      ? 'border-primary-500 bg-primary-50 text-primary-700'
                      : 'border-border text-text-secondary hover:bg-divider',
                  )}
                >
                  <span
                    className={cn(
                      'w-1.5 h-1.5 rounded-full',
                      filled ? 'bg-success-500' : 'bg-border-strong',
                    )}
                    aria-hidden
                  />
                  {loc.split('-')[0].toUpperCase()}
                  {loc === PRIMARY_LOCALE ? '*' : ''}
                </button>
              );
            })}
          </div>
          <label className="block text-sm mt-2">
            <span className="text-text-secondary">
              {t('gradeStructure.row_editor.name_label')}{' '}
              {tab === PRIMARY_LOCALE ? <span className="text-danger-600">*</span> : null}
            </span>
            <input
              value={name[tab] ?? ''}
              disabled={readOnly}
              onChange={(e) => setName({ ...name, [tab]: e.target.value })}
              className="mt-1 w-full h-9 px-3 rounded-md border border-border-strong text-sm focus:border-primary-500 focus:outline-none"
              data-testid={`grade-row-editor-name-${tab}`}
            />
          </label>
          <label className="block text-sm mt-2">
            <span className="text-text-secondary">
              {t('gradeStructure.row_editor.description_label')}
            </span>
            <textarea
              value={description[tab] ?? ''}
              disabled={readOnly}
              onChange={(e) => setDescription({ ...description, [tab]: e.target.value })}
              rows={3}
              className="mt-1 w-full px-3 py-2 rounded-md border border-border-strong text-sm focus:border-primary-500 focus:outline-none"
              data-testid={`grade-row-editor-description-${tab}`}
            />
          </label>
        </div>

        {/* Band */}
        <fieldset className="border border-border rounded-lg p-3">
          <legend className="text-xs text-text-secondary px-1">
            {t('gradeStructure.row_editor.band_legend')}
          </legend>
          <div className="grid grid-cols-2 gap-3">
            <label className="text-sm">
              <span className="text-text-secondary">
                {t('gradeStructure.row_editor.min_score')}
              </span>
              <input
                type="number"
                step="0.0001"
                min={0}
                value={minScore}
                disabled={readOnly}
                onChange={(e) => setMinScore(e.target.value)}
                className="mt-1 w-full h-9 px-3 rounded-md border border-border-strong text-sm tabular-nums focus:border-primary-500 focus:outline-none"
                data-testid="grade-row-editor-min-score"
              />
            </label>
            <label className="text-sm">
              <span className="text-text-secondary">
                {t('gradeStructure.row_editor.max_score')}
              </span>
              <input
                type="number"
                step="0.0001"
                min={0}
                value={maxScore}
                disabled={readOnly}
                onChange={(e) => setMaxScore(e.target.value)}
                className="mt-1 w-full h-9 px-3 rounded-md border border-border-strong text-sm tabular-nums focus:border-primary-500 focus:outline-none"
                data-testid="grade-row-editor-max-score"
              />
            </label>
          </div>
          {minNum > maxNum ? (
            <p
              className="text-xs text-danger-700 mt-2"
              data-testid="grade-row-editor-error-min-gt-max"
            >
              {t('gradeStructure.row_editor.error_min_gt_max')}
            </p>
          ) : null}
        </fieldset>

        {error ? (
          <p className="text-xs text-danger-700" data-testid="grade-row-editor-error">
            {error}
          </p>
        ) : null}
        {!valid && primaryName === '' ? (
          <p className="text-xs text-warning-700">
            {t('gradeStructure.row_editor.hint_primary_name')}
          </p>
        ) : null}
      </div>
    </DrawerForm>
  );
}
