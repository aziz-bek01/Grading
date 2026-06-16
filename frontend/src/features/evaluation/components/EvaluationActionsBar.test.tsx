import { describe, expect, it, beforeEach, afterAll, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import i18n from '@/shared/i18n';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import { EvaluationActionsBar } from './EvaluationActionsBar';
import type { EvaluationStatus } from '../types';

function mount(status: EvaluationStatus, canSubmit = true) {
  return render(
    renderWithProviders(
      <EvaluationActionsBar status={status} canSubmit={canSubmit} />,
    ),
  );
}

describe('EvaluationActionsBar — status-aware visibility (super-admin)', () => {
  beforeEach(() => {
    signIn('super-admin');
  });

  it('DRAFT shows Submit', () => {
    mount('DRAFT');
    expect(screen.getByTestId('action-submit')).toBeInTheDocument();
  });

  it('INCOMPLETE shows Submit disabled when !canSubmit', () => {
    mount('INCOMPLETE', false);
    const btn = screen.getByTestId('action-submit') as HTMLButtonElement;
    expect(btn.disabled).toBe(true);
  });

  it('COMPLETE shows Submit enabled', () => {
    mount('COMPLETE');
    const btn = screen.getByTestId('action-submit') as HTMLButtonElement;
    expect(btn.disabled).toBe(false);
  });

  it('SUBMITTED shows Approve and Request changes', () => {
    mount('SUBMITTED');
    expect(screen.getByTestId('action-approve')).toBeInTheDocument();
    expect(screen.getByTestId('action-request-changes')).toBeInTheDocument();
  });

  it('APPROVED shows Lock and Calibrate', () => {
    mount('APPROVED');
    expect(screen.getByTestId('action-lock')).toBeInTheDocument();
    expect(screen.getByTestId('action-calibrate')).toBeInTheDocument();
  });

  it('LOCKED shows Calibrate and Archive', () => {
    mount('LOCKED');
    expect(screen.getByTestId('action-calibrate')).toBeInTheDocument();
    expect(screen.getByTestId('action-archive')).toBeInTheDocument();
  });

  it('ARCHIVED shows no actions', () => {
    mount('ARCHIVED');
    expect(screen.queryByTestId('action-submit')).not.toBeInTheDocument();
    expect(screen.queryByTestId('action-approve')).not.toBeInTheDocument();
    expect(screen.queryByTestId('action-lock')).not.toBeInTheDocument();
    expect(screen.queryByTestId('action-calibrate')).not.toBeInTheDocument();
  });
});

describe('EvaluationActionsBar — permission gating', () => {
  it('viewer cannot see Submit on DRAFT', () => {
    signOut();
    signIn('viewer');
    mount('DRAFT');
    expect(screen.queryByTestId('action-submit')).not.toBeInTheDocument();
  });

  it('viewer cannot see Approve on SUBMITTED', () => {
    signOut();
    signIn('viewer');
    mount('SUBMITTED');
    expect(screen.queryByTestId('action-approve')).not.toBeInTheDocument();
  });

  it('approve opens confirm dialog (super-admin)', () => {
    signOut();
    signIn('super-admin');
    mount('SUBMITTED');
    fireEvent.click(screen.getByTestId('action-approve'));
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });
});

describe('EvaluationActionsBar — pre-lock review + confirm flow', () => {
  beforeEach(async () => {
    signOut();
    signIn('super-admin');
    await i18n.changeLanguage('en-US');
  });

  // Restore the suite-default locale so we never leak en-US into other files.
  afterAll(async () => {
    await i18n.changeLanguage('ru-RU');
  });

  function mountLock(
    props: Partial<React.ComponentProps<typeof EvaluationActionsBar>> = {},
  ) {
    const onLock = vi.fn();
    const onLockCancel = vi.fn();
    render(
      renderWithProviders(
        <EvaluationActionsBar
          status="APPROVED"
          canSubmit
          onLock={onLock}
          onLockCancel={onLockCancel}
          lockSummary={{
            positionName: 'Senior Risk Analyst',
            positionCode: 'POS-001',
            scoredFactors: 8,
            totalFactors: 8,
            displayedTotal: 742.5,
          }}
          {...props}
        />,
      ),
    );
    return { onLock, onLockCancel };
  }

  it('clicking Lock opens the review dialog and does NOT lock yet', () => {
    const { onLock } = mountLock();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    fireEvent.click(screen.getByTestId('action-lock'));
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    // Review step shows the irreversibility warning + summary.
    expect(screen.getByTestId('lock-irreversible-warning')).toBeInTheDocument();
    expect(screen.getByTestId('lock-summary')).toHaveTextContent('Senior Risk Analyst');
    // Mutation must NOT have fired on mere intent.
    expect(onLock).not.toHaveBeenCalled();
  });

  it('does not lock until the affirmative checkbox + confirm', () => {
    const { onLock } = mountLock();
    fireEvent.click(screen.getByTestId('action-lock'));
    // Confirm is gated by the "I understand" checkbox.
    fireEvent.click(screen.getByTestId('lock-confirm'));
    expect(onLock).not.toHaveBeenCalled();
    fireEvent.click(screen.getByTestId('lock-understand-checkbox'));
    fireEvent.click(screen.getByTestId('lock-confirm'));
    expect(onLock).toHaveBeenCalledTimes(1);
  });

  it('cancel leaves the sheet editable and fires no lock', () => {
    const { onLock, onLockCancel } = mountLock();
    fireEvent.click(screen.getByTestId('action-lock'));
    fireEvent.click(screen.getByTestId('lock-cancel'));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(onLock).not.toHaveBeenCalled();
    expect(onLockCancel).toHaveBeenCalledTimes(1);
  });

  it('ESC cancels without firing the lock mutation', () => {
    const { onLock } = mountLock();
    fireEvent.click(screen.getByTestId('action-lock'));
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(onLock).not.toHaveBeenCalled();
  });

  it('an error keeps the dialog open with a message (sheet still editable)', () => {
    mountLock({ lockError: 'Server rejected the lock' });
    fireEvent.click(screen.getByTestId('action-lock'));
    expect(screen.getByTestId('lock-error')).toHaveTextContent(
      'Server rejected the lock',
    );
    // Status is still APPROVED -> matrix/actions remain editable upstream.
    expect(screen.getByTestId('action-lock')).toBeInTheDocument();
  });
});
