import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from '@/shared/components/ui/Button';
import { ConfirmDialog } from '@/shared/components/confirm-dialog/ConfirmDialog';
import { ReasonRequiredDialog } from '@/shared/components/confirm-dialog/ReasonRequiredDialog';
import { PermissionGate } from '@/shared/components/access/PermissionGate';
import { PERMISSIONS } from '@/shared/types/permissions';
import {
  LockEvaluationDialog,
  type LockSummary,
} from './LockEvaluationDialog';
import type { EvaluationStatus } from '../types';

interface Props {
  status: EvaluationStatus;
  /** Disable Submit unless evaluation completeness check passes. */
  canSubmit: boolean;
  onSubmit?: () => void;
  onApprove?: () => void;
  onRequestChanges?: (reason: string) => void;
  onLock?: () => void;
  /** Fired when the pre-lock review dialog is cancelled (clears any lock error). */
  onLockCancel?: () => void;
  onArchive?: (reason: string) => void;
  onCalibrate?: () => void;
  /**
   * Summary shown in the pre-lock review step — sourced from data already on
   * the page (no extra backend call). Omit when no summary is available.
   */
  lockSummary?: LockSummary;
  /** True while the lock mutation is in flight (keeps the review dialog open). */
  lockBusy?: boolean;
  /** Localized error from a failed lock; keeps the dialog open + sheet editable. */
  lockError?: string | null;
}

/**
 * Status-aware buttons (PRD MVP1-E9-3 state machine):
 *   DRAFT / INCOMPLETE  -> [Submit (disabled if !canSubmit)]
 *   COMPLETE            -> [Submit]
 *   SUBMITTED           -> [Approve, Request changes]
 *   APPROVED            -> [Lock, Calibrate]
 *   LOCKED              -> [Calibrate, Archive]
 *   ARCHIVED            -> []
 *
 * Approve/Lock are confirm-dialog gated; Request changes / Archive
 * require a reason ≥ 20 chars to match backend validation.
 */
export function EvaluationActionsBar({
  status,
  canSubmit,
  onSubmit,
  onApprove,
  onRequestChanges,
  onLock,
  onLockCancel,
  onArchive,
  onCalibrate,
  lockSummary,
  lockBusy = false,
  lockError = null,
}: Props) {
  const { t } = useTranslation();
  const [confirmApprove, setConfirmApprove] = useState(false);
  const [confirmLock, setConfirmLock] = useState(false);
  const [reasonReturn, setReasonReturn] = useState(false);
  const [reasonArchive, setReasonArchive] = useState(false);

  return (
    <div
      className="flex flex-wrap items-center gap-2"
      data-testid="evaluation-actions"
    >
      {(status === 'DRAFT' ||
        status === 'INCOMPLETE' ||
        status === 'COMPLETE') && (
        <PermissionGate permission={PERMISSIONS.EVALUATION_EDIT}>
          <Button
            variant="primary"
            disabled={!canSubmit}
            onClick={onSubmit}
            data-testid="action-submit"
          >
            {t('evaluation.actions.submit')}
          </Button>
        </PermissionGate>
      )}

      {status === 'SUBMITTED' && (
        <>
          <PermissionGate permission={PERMISSIONS.EVALUATION_APPROVE}>
            <Button
              variant="primary"
              onClick={() => setConfirmApprove(true)}
              data-testid="action-approve"
            >
              {t('evaluation.actions.approve')}
            </Button>
            <Button
              variant="secondary"
              onClick={() => setReasonReturn(true)}
              data-testid="action-request-changes"
            >
              {t('evaluation.actions.request_changes')}
            </Button>
          </PermissionGate>
        </>
      )}

      {status === 'APPROVED' && (
        <>
          <PermissionGate permission={PERMISSIONS.EVALUATION_LOCK}>
            <Button
              variant="primary"
              onClick={() => setConfirmLock(true)}
              data-testid="action-lock"
            >
              {t('evaluation.actions.lock')}
            </Button>
          </PermissionGate>
          <PermissionGate permission={PERMISSIONS.CALIBRATION_EDIT}>
            <Button
              variant="secondary"
              onClick={onCalibrate}
              data-testid="action-calibrate"
            >
              {t('evaluation.actions.calibrate')}
            </Button>
          </PermissionGate>
        </>
      )}

      {status === 'LOCKED' && (
        <>
          <PermissionGate permission={PERMISSIONS.CALIBRATION_EDIT}>
            <Button
              variant="secondary"
              onClick={onCalibrate}
              data-testid="action-calibrate"
            >
              {t('evaluation.actions.calibrate')}
            </Button>
          </PermissionGate>
          <PermissionGate permission={PERMISSIONS.EVALUATION_EDIT}>
            <Button
              variant="danger"
              onClick={() => setReasonArchive(true)}
              data-testid="action-archive"
            >
              {t('evaluation.actions.archive')}
            </Button>
          </PermissionGate>
        </>
      )}

      <ConfirmDialog
        open={confirmApprove}
        title={t('evaluation.confirm.approve_title')}
        body={t('evaluation.confirm.approve_body')}
        onConfirm={() => {
          setConfirmApprove(false);
          onApprove?.();
        }}
        onCancel={() => setConfirmApprove(false)}
      />

      {/*
        Lock uses a dedicated REVIEW + CONFIRM dialog (not the generic
        ConfirmDialog): locking is irreversible, so the evaluator must review a
        summary, acknowledge an "I understand this is final" checkbox, and the
        dialog stays open with a spinner while the mutation runs. We do NOT close
        it on confirm — the parent closes it by flipping `status` away from
        APPROVED (the Lock button disappears) once the lock succeeds; on error
        it stays open with the message so the sheet remains editable.
      */}
      <LockEvaluationDialog
        open={confirmLock && status === 'APPROVED'}
        summary={lockSummary}
        busy={lockBusy}
        error={lockError}
        onConfirm={() => onLock?.()}
        onCancel={() => {
          setConfirmLock(false);
          onLockCancel?.();
        }}
      />

      <ReasonRequiredDialog
        open={reasonReturn}
        title={t('evaluation.confirm.request_changes_title')}
        body={t('evaluation.confirm.request_changes_body')}
        onConfirm={(reason) => {
          setReasonReturn(false);
          onRequestChanges?.(reason);
        }}
        onCancel={() => setReasonReturn(false)}
      />

      <ReasonRequiredDialog
        open={reasonArchive}
        title={t('evaluation.confirm.archive_title')}
        body={t('evaluation.confirm.archive_body')}
        onConfirm={(reason) => {
          setReasonArchive(false);
          onArchive?.(reason);
        }}
        onCancel={() => setReasonArchive(false)}
      />
    </div>
  );
}
