import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { DrawerForm } from '@/shared/components/data-table/DrawerForm';
import { LocalizedNameTabs } from '@/features/projects/components/LocalizedNameTabs';
import { FactorLevelEditor } from './FactorLevelEditor';
import type { LocalizedString } from '@/shared/types/common';
import type { Factor, FactorLevel, ScoringMode } from '../types';

interface FactorEditorProps {
  open: boolean;
  factor: Factor | null;
  scoringMode: ScoringMode;
  readOnly?: boolean;
  onClose: () => void;
  onSubmit: (patch: {
    code: string;
    name_i18n: LocalizedString;
    description_i18n?: LocalizedString;
    weight: number;
    max_points: number;
    required: boolean;
  }) => void;
  /** Level CRUD callbacks bubble up to the page so it can call mutations. */
  onAddLevel?: (next: Omit<FactorLevel, 'id' | 'factor_id'>) => void;
  onUpdateLevel?: (lvl: FactorLevel) => void;
  onRemoveLevel?: (lvl: FactorLevel) => void;
  onReorderLevel?: (lvl: FactorLevel, direction: 'up' | 'down') => void;
}

/**
 * Drawer for creating / editing a factor.
 * Mounts a nested FactorLevelEditor when editing an existing factor.
 */
export function FactorEditor({
  open,
  factor,
  scoringMode,
  readOnly,
  onClose,
  onSubmit,
  onAddLevel,
  onUpdateLevel,
  onRemoveLevel,
  onReorderLevel,
}: FactorEditorProps) {
  const { t } = useTranslation();
  const [code, setCode] = useState('');
  const [name, setName] = useState<LocalizedString>({});
  // description tabs (reserved Phase 4.1 — kept in state so future panel can wire it).
  const [description, setDescription] = useState<LocalizedString>({});
  const [weight, setWeight] = useState('0');
  const [maxPoints, setMaxPoints] = useState('0');
  const [required, setRequired] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    if (factor) {
      setCode(factor.code);
      setName(factor.name_i18n ?? {});
      setDescription(factor.description_i18n ?? {});
      setWeight(String(factor.weight));
      setMaxPoints(String(factor.max_points));
      setRequired(factor.required);
    } else {
      setCode('');
      setName({});
      setDescription({});
      setWeight('0');
      setMaxPoints('0');
      setRequired(true);
    }
    setError(null);
  }, [open, factor?.id]); // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <DrawerForm
      open={open}
      title={factor ? t('methodology.factor_editor.edit_title') : t('methodology.factor_editor.create_title')}
      onClose={onClose}
      readOnly={readOnly}
      onSubmit={() => {
        if (!code.trim()) return setError(t('factor.validation.code_required'));
        const ru = (name['ru-RU'] ?? '').trim();
        if (!ru) return setError(t('factor.validation.name_primary_required'));
        const w = Number.parseFloat(weight);
        const m = Number.parseFloat(maxPoints);
        if (Number.isNaN(w) || w < 0) return setError(t('factor.validation.weight_invalid'));
        if (Number.isNaN(m) || m < 0) return setError(t('factor.validation.max_points_invalid'));
        onSubmit({
          code: code.trim(),
          name_i18n: name,
          description_i18n: description,
          weight: w,
          max_points: m,
          required,
        });
      }}
    >
      <div className="space-y-4">
        <label className="block text-sm font-medium text-text-primary">
          <span>
            {t('factor.field.code')}{' '}
            <span className="text-danger-700">*</span>
          </span>
          <input
            type="text"
            value={code}
            onChange={(e) => setCode(e.target.value.toUpperCase())}
            placeholder="FACTOR_A"
            disabled={readOnly}
            className="mt-1 w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500 font-mono"
            data-testid="factor-code"
          />
        </label>

        <LocalizedNameTabs
          value={name}
          onChange={setName}
          label={t('factor.field.name')}
        />

        <LocalizedNameTabs
          value={description}
          onChange={setDescription}
          label={t('factor.field.description')}
        />

        {scoringMode !== 'DIRECT_POINTS' ? (
          <label className="block text-sm font-medium text-text-primary">
            <span>{t('factor.field.weight')}</span>
            <input
              type="number"
              step="any"
              value={weight}
              onChange={(e) => setWeight(e.target.value)}
              disabled={readOnly}
              className="mt-1 w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500 tabular-nums"
              data-testid="factor-weight"
            />
          </label>
        ) : null}

        <label className="block text-sm font-medium text-text-primary">
          <span>{t('factor.field.max_points')}</span>
          <input
            type="number"
            step="any"
            value={maxPoints}
            onChange={(e) => setMaxPoints(e.target.value)}
            disabled={readOnly}
            className="mt-1 w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500 tabular-nums"
            data-testid="factor-max-points"
          />
        </label>

        <label className="inline-flex items-center gap-2 text-sm">
          <input
            type="checkbox"
            checked={required}
            onChange={(e) => setRequired(e.target.checked)}
            disabled={readOnly}
            data-testid="factor-required"
          />
          <span>{t('factor.field.required')}</span>
        </label>

        {error ? (
          <p className="text-xs text-danger-700" role="alert" data-testid="factor-editor-error">
            {error}
          </p>
        ) : null}

        {factor ? (
          <FactorLevelEditor
            levels={factor.levels ?? []}
            scoringMode={scoringMode}
            readOnly={readOnly}
            onAdd={onAddLevel}
            onUpdate={onUpdateLevel}
            onRemove={onRemoveLevel}
            onReorder={onReorderLevel}
          />
        ) : (
          <p className="text-xs text-text-secondary">
            {t('factor.levels_after_create')}
          </p>
        )}
      </div>
    </DrawerForm>
  );
}

