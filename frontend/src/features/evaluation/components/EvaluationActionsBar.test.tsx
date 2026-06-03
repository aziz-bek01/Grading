import { describe, expect, it, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
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
