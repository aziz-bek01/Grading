import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders, signIn } from '@/test/testUtils';
import { ApprovalActionsBar } from '../components/ApprovalActionsBar';
import type { ApprovalRequest, ApprovalStep } from '../types';
import { useAuthStore } from '@/features/auth/authStore';
import { ApiError } from '@/shared/api/apiError';

// Controllable mutation state so the AC8 inline-error path is testable
// without a network round-trip. `mutate` stays a no-op for the other tests.
const mut = vi.hoisted(() => ({ approveError: null as unknown }));
vi.mock('../hooks/useApprovals', () => ({
  useApproveStep: () => ({ mutate: vi.fn(), error: mut.approveError }),
  useRejectStep: () => ({ mutate: vi.fn(), error: null }),
  useRequestChangesStep: () => ({ mutate: vi.fn(), error: null }),
  useCancelApprovalRequest: () => ({ mutate: vi.fn(), error: null }),
}));

/** Resolve the currently signed-in user's id (set by `signIn` in beforeEach). */
function meId(): string {
  return useAuthStore.getState().user?.id ?? '';
}

function buildRequest(overrides: Partial<ApprovalRequest> = {}, step?: Partial<ApprovalStep>): ApprovalRequest {
  return {
    id: 'a-1',
    projectId: 'p-1',
    entityType: 'JOB_PROFILE',
    entityId: 'jp-1',
    status: 'PENDING',
    initiatedByUserId: 'somebody-else',
    initiatedAt: '2026-05-01T00:00:00Z',
    currentStepOrder: 1,
    totalSteps: 1,
    steps: [
      {
        id: 'step-1',
        approvalRequestId: 'a-1',
        stepOrder: 1,
        approverUserId: null,
        requiredPermission: 'JOB_PROFILE_APPROVE',
        status: 'PENDING',
        ...step,
      } as ApprovalStep,
    ],
    decisions: [],
    ...overrides,
  };
}

describe('<ApprovalActionsBar />', () => {
  beforeEach(() => {
    signIn('super-admin');
    mut.approveError = null;
  });

  it('shows approve / reject / request-changes when user has permission', () => {
    const req = buildRequest();
    render(renderWithProviders(<ApprovalActionsBar request={req} currentStep={req.steps[0]} />));
    expect(screen.getByTestId('approval-actions-bar')).toBeInTheDocument();
    expect(screen.getByTestId('approval-approve-button')).toBeInTheDocument();
    expect(screen.getByTestId('approval-reject-button')).toBeInTheDocument();
    expect(screen.getByTestId('approval-request-changes-button')).toBeInTheDocument();
  });

  it('hides actions for a user without authority on the step', () => {
    // Strip permissions so neither approverUserId nor requiredPermission matches.
    useAuthStore.setState((s) =>
      s.user
        ? { user: { ...s.user, permissions: [], id: 'someone-else' } }
        : {},
    );
    const req = buildRequest();
    render(renderWithProviders(<ApprovalActionsBar request={req} currentStep={req.steps[0]} />));
    expect(screen.getByTestId('approval-no-authority')).toBeInTheDocument();
    expect(screen.queryByTestId('approval-approve-button')).not.toBeInTheDocument();
  });

  it('reject button requires reason of at least 20 chars', async () => {
    const req = buildRequest();
    render(renderWithProviders(<ApprovalActionsBar request={req} currentStep={req.steps[0]} />));
    const user = userEvent.setup();
    await user.click(screen.getByTestId('approval-reject-button'));
    const dialog = await screen.findByRole('dialog');
    const confirmBtn = dialog.querySelectorAll('button')[1] as HTMLButtonElement;
    // No reason yet → confirm disabled
    expect(confirmBtn).toBeDisabled();
    const textarea = dialog.querySelector('textarea')!;
    await user.type(textarea, 'short');
    expect(confirmBtn).toBeDisabled();
    await user.type(textarea, ' but now reason is more than twenty characters long');
    expect(confirmBtn).not.toBeDisabled();
  });

  it('the explicit approver gains authority even without the required permission match', () => {
    const req = buildRequest({}, {
      approverUserId: meId(),
      requiredPermission: 'SOME_OTHER_PERMISSION',
    });
    render(renderWithProviders(<ApprovalActionsBar request={req} currentStep={req.steps[0]} />));
    expect(screen.getByTestId('approval-actions-bar')).toBeInTheDocument();
  });

  it('AC8: a failed mutation surfaces an inline alert — page stays rendered', () => {
    mut.approveError = new ApiError(409, {
      code: 'INVALID_TRANSITION',
      message: 'already decided',
    });
    const req = buildRequest();
    render(renderWithProviders(<ApprovalActionsBar request={req} currentStep={req.steps[0]} />));
    // The bar itself still renders (no global error state)...
    expect(screen.getByTestId('approval-actions-bar')).toBeInTheDocument();
    // ...and the failure is SHOWN to the user, not silently swallowed.
    const alert = screen.getByTestId('approval-action-error');
    expect(alert).toHaveAttribute('role', 'alert');
    expect(alert.textContent ?? '').not.toHaveLength(0);
  });

  it('AC8: no alert is rendered when no mutation has failed', () => {
    const req = buildRequest();
    render(renderWithProviders(<ApprovalActionsBar request={req} currentStep={req.steps[0]} />));
    expect(screen.queryByTestId('approval-action-error')).not.toBeInTheDocument();
  });
});
