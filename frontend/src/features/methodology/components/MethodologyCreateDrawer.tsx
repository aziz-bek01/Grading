import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { DrawerForm } from '@/shared/components/data-table/DrawerForm';
import { LocalizedNameTabs } from '@/features/projects/components/LocalizedNameTabs';
import { ApiError } from '@/shared/api/apiError';
import type { LocalizedString } from '@/shared/types/common';
import type { MethodologyCreatePayload, MethodologyType, ScoringMode } from '../types';

interface MethodologyCreateDrawerProps {
  open: boolean;
  projectId: string;
  onClose: () => void;
  onSubmit: (payload: MethodologyCreatePayload) => Promise<void>;
}

const TYPE_OPTIONS: { value: MethodologyType; labelKey: string }[] = [
  { value: 'CLASSIC_8_FACTOR', labelKey: 'methodology.type.classic_8_factor' },
  { value: 'EXTENDED_11_CRITERIA', labelKey: 'methodology.type.extended_11_criteria' },
  { value: 'CUSTOM', labelKey: 'methodology.type.custom' },
];

const SCORING_OPTIONS: { value: ScoringMode; labelKey: string }[] = [
  { value: 'WEIGHTED_POINTS', labelKey: 'methodology.scoring_mode_label.weighted_points' },
  { value: 'DIRECT_POINTS', labelKey: 'methodology.scoring_mode_label.direct_points' },
  { value: 'WEIGHTED_SCALE', labelKey: 'methodology.scoring_mode_label.weighted_scale' },
];

/**
 * "Start from scratch" create drawer (F7).
 *
 * Wired to CreateMethodologyFromScratchUseCase (POST /methodologies). Unlike the
 * template picker, this lets the consultant pick the scoring mode + (for
 * WEIGHTED_SCALE) target_total_points up front, then seeds an EMPTY v1 the
 * builder fills factor-by-factor. The backend duplicate-code error is surfaced
 * inline on the code field.
 */
export function MethodologyCreateDrawer({ open, ...rest }: MethodologyCreateDrawerProps) {
  if (!open) return null;
  return <MethodologyCreateDrawerBody {...rest} />;
}

function MethodologyCreateDrawerBody({
  projectId,
  onClose,
  onSubmit,
}: Omit<MethodologyCreateDrawerProps, 'open'>) {
  const { t } = useTranslation();
  const [code, setCode] = useState('');
  const [name, setName] = useState<LocalizedString>({});
  const [description, setDescription] = useState<LocalizedString>({});
  const [methodologyType, setMethodologyType] = useState<MethodologyType>('CUSTOM');
  const [scoringMode, setScoringMode] = useState<ScoringMode>('WEIGHTED_POINTS');
  const [targetTotalPoints, setTargetTotalPoints] = useState('1000');
  const [error, setError] = useState<string | null>(null);
  const [codeError, setCodeError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async () => {
    setError(null);
    setCodeError(null);
    if (!code.trim()) {
      setCodeError(t('methodology.create.code_required'));
      return;
    }
    const ru = (name['ru-RU'] ?? '').trim();
    if (!ru) {
      setError(t('methodology.metadata.name_primary_required'));
      return;
    }
    const payload: MethodologyCreatePayload = {
      project_id: projectId,
      code: code.trim(),
      name_i18n: name,
      description_i18n: description,
      methodology_type: methodologyType,
      scoring_mode: scoringMode,
      // Container is created empty (from scratch) — never seed from a template.
      source_template_code: null,
    };
    if (scoringMode === 'WEIGHTED_SCALE') {
      const tt = Number.parseFloat(targetTotalPoints);
      if (Number.isNaN(tt) || tt <= 0) {
        setError(t('methodology.create.target_total_invalid'));
        return;
      }
      payload.target_total_points = tt;
    }
    setSubmitting(true);
    try {
      await onSubmit(payload);
    } catch (e) {
      // Surface the backend duplicate-code conflict inline on the code field.
      if (e instanceof ApiError && (e.status === 409 || e.code === 'DUPLICATE_CODE')) {
        setCodeError(t('methodology.create.duplicate_code'));
      } else if (e instanceof ApiError && e.fieldErrors?.code) {
        setCodeError(e.fieldErrors.code);
      } else {
        setError(t('methodology.create.failed'));
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <DrawerForm
      open
      title={t('methodology.create.title')}
      subtitle={t('methodology.create.subtitle')}
      onClose={onClose}
      submitLabel={t('common.create')}
      onSubmit={() => {
        if (!submitting) void handleSubmit();
      }}
    >
      <div className="space-y-4">
        <label className="block text-sm font-medium text-text-primary">
          <span>
            {t('common.code')} <span className="text-danger-700">*</span>
          </span>
          <input
            type="text"
            value={code}
            onChange={(e) => setCode(e.target.value.toUpperCase())}
            placeholder="M-CFO-2026"
            className="mt-1 w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500 font-mono"
            data-testid="create-code"
          />
          {codeError ? (
            <span className="mt-1 block text-xs text-danger-700" role="alert" data-testid="create-code-error">
              {codeError}
            </span>
          ) : null}
        </label>

        <LocalizedNameTabs value={name} onChange={setName} label={t('common.name')} />

        <LocalizedNameTabs
          value={description}
          onChange={setDescription}
          label={t('common.description')}
        />

        <label className="block text-sm font-medium text-text-primary">
          <span>{t('common.type')}</span>
          <select
            value={methodologyType}
            onChange={(e) => setMethodologyType(e.target.value as MethodologyType)}
            className="mt-1 w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500"
            data-testid="create-type"
          >
            {TYPE_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {t(opt.labelKey)}
              </option>
            ))}
          </select>
        </label>

        <label className="block text-sm font-medium text-text-primary">
          <span>{t('methodology.scoring_mode')}</span>
          <select
            value={scoringMode}
            onChange={(e) => setScoringMode(e.target.value as ScoringMode)}
            className="mt-1 w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500"
            data-testid="create-scoring-mode"
          >
            {SCORING_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {t(opt.labelKey)}
              </option>
            ))}
          </select>
        </label>

        {scoringMode === 'WEIGHTED_SCALE' ? (
          <label className="block text-sm font-medium text-text-primary">
            <span>{t('methodology.create.target_total_points')}</span>
            <input
              type="number"
              step="any"
              min="1"
              value={targetTotalPoints}
              onChange={(e) => setTargetTotalPoints(e.target.value)}
              className="mt-1 w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500 tabular-nums"
              data-testid="create-target-total"
            />
          </label>
        ) : null}

        {error ? (
          <p className="text-xs text-danger-700" role="alert" data-testid="create-error">
            {error}
          </p>
        ) : null}
      </div>
    </DrawerForm>
  );
}
