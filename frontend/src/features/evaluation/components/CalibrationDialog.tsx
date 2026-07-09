import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from '@/shared/components/ui/Button';
import { Modal } from '@/shared/components/ui/Modal';
import { pickLocalized } from '@/shared/lib/localized';
import type { Factor } from '@/features/methodology/types';
import {
  CalibrateScoreSchema,
  type CalibrateScoreInput,
} from '../schemas/evaluationSchemas';

interface Props {
  open: boolean;
  factors: Factor[];
  onConfirm: (input: CalibrateScoreInput) => void;
  onCancel: () => void;
}

/**
 * Manual calibration dialog (architecture §15 "manual calibration must
 * require a reason/comment"). Opens for APPROVED / LOCKED evaluations
 * only (parent gates the trigger).
 *
 * Validation mirrors backend `CalibrateScoreRequest`:
 *   - factorId required
 *   - newRawFactorScore ≥ 0
 *   - reason length ≥ 20 chars
 *
 * Audited server-side as SCORE_CALIBRATED.
 */
export function CalibrationDialog({ open, ...rest }: Props) {
  // Mount fresh while open so state initializes from props (no reset effect).
  if (!open) return null;
  return <CalibrationDialogBody {...rest} />;
}

function CalibrationDialogBody({ factors, onConfirm, onCancel }: Omit<Props, 'open'>) {
  const { t, i18n } = useTranslation();
  const [factorId, setFactorId] = useState(factors[0]?.id ?? '');
  const [score, setScore] = useState('');
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = () => {
    const parsed = CalibrateScoreSchema.safeParse({
      factor_id: factorId,
      new_raw_factor_score: Number(score),
      reason,
    });
    if (!parsed.success) {
      const first = parsed.error.issues[0];
      setError(first?.message ?? 'validation_failed');
      return;
    }
    onConfirm(parsed.data);
  };

  const reasonValid = reason.trim().length >= 20;
  const scoreNum = Number(score);
  const scoreValid =
    score !== '' && !Number.isNaN(scoreNum) && scoreNum >= 0;
  const valid = !!factorId && scoreValid && reasonValid;

  return (
    <Modal
      open
      onClose={onCancel}
      labelledBy="calibration-dialog-title"
      size="lg"
      className="bg-surface rounded-xl shadow-lg border border-border p-6 space-y-4"
    >
      <>
        <h2
          id="calibration-dialog-title"
          className="text-lg text-text-primary"
          data-testid="calibration-dialog-title"
        >
          {t('evaluation.calibration.dialog_title')}
        </h2>
        <p className="text-sm text-text-secondary">
          {t('evaluation.calibration.dialog_body')}
        </p>

        <label className="block text-sm font-medium text-text-primary">
          {t('evaluation.calibration.factor_label')}
        </label>
        <select
          value={factorId}
          onChange={(e) => setFactorId(e.target.value)}
          data-testid="calibration-factor-select"
          className="w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface"
        >
          {factors.map((f) => (
            <option key={f.id} value={f.id}>
              {f.code} — {pickLocalized(f.name_i18n, i18n.language)}
            </option>
          ))}
        </select>

        <label className="block text-sm font-medium text-text-primary">
          {t('evaluation.calibration.new_score_label')}
        </label>
        <input
          type="number"
          step="0.0001"
          min="0"
          value={score}
          onChange={(e) => setScore(e.target.value)}
          data-testid="calibration-score-input"
          className="w-full h-10 px-3 border border-border-strong rounded-md text-sm"
        />

        <label className="block text-sm font-medium text-text-primary">
          {t('common.reason_label')}
        </label>
        <textarea
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          data-testid="calibration-reason-input"
          className="w-full min-h-[96px] rounded-md border border-border-strong p-3 text-sm focus:border-primary-500 focus:outline-none"
        />
        <p className="text-xs text-text-muted">{t('common.reason_required')}</p>

        {error ? (
          <p className="text-xs text-danger-600" data-testid="calibration-error">
            {error}
          </p>
        ) : null}

        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" onClick={onCancel}>
            {t('common.cancel')}
          </Button>
          <Button
            onClick={handleSubmit}
            disabled={!valid}
            data-testid="calibration-confirm"
          >
            {t('common.confirm')}
          </Button>
        </div>
      </>
    </Modal>
  );
}
