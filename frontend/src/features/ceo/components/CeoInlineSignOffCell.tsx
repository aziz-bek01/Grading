/**
 * CeoInlineSignOffCell — inline approve / request-changes / reject buttons
 * for a single CEO sign-off step shown in the CeoPanelsPage table.
 *
 * REUSE CONTRACT (nothing is reimplemented):
 *   - Mutations:  useApproveStep / useRequestChangesStep / useRejectStep from
 *     `@/features/approval/hooks/useApprovals` — the SAME hooks used by
 *     ApprovalActionsBar on the detail page.
 *   - Dialogs:    ConfirmDialog (approve) + ReasonRequiredDialog (reject /
 *     request-changes) — the SAME shared dialog components.
 *   - Error display: mirrors the ActionErrorAlert pattern from ApprovalActionsBar
 *     (ApiError code → specific i18n key, else generic fallback).
 *
 * The component does NOT:
 *   - Duplicate any API call logic.
 *   - Implement new dialogs.
 *   - Have its own mutation for the decide endpoints.
 */
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from '@/shared/components/ui/Button';
import { ConfirmDialog } from '@/shared/components/confirm-dialog/ConfirmDialog';
import { ReasonRequiredDialog } from '@/shared/components/confirm-dialog/ReasonRequiredDialog';
import { ApiError } from '@/shared/api/apiError';
import {
  useApproveStep,
  useRejectStep,
  useRequestChangesStep,
} from '@/features/approval/hooks/useApprovals';
import type { ApprovalStep } from '@/features/approval/types';

/** Known backend rejection codes mapped to specific localized messages. */
const ACTION_ERROR_KEYS: Record<string, string> = {
  REASON_REQUIRED: 'approval.actions.error_reason_required',
  INVALID_TRANSITION: 'approval.actions.error_invalid_transition',
};

interface Props {
  approvalId: string;
  currentStep: ApprovalStep;
}

/**
 * Inline error strip — mirrors the ActionErrorAlert pattern from ApprovalActionsBar
 * (~lines 35–48): a BE 400/409 is surfaced inline while the table stays interactive.
 */
function InlineErrorAlert({ error }: { error: unknown }) {
  const { t } = useTranslation();
  if (!error) return null;
  const key =
    error instanceof ApiError ? ACTION_ERROR_KEYS[error.code] : undefined;
  return (
    <p
      role="alert"
      data-testid="ceo-inline-action-error"
      className="w-full text-xs text-danger-700 mt-1"
    >
      {key ? t(key) : t('approval.actions.action_failed')}
    </p>
  );
}

export function CeoInlineSignOffCell({ approvalId, currentStep }: Props) {
  const { t } = useTranslation();

  const [confirmApprove, setConfirmApprove] = useState(false);
  const [reasonDialog, setReasonDialog] = useState<'reject' | 'request-changes' | null>(null);

  // Reuse the three decide mutations verbatim — same hooks used by ApprovalActionsBar.
  const approveMut = useApproveStep(approvalId);
  const rejectMut = useRejectStep(approvalId);
  const requestChangesMut = useRequestChangesStep(approvalId);

  const anyPending =
    approveMut.isPending || rejectMut.isPending || requestChangesMut.isPending;

  // Surface the first error that occurred (matches ApprovalActionsBar pattern).
  const actionError = approveMut.error ?? rejectMut.error ?? requestChangesMut.error;

  return (
    <div className="flex flex-col gap-1">
      <div className="flex flex-wrap gap-1">
        {/* Primary: Tasdiqlayman */}
        <Button
          size="sm"
          variant="primary"
          disabled={anyPending}
          onClick={() => setConfirmApprove(true)}
          data-testid="ceo-inline-approve"
        >
          {t('ceo.panels.action_approve')}
        </Button>

        {/* Secondary: Qayta ko'rib chiqilsin */}
        <Button
          size="sm"
          variant="secondary"
          disabled={anyPending}
          onClick={() => setReasonDialog('request-changes')}
          data-testid="ceo-inline-request-changes"
        >
          {t('ceo.panels.action_request_changes')}
        </Button>

        {/* Danger: Bekor qilinsin */}
        <Button
          size="sm"
          variant="danger"
          disabled={anyPending}
          onClick={() => setReasonDialog('reject')}
          data-testid="ceo-inline-reject"
        >
          {t('ceo.panels.action_reject')}
        </Button>
      </div>

      <InlineErrorAlert error={actionError} />

      {/* Approve confirmation — no reason required (same as ApprovalActionsBar). */}
      <ConfirmDialog
        open={confirmApprove}
        title={t('approval.actions.approve_confirm_title')}
        body={t('approval.actions.approve_confirm_body')}
        confirmLabel={t('ceo.panels.action_approve')}
        busy={approveMut.isPending}
        onCancel={() => setConfirmApprove(false)}
        onConfirm={() => {
          approveMut.mutate({ stepId: currentStep.id });
          setConfirmApprove(false);
        }}
      />

      {/* Request-changes — reason required (default 20-char minimum kept). */}
      <ReasonRequiredDialog
        open={reasonDialog === 'request-changes'}
        title={t('approval.actions.changes_dialog_title')}
        body={t('approval.actions.changes_dialog_body')}
        onCancel={() => setReasonDialog(null)}
        onConfirm={(reason) => {
          requestChangesMut.mutate({ stepId: currentStep.id, reason });
          setReasonDialog(null);
        }}
      />

      {/* Reject — reason required (default 20-char minimum kept). */}
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
    </div>
  );
}
