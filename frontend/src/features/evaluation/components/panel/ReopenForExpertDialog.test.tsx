import type { ComponentProps } from 'react';
import { describe, it, expect, vi, afterEach } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { renderWithProviders, signOut } from '@/test/testUtils';
import { ApiError } from '@/shared/api/apiError';
import { ReopenForExpertDialog } from './ReopenForExpertDialog';
import { describeReopenForExpertError } from './reopenForExpertError';

// EvaluatorPicker is data-driven from useAllUsers — stub a small ACTIVE
// roster so the manager can pick an expert.
vi.mock('@/features/users-access/hooks/useUsers', () => ({
  useAllUsers: () => ({
    data: {
      items: [
        { id: 'user-x', full_name: 'Expert X', status: 'ACTIVE' },
        { id: 'user-y', full_name: 'Expert Y', status: 'ACTIVE' },
      ],
      totalElements: 2,
      truncated: false,
    },
    isLoading: false,
    isError: false,
  }),
}));

function renderDialog(props: Partial<ComponentProps<typeof ReopenForExpertDialog>> = {}) {
  const onConfirm = vi.fn();
  const onCancel = vi.fn();
  render(
    renderWithProviders(
      <ReopenForExpertDialog
        open
        busy={false}
        error={null}
        onConfirm={onConfirm}
        onCancel={onCancel}
        {...props}
      />,
    ),
  );
  return { onConfirm, onCancel };
}

describe('<ReopenForExpertDialog /> (Feature 2)', () => {
  afterEach(() => signOut());

  it('renders the consequence warning (approved result reopened, re-approval needed)', () => {
    renderDialog();
    expect(screen.getByTestId('reopen-for-expert-consequence')).toBeInTheDocument();
  });

  it('blocks submit until an evaluator and a valid reason (>= 5) are present', () => {
    const { onConfirm } = renderDialog();
    // Confirm is disabled with no selection / reason.
    const confirm = screen.getByTestId('reopen-for-expert-confirm');
    fireEvent.click(confirm);
    expect(onConfirm).not.toHaveBeenCalled();

    // Pick an evaluator but give a too-short reason → still blocked + error shown.
    fireEvent.change(screen.getByTestId('reopen-expert-user'), {
      target: { value: 'user-x' },
    });
    fireEvent.change(screen.getByTestId('reopen-expert-reason'), {
      target: { value: 'no' },
    });
    fireEvent.click(confirm);
    expect(onConfirm).not.toHaveBeenCalled();
    expect(screen.getByTestId('reopen-expert-reason-error')).toBeInTheDocument();
  });

  it('submits with the user id + trimmed reason once valid', () => {
    const { onConfirm } = renderDialog();
    fireEvent.change(screen.getByTestId('reopen-expert-user'), {
      target: { value: 'user-y' },
    });
    fireEvent.change(screen.getByTestId('reopen-expert-reason'), {
      target: { value: '  needs a second opinion  ' },
    });
    fireEvent.click(screen.getByTestId('reopen-for-expert-confirm'));
    expect(onConfirm).toHaveBeenCalledWith('user-y', 'needs a second opinion');
  });

  it('renders a mapped error banner when given an error string', () => {
    renderDialog({ error: 'Boom' });
    expect(screen.getByTestId('reopen-for-expert-error')).toHaveTextContent('Boom');
  });
});

describe('describeReopenForExpertError', () => {
  const t = (key: string) => key;

  it('maps PANEL_NOT_APPROVED_FOR_REOPEN to its dedicated message key', () => {
    const err = new ApiError(400, {
      code: 'PANEL_NOT_APPROVED_FOR_REOPEN',
      message: 'x',
    });
    expect(describeReopenForExpertError(err, t)).toBe(
      'panel.reopen_expert.error_not_approved',
    );
  });

  it('maps 403 / 404 / generic validation distinctly', () => {
    expect(
      describeReopenForExpertError(new ApiError(403, { code: 'ACCESS_DENIED' }), t),
    ).toBe('panel.reopen_expert.error_forbidden');
    expect(
      describeReopenForExpertError(new ApiError(404, { code: 'NOT_FOUND' }), t),
    ).toBe('panel.reopen_expert.error_not_found');
    expect(
      describeReopenForExpertError(new ApiError(400, { code: 'VALIDATION_FAILED' }), t),
    ).toBe('panel.reopen_expert.error_validation');
  });
});
