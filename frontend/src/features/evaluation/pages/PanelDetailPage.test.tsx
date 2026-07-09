import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { Route, Routes } from 'react-router-dom';
import {
  renderWithProviders,
  signInWithPermissions,
  signOut,
} from '@/test/testUtils';
import { PERMISSIONS } from '@/shared/types/permissions';
import { ApiError } from '@/shared/api/apiError';
import { PanelDetailPage } from './PanelDetailPage';
import type { PanelDetail, PanelStatus } from '../panelTypes';

// The detail page composes the heavy reused panel components (each of which does
// its own gated fetch); stub them so the test focuses on the management-action
// gating + the reason-required confirm wiring.
vi.mock('../components/panel/PanelProgressView', () => ({
  PanelProgressView: () => <div data-testid="stub-progress" />,
}));
vi.mock('../components/panel/PanelAveragedResult', () => ({
  PanelAveragedResult: () => <div data-testid="stub-averaged" />,
}));
vi.mock('../components/panel/PanelApprovalSummary', () => ({
  PanelApprovalSummary: () => <div data-testid="stub-summary" />,
}));

// ---------- Hook mocks ----------
const detailState = { status: 'COLLECTING' as PanelStatus };

const deletePanelSpy = vi.fn().mockResolvedValue(undefined);
const archivePanelSpy = vi.fn().mockResolvedValue({});
const reopenPanelSpy = vi.fn().mockResolvedValue({});
const reopenForExpertSpy = vi.fn().mockResolvedValue({});

// Mutable mutation-error state so a test can drive the "server rejected → the
// FE must SHOW it" path (the prod delete-does-nothing regression guard).
const deleteState = { isError: false, error: null as unknown };
const reopenState = { isError: false, error: null as unknown };
const reopenExpertState = { isError: false, error: null as unknown };

function makeDetail(status: PanelStatus): PanelDetail {
  return {
    panel: {
      id: 'panel-1',
      project_id: 'proj-1',
      position_id: 'pos-1',
      position_title_i18n: { 'en-US': 'Chief Risk Officer' },
      methodology_version_id: 'v-1',
      status,
      min_evaluators: 3,
      evaluator_count: 3,
      completed_count: 0,
      created_at: '2026-05-01T00:00:00Z',
    },
    roster: [],
    completed_count: 0,
    total_count: 0,
  };
}

vi.mock('../hooks/usePanels', () => ({
  usePanelDetail: (id: string | undefined) => ({
    data: id ? makeDetail(detailState.status) : undefined,
    isLoading: false,
    error: null,
    refetch: vi.fn(),
  }),
  useDeletePanel: () => ({
    mutateAsync: deletePanelSpy,
    isPending: false,
    isError: deleteState.isError,
    error: deleteState.error,
    reset: vi.fn(),
  }),
  useArchivePanel: () => ({
    mutateAsync: archivePanelSpy,
    isPending: false,
    isError: false,
    error: null,
    reset: vi.fn(),
  }),
  useReopenPanel: () => ({
    mutate: reopenPanelSpy,
    isPending: false,
    isError: reopenState.isError,
    error: reopenState.error,
  }),
  useReopenPanelForExpert: () => ({
    mutateAsync: reopenForExpertSpy,
    isPending: false,
    isError: reopenExpertState.isError,
    error: reopenExpertState.error,
    reset: vi.fn(),
  }),
}));

// The reopen-for-expert dialog mounts EvaluatorPicker, which calls useAllUsers.
// Stub a small ACTIVE roster so the manager can pick an expert.
vi.mock('@/features/users-access/hooks/useUsers', () => ({
  useAllUsers: () => ({
    data: {
      items: [{ id: 'user-x', full_name: 'Expert X', status: 'ACTIVE' }],
      totalElements: 1,
      truncated: false,
    },
    isLoading: false,
    isError: false,
  }),
}));

function renderPage(permissions: string[]) {
  signInWithPermissions(permissions);
  return render(
    renderWithProviders(
      <Routes>
        <Route
          path="/app/projects/:projectId/evaluation/panels/:panelId"
          element={<PanelDetailPage />}
        />
      </Routes>,
      ['/app/projects/proj-1/evaluation/panels/panel-1'],
    ),
  );
}

const MANAGE = [PERMISSIONS.EVALUATION_READ, PERMISSIONS.EVALUATION_PANEL_MANAGE];

