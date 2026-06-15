import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Route, Routes } from 'react-router-dom';
import {
  renderWithProviders,
  signInWithPermissions,
  signOut,
} from '@/test/testUtils';
import { PERMISSIONS } from '@/shared/types/permissions';
import { EvaluationDetailsPage } from './EvaluationDetailsPage';
import type { Evaluation } from '../types';
import type { MethodologyVersion } from '@/features/methodology/types';

/**
 * Regression guard for the P0 route-param drift bug: the route declares
 * `evaluation/:evaluationId` but the page was reading `evaluation_id` from
 * useParams -> always undefined -> useEvaluation('') disabled -> the page
 * rendered a blank EmptyState for EVERY user (broke the My-evaluations deep
 * link AND the EvaluationListPage row link, both of which use this URL shape).
 *
 * The contract this test pins: the name read by useParams MUST exactly match
 * router.tsx's `:evaluationId`. We mount under the REAL route path with a
 * concrete id and assert the id reaches useEvaluation NON-EMPTY (and the sheet
 * renders instead of the EmptyState).
 */

// ---------- Hook mocks (mirror PanelDetailPage.test's hook-mock style) ----------

// Captures every id useEvaluation is invoked with so the test can assert the
// route param actually reached the data layer (not '').
const evaluationIdSpy = vi.fn<(id: string | undefined) => void>();

function makeEvaluation(id: string): Evaluation {
  return {
    id,
    project_id: 'proj-77',
    position_id: 'pos-1',
    methodology_version_id: 'mv-1',
    status: 'DRAFT',
    raw_total_score: null,
    displayed_total_score: null,
    grade_band_id: null,
    assigned_grade_number: null,
  } as Evaluation;
}

vi.mock('../hooks/useEvaluation', () => {
  const ok = <T,>(data: T) => ({
    data,
    isLoading: false,
    error: null,
    refetch: vi.fn(),
  });
  const noopMutation = () => ({ mutate: vi.fn(), isPending: false });
  return {
    useEvaluation: (id: string | undefined) => {
      evaluationIdSpy(id);
      // Mirror the real hook: an empty id yields NO data (disabled query),
      // which is exactly what produced the blank EmptyState in the bug.
      return id ? ok(makeEvaluation(id)) : ok(undefined);
    },
    useEvaluationScores: () => ok([]),
    useCalibrationHistory: () => ok([]),
    useUpsertScore: noopMutation,
    useSubmitEvaluation: noopMutation,
    useApproveEvaluation: noopMutation,
    useRequestChanges: noopMutation,
    useLockEvaluation: noopMutation,
    useArchiveEvaluation: noopMutation,
    useCalibrateScore: noopMutation,
  };
});

function makeVersion(): MethodologyVersion {
  return {
    id: 'mv-1',
    factors: [],
  } as unknown as MethodologyVersion;
}

vi.mock('@/features/methodology/hooks/useMethodology', () => ({
  useMethodologyVersion: () => ({
    data: makeVersion(),
    isLoading: false,
    error: null,
    refetch: vi.fn(),
  }),
}));

// The page resolves position_id -> name via usePositions; stub an empty list.
vi.mock('@/features/positions/hooks/usePositions', () => ({
  usePositions: () => ({ data: { items: [] }, isLoading: false }),
}));

// SUBMITTED-only inline approval badge — keep it inert.
vi.mock('@/features/approval/hooks/useApprovals', () => ({
  useApprovalRequests: () => ({ data: { items: [] }, isLoading: false }),
}));

// Heavy child components — stub so the test focuses on the param contract.
vi.mock('../components/EvaluationMatrix', () => ({
  EvaluationMatrix: () => <div data-testid="stub-matrix" />,
}));

const REAL_ROUTE = '/app/projects/:projectId/evaluation/:evaluationId';

function renderPage(entry: string) {
  signInWithPermissions([PERMISSIONS.EVALUATION_READ]);
  return render(
    renderWithProviders(
      <Routes>
        <Route path={REAL_ROUTE} element={<EvaluationDetailsPage />} />
      </Routes>,
      [entry],
    ),
  );
}

describe('<EvaluationDetailsPage /> route-param contract', () => {
  beforeEach(() => {
    evaluationIdSpy.mockClear();
  });
  afterEach(() => signOut());

  it('reads the id under the route-declared :evaluationId name (NON-EMPTY)', () => {
    renderPage('/app/projects/proj-77/evaluation/eval-42');
    // The id from the URL must reach the hook — never '' (the bug).
    expect(evaluationIdSpy).toHaveBeenCalledWith('eval-42');
    expect(evaluationIdSpy).not.toHaveBeenCalledWith('');
  });

  it('renders the evaluation sheet, NOT the blank EmptyState', () => {
    renderPage('/app/projects/proj-77/evaluation/eval-42');
    // Tabs + matrix prove we got past the `!evaluation.data` EmptyState guard.
    expect(screen.getByTestId('tab-matrix')).toBeInTheDocument();
    expect(screen.getByTestId('stub-matrix')).toBeInTheDocument();
  });

  it('a different concrete id flows through unchanged (param not hardcoded)', () => {
    renderPage('/app/projects/proj-a/evaluation/eval-deep-link');
    expect(evaluationIdSpy).toHaveBeenCalledWith('eval-deep-link');
  });
});
