import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from '@/shared/components/ui/Button';
import { ReasonRequiredDialog } from '@/shared/components/confirm-dialog/ReasonRequiredDialog';
import { ConfirmDialog } from '@/shared/components/confirm-dialog/ConfirmDialog';
import { ApiError } from '@/shared/api/apiError';
import { useAuthStore } from '@/features/auth/authStore';
import {
  useApproveStep,
  useCancelApprovalRequest,
  useRejectStep,
  useRequestChangesStep,
} from '../hooks/useApprovals';
import type { ApprovalRequest, ApprovalStep } from '../types';
import type { PermissionCode } from '@/shared/types/permissions';
import { PERMISSIONS } from '@/shared/types/permissions';

interface Props {
  request: ApprovalRequest;
  currentStep: ApprovalStep | null;
}

/** Known backend rejection codes mapped to specific localized messages. */
const ACTION_ERROR_KEYS: Record<string, string> = {
  REASON_REQUIRED: 'approval.actions.error_reason_required',
  INVALID_TRANSITION: 'approval.actions.error_invalid_transition',
};

/**
 * Inline error strip for approve/reject/request-changes/cancel mutation
 * failures (AC8): a BE 400/409 must be SHOWN to the user, never silently
 * swallowed — while the page itself stays rendered (no global error state).
 * One component, reused by both render branches of the actions bar.
 */
function ActionErrorAlert({ error }: { error: unknown }) {
  const { t } = useTranslation();
  if (!error) return null;
  const key =
    error instanceof ApiError ? ACTION_ERROR_KEYS[error.code] : undefined;
  return (
    <p
      role="alert"
      data-testid="approval-action-error"
      className="w-full text-xs text-danger-700"
    >
      {key ? t(key) : t('approval.actions.action_failed')}
    </p>
  );
}

/**
 * Actions bar shown next to the current pending step.
 *
 * Authority rule (mirrors backend):
 *   The acting user MUST either
 *   - be the explicit `approverUserId` for the step, OR
 *   - hold the step's `requiredPermission`.
 *
 * Backend remains the source of truth — UI just hides actions the user
 * cannot perform.
 */
export function ApprovalActionsBar({ request, currentStep }: Props) {
  const { t } = useTranslation();
  const user = useAuthStore((s) => s.user);
  const [reasonDialog, setReasonDialog] = useState<'reject' | 'changes' | null>(null);
  const [confirmApprove, setConfirmApprove] = useState(false);
  const [confirmCancel, setConfirmCancel] = useState(false);

  const approveMut = useApproveStep(request.id);
  const rejectMut = useRejectStep(request.id);
  const requestChangesMut = useRequestChangesStep(request.id);
  const cancelMut = useCancelApprovalRequest(request.id);

  if (!user) return null;

  const canCancel =
    user.permissions.includes(PERMISSIONS.APPROVAL_REQUEST_CANCEL) &&
    request.status === 'PENDING' &&
    request.initiatedByUserId === user.id;

  // First failed mutation (if any) — surfaced inline below the buttons.
  const actionError =
    approveMut.error ?? rejectMut.error ?? requestChangesMut.error ?? cancelMut.error;

  if (!currentStep || request.status !== 'PENDING') {
    return canCancel ? (
      <div className="flex flex-wrap gap-2">
        <Button variant="secondary" onClick={() => setConfirmCancel(true)}>
          {t('approval.actions.cancel_request')}
        </Button>
        <ActionErrorAlert error={cancelMut.error} />
        <ConfirmDialogWrapper
          open={confirmCancel}
          onCancel={() => setConfirmCancel(false)}
          onConfirm={() => {
            cancelMut.mutate();
            setConfirmCancel(false);
          }}
        />
      </div>
    ) : null;
  }

  const userIsExplicitApprover =
    currentStep.approverUserId !== null &&
    currentStep.approverUserId !== undefined &&
    currentStep.approverUserId === user.id;
  const userHasPermission =
    currentStep.requiredPermission !== null &&
    currentStep.requiredPermission !== undefined &&
    user.permissions.includes(currentStep.requiredPermission as PermissionCode);
  const canAct = userIsExplicitApprover || userHasPermission;

  const canApprove = canAct && user.permissions.includes(PERMISSIONS.APPROVAL_STEP_APPROVE);
  const canReject = canAct && user.permissions.includes(PERMISSIONS.APPROVAL_STEP_REJECT);

  if (!canAct) {
    return (
      <div className="text-sm text-text-muted" data-testid="approval-no-authority">
        {t('approval.actions.no_authority')}
      </div>
    );
  }

  return (
    <div
      className="flex flex-wrap items-center gap-2"
      data-testid="approval-actions-bar"
    >
      {canApprove ? (
        <Button onClick={() => setConfirmApprove(true)} data-testid="approval-approve-button">
          {t('approval.actions.approve')}
        </Button>
      ) : null}
      {canReject ? (
        <Button
          variant="danger"
          onClick={() => setReasonDialog('reject')}
          data-testid="approval-reject-button"
        >
          {t('approval.actions.reject')}
        </Button>
      ) : null}
      <Button
        variant="secondary"
        onClick={() => setReasonDialog('changes')}
        data-testid="approval-request-changes-button"
      >
        {t('approval.actions.request_changes')}
      </Button>
      {canCancel ? (
        <Button variant="ghost" onClick={() => setConfirmCancel(true)}>
          {t('approval.actions.cancel_request')}
        </Button>
      ) : null}

      <ActionErrorAlert error={actionError} />

      <ConfirmDialog
        open={confirmApprove}
        title={t('approval.actions.approve_confirm_title')}
        body={t('approval.actions.approve_confirm_body')}
        confirmLabel={t('approval.actions.approve')}
        onCancel={() => setConfirmApprove(false)}
        onConfirm={() => {
          approveMut.mutate({ stepId: currentStep.id });
          setConfirmApprove(false);
        }}
      />

      <ReasonRequiredDialog
        open={reasonDialog === 'reject'}
        title={t('approval.actions.reject_dialog_title')}
        body={t('approval.actions.reject_dialog_body')}
        onCancel={() => setReasonDialog(null)}
        onConfirm={(reason) => {
          rejectMut.mutate({ stepId: currentStep.id, reason });
          setReasonDialog(null);
        }}
      />

      <ReasonRequiredDialog
        open={reasonDialog === 'changes'}
        title={t('approval.actions.changes_dialog_title')}
        body={t('approval.actions.changes_dialog_body')}
        onCancel={() => setReasonDialog(null)}
        onConfirm={(reason) => {
          requestChangesMut.mutate({ stepId: currentStep.id, reason });
          setReasonDialog(null);
        }}
      />

      <ConfirmDialogWrapper
        open={confirmCancel}
        onCancel={() => setConfirmCancel(false)}
        onConfirm={() => {
          cancelMut.mutate();
          setConfirmCancel(false);
        }}
      />
    </div>
  );
}

function ConfirmDialogWrapper({
  open,
  onCancel,
  onConfirm,
}: {
  open: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  const { t } = useTranslation();
  return (
    <ConfirmDialog
      open={open}
      title={t('approval.actions.cancel_confirm_title')}
      body={t('approval.actions.cancel_confirm_body')}
      confirmLabel={t('approval.actions.cancel_request')}
      onCancel={onCancel}
      onConfirm={onConfirm}
    />
  );
}
