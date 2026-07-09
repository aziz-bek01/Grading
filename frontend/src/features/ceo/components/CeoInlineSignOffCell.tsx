/**
 * CeoInlineSignOffCell — dropdown menu with approve / request-changes / reject
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
 *   - Dropdown pattern: matches the LanguageSwitcher pattern (button +
 *     absolute <ul>, closes on outside-click and Escape key).
 *
 * The component does NOT:
 *   - Duplicate any API call logic.
 *   - Implement new dialogs.
 *   - Have its own mutation for the decide endpoints.
 */
import { useState, useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { ChevronDown } from 'lucide-react';
import { ConfirmDialog } from '@/shared/components/confirm-dialog/ConfirmDialog';
import { ReasonRequiredDialog } from '@/shared/components/confirm-dialog/ReasonRequiredDialog';
import { ApiError } from '@/shared/api/apiError';
import { cn } from '@/shared/lib/cn';
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

/**
 * Dropdown action menu — three decision items: approve, request-changes, reject.
 * Pattern matches LanguageSwitcher: button + absolute <ul>, outside-click and
 * Escape close the menu.
 */
export function CeoInlineSignOffCell({ approvalId, currentStep }: Props) {
  const { t } = useTranslation();

  const [menuOpen, setMenuOpen] = useState(false);
  const [confirmApprove, setConfirmApprove] = useState(false);
  const [reasonDialog, setReasonDialog] = useState<'reject' | 'request-changes' | null>(null);

  const containerRef = useRef<HTMLDivElement>(null);

  // Reuse the three decide mutations verbatim — same hooks used by ApprovalActionsBar.
  const approveMut = useApproveStep(approvalId);
  const rejectMut = useRejectStep(approvalId);
  const requestChangesMut = useRequestChangesStep(approvalId);

  const anyPending =
    approveMut.isPending || rejectMut.isPending || requestChangesMut.isPending;

  // Surface the first error that occurred (matches ApprovalActionsBar pattern).
  const actionError = approveMut.error ?? rejectMut.error ?? requestChangesMut.error;

  // Close menu on outside-click.
  useEffect(() => {
    if (!menuOpen) return;
    const handler = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setMenuOpen(false);
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [menuOpen]);

  // Close menu on Escape key.
  useEffect(() => {
    if (!menuOpen) return;
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setMenuOpen(false);
    };
    document.addEventListener('keydown', handler);
    return () => document.removeEventListener('keydown', handler);
  }, [menuOpen]);

  return (
    <div className="flex flex-col gap-1">
      {/* Dropdown trigger */}
      <div ref={containerRef} className="relative inline-block">
        <button
          type="button"
          disabled={anyPending}
          aria-haspopup="menu"
          aria-expanded={menuOpen}
          onClick={() => setMenuOpen((v) => !v)}
          data-testid="ceo-actions-menu-trigger"
          className={cn(
            'inline-flex items-center gap-1.5 h-8 px-3 text-sm font-medium rounded-md',
            'border border-border-strong bg-surface text-text-primary',
            'hover:bg-divider disabled:opacity-60 disabled:cursor-not-allowed',
          )}
        >
          {t('ceo.panels.actions_menu')}
          <ChevronDown
            size={14}
            aria-hidden
            className={cn('shrink-0 transition-transform', menuOpen && 'rotate-180')}
          />
        </button>

        {menuOpen && !anyPending ? (
          <ul
            role="menu"
            data-testid="ceo-actions-menu"
            className="absolute left-0 mt-1 z-30 min-w-[11rem] bg-surface border border-border rounded-lg shadow-md py-1"
          >
            {/* Approve */}
            <li role="none">
              <button
                type="button"
                role="menuitem"
                data-testid="ceo-inline-approve"
                onClick={() => {
                  setMenuOpen(false);
                  setConfirmApprove(true);
                }}
                className="w-full flex items-center px-3 py-2 text-sm text-text-primary hover:bg-divider text-left"
              >
                {t('ceo.panels.action_approve')}
              </button>
            </li>

            {/* Request changes */}
            <li role="none">
              <button
                type="button"
                role="menuitem"
                data-testid="ceo-inline-request-changes"
                onClick={() => {
                  setMenuOpen(false);
                  setReasonDialog('request-changes');
                }}
                className="w-full flex items-center px-3 py-2 text-sm text-text-primary hover:bg-divider text-left"
              >
                {t('ceo.panels.action_request_changes')}
              </button>
            </li>

            {/* Reject */}
            <li role="none">
              <button
                type="button"
                role="menuitem"
                data-testid="ceo-inline-reject"
                onClick={() => {
                  setMenuOpen(false);
                  setReasonDialog('reject');
                }}
                className="w-full flex items-center px-3 py-2 text-sm text-danger-600 hover:bg-divider text-left"
              >
                {t('ceo.panels.action_reject')}
              </button>
            </li>
          </ul>
        ) : null}
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

      {/*
        Reject — reason required (default 20-char minimum kept).
        DESTRUCTIVE: rejecting an EVALUATION_PANEL sign-off resets the panel to
        COLLECTING and permanently deletes every evaluator's scores (deliberate,
        twice-confirmed product decision — see QA-005). This cell is used ONLY
        for panel sign-off rows, so the stronger `reject_dialog_body_destructive`
        copy always applies here — no entityType branch needed. The generic
        `approval.actions.reject_dialog_body` stays untouched for non-panel
        (non-destructive) rejections used by ApprovalActionsBar.
      */}
      <ReasonRequiredDialog
        open={reasonDialog === 'reject'}
        title={t('approval.actions.reject_dialog_title')}
        body={t('ceo.panels.reject_dialog_body_destructive')}
        onCancel={() => setReasonDialog(null)}
        onConfirm={(reason) => {
          rejectMut.mutate({ stepId: currentStep.id, reason });
          setReasonDialog(null);
        }}
      />
    </div>
  );
}