describe('<PanelDetailPage /> (T3 — panel management)', () => {
  beforeEach(() => {
    detailState.status = 'COLLECTING';
    deletePanelSpy.mockClear();
    archivePanelSpy.mockClear();
    reopenPanelSpy.mockClear();
    reopenForExpertSpy.mockClear();
    deleteState.isError = false;
    deleteState.error = null;
    reopenState.isError = false;
    reopenState.error = null;
    reopenExpertState.isError = false;
    reopenExpertState.error = null;
  });
  afterEach(() => signOut());

  it('renders the panel and reuses the progress/averaged components', () => {
    renderPage(MANAGE);
    expect(screen.getByTestId('panel-detail-page')).toBeInTheDocument();
    expect(screen.getByText('Chief Risk Officer')).toBeInTheDocument();
    expect(screen.getByTestId('stub-progress')).toBeInTheDocument();
    expect(screen.getByTestId('stub-averaged')).toBeInTheDocument();
  });

  it('COLLECTING + manage → only Delete is offered (deletable predicate)', () => {
    detailState.status = 'COLLECTING';
    renderPage(MANAGE);
    expect(screen.getByTestId('panel-delete-action')).toBeInTheDocument();
    expect(screen.queryByTestId('panel-archive-action')).not.toBeInTheDocument();
    expect(screen.queryByTestId('panel-reopen-action')).not.toBeInTheDocument();
  });

  it('AWAITING_EVALUATIONS + manage → only Archive (archivable, not deletable/reopenable)', () => {
    detailState.status = 'AWAITING_EVALUATIONS';
    renderPage(MANAGE);
    expect(screen.queryByTestId('panel-delete-action')).not.toBeInTheDocument();
    expect(screen.getByTestId('panel-archive-action')).toBeInTheDocument();
    expect(screen.queryByTestId('panel-reopen-action')).not.toBeInTheDocument();
  });

  it('SUBMITTED + manage → Archive AND Reopen (no Delete)', () => {
    detailState.status = 'SUBMITTED';
    renderPage(MANAGE);
    expect(screen.queryByTestId('panel-delete-action')).not.toBeInTheDocument();
    expect(screen.getByTestId('panel-archive-action')).toBeInTheDocument();
    expect(screen.getByTestId('panel-reopen-action')).toBeInTheDocument();
  });

  it('AVERAGED + manage → Archive AND Reopen', () => {
    detailState.status = 'AVERAGED';
    renderPage(MANAGE);
    expect(screen.getByTestId('panel-archive-action')).toBeInTheDocument();
    expect(screen.getByTestId('panel-reopen-action')).toBeInTheDocument();
    expect(screen.queryByTestId('panel-delete-action')).not.toBeInTheDocument();
  });

  it('APPROVED → only Reopen-for-expert (no delete/archive/reopen)', () => {
    detailState.status = 'APPROVED';
    renderPage(MANAGE);
    // Feature 2: APPROVED offers exactly the reopen-for-additional-expert action.
    expect(screen.getByTestId('panel-reopen-for-expert-action')).toBeInTheDocument();
    expect(screen.queryByTestId('panel-delete-action')).not.toBeInTheDocument();
    expect(screen.queryByTestId('panel-archive-action')).not.toBeInTheDocument();
    expect(screen.queryByTestId('panel-reopen-action')).not.toBeInTheDocument();
  });

  it('LOCKED → NO management actions', () => {
    detailState.status = 'LOCKED';
    renderPage(MANAGE);
    expect(screen.queryByTestId('panel-detail-actions')).not.toBeInTheDocument();
  });

  it('without EVALUATION_PANEL_MANAGE no management action is rendered (read-only)', () => {
    detailState.status = 'COLLECTING';
    renderPage([PERMISSIONS.EVALUATION_READ]);
    expect(screen.queryByTestId('panel-detail-actions')).not.toBeInTheDocument();
    expect(screen.queryByTestId('panel-delete-action')).not.toBeInTheDocument();
  });

  it('Delete requires a reason (≥ 5 chars) before it fires', async () => {
    detailState.status = 'COLLECTING';
    renderPage(MANAGE);
    fireEvent.click(screen.getByTestId('panel-delete-action'));
    // The reason-required ConfirmDialog appears.
    const dialog = screen.getByRole('dialog');
    const reason = within(dialog).getByTestId('confirm-dialog-reason');
    const confirm = within(dialog).getByRole('button', { name: /удал|delete/i });
    fireEvent.click(confirm);
    expect(deletePanelSpy).not.toHaveBeenCalled(); // empty reason — disabled
    fireEvent.change(reason, { target: { value: 'duplicate panel' } });
    fireEvent.click(confirm);
    await waitFor(() => expect(deletePanelSpy).toHaveBeenCalledWith('duplicate panel'));
  });

  it('Reopen fires immediately (no reason required)', () => {
    detailState.status = 'SUBMITTED';
    renderPage(MANAGE);
    fireEvent.click(screen.getByTestId('panel-reopen-action'));
    expect(reopenPanelSpy).toHaveBeenCalledTimes(1);
  });

  // Regression guard for the production "delete does nothing" report: when the
  // server rejects the delete, the confirm dialog must STAY OPEN and SHOW the
  // error (code + ref), never silently no-op.
  it('Delete failure surfaces the server error and keeps the dialog open', () => {
    detailState.status = 'COLLECTING';
    deleteState.isError = true;
    deleteState.error = new ApiError(400, {
      code: 'PANEL_NOT_DELETABLE',
      message: 'forced',
      correlation_id: 'corr-xyz',
    });
    renderPage(MANAGE);
    fireEvent.click(screen.getByTestId('panel-delete-action'));
    const dialog = screen.getByRole('dialog');
    const banner = within(dialog).getByTestId('confirm-dialog-error');
    expect(banner).toBeInTheDocument();
    // The stable code + correlation id are surfaced for diagnosis.
    expect(banner).toHaveTextContent('PANEL_NOT_DELETABLE');
    expect(banner).toHaveTextContent('corr-xyz');
    // Dialog is NOT dismissed on failure.
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it('Reopen failure surfaces an inline error banner (no silent no-op)', () => {
    detailState.status = 'SUBMITTED';
    reopenState.isError = true;
    reopenState.error = new ApiError(404, { code: 'NOT_FOUND', message: 'gone' });
    renderPage(MANAGE);
    expect(screen.getByTestId('panel-reopen-error')).toBeInTheDocument();
  });

  // ---------- Feature 2: reopen for additional expert ----------

  it('reopen-for-expert action is NOT shown for a non-APPROVED panel', () => {
    detailState.status = 'AVERAGED';
    renderPage(MANAGE);
    expect(
      screen.queryByTestId('panel-reopen-for-expert-action'),
    ).not.toBeInTheDocument();
  });

  it('reopen-for-expert action IS shown for an APPROVED panel + MANAGE', () => {
    detailState.status = 'APPROVED';
    renderPage(MANAGE);
    expect(screen.getByTestId('panel-reopen-for-expert-action')).toBeInTheDocument();
  });

  it('reopen-for-expert action is hidden for a read-only (no MANAGE) user', () => {
    detailState.status = 'APPROVED';
    renderPage([PERMISSIONS.EVALUATION_READ]);
    expect(
      screen.queryByTestId('panel-reopen-for-expert-action'),
    ).not.toBeInTheDocument();
  });

  it('a valid reopen submits the camel payload the hook maps to snake_case', async () => {
    detailState.status = 'APPROVED';
    renderPage(MANAGE);
    fireEvent.click(screen.getByTestId('panel-reopen-for-expert-action'));
    // Empty form → confirm does nothing.
    fireEvent.click(screen.getByTestId('reopen-for-expert-confirm'));
    expect(reopenForExpertSpy).not.toHaveBeenCalled();
    // Fill the picker + a valid reason.
    fireEvent.change(screen.getByTestId('reopen-expert-user'), {
      target: { value: 'user-x' },
    });
    fireEvent.change(screen.getByTestId('reopen-expert-reason'), {
      target: { value: 'needs a second opinion' },
    });
    fireEvent.click(screen.getByTestId('reopen-for-expert-confirm'));
    await waitFor(() =>
      expect(reopenForExpertSpy).toHaveBeenCalledWith({
        additionalEvaluatorUserId: 'user-x',
        reason: 'needs a second opinion',
      }),
    );
  });

  it('PANEL_NOT_APPROVED_FOR_REOPEN renders its dedicated message in the dialog', () => {
    detailState.status = 'APPROVED';
    reopenExpertState.isError = true;
    reopenExpertState.error = new ApiError(400, {
      code: 'PANEL_NOT_APPROVED_FOR_REOPEN',
      message: 'forced',
    });
    renderPage(MANAGE);
    fireEvent.click(screen.getByTestId('panel-reopen-for-expert-action'));
    const banner = screen.getByTestId('reopen-for-expert-error');
    // The RU default locale message for the not-approved code.
    expect(banner).toHaveTextContent(/не находится в статусе/i);
  });
});
