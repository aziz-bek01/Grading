import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { Route, Routes } from 'react-router-dom';
import {
  renderWithProviders,
  signInWithPermissions,
  signOut,
} from '@/test/testUtils';
import { PERMISSIONS } from '@/shared/types/permissions';
import { useAuthStore } from '@/features/auth/authStore';
import { MyEvaluationsPage } from './MyEvaluationsPage';
import type { MyEvaluationRow } from '../api/myEvaluationApi';

// Drive the page through the hook's public surface (mirrors the PanelDetailPage
// test style) so the test focuses on rendering / linking / states.
const queryState: {
  data: MyEvaluationRow[] | undefined;
  isLoading: boolean;
  error: unknown;
} = { data: [], isLoading: false, error: null };

vi.mock('../hooks/useMyEvaluations', () => ({
  useMyEvaluations: () => ({
    data: queryState.data,
    isLoading: queryState.isLoading,
    error: queryState.error,
    refetch: vi.fn(),
  }),
}));

function row(over: Partial<MyEvaluationRow> = {}): MyEvaluationRow {
  return {
    evaluationId: 'eval-1',
    // Each row carries its OWN owning project — the deep-link is built from
    // this, NOT from the active project (the inbox is project-agnostic).
    projectId: 'proj-1',
    panelId: 'panel-1',
    positionId: 'pos-1',
    positionCode: 'FIN-AN',
    positionTitle: { 'ru-RU': 'Финансовый аналитик', 'en-US': 'Financial Analyst' },
    title: 'Financial Analyst',
    status: 'DRAFT',
    filledFactorsCount: 3,
    totalFactorsCount: 8,
    // PART 4 — new methodology fields (optional; defaults so old-style tests pass)
    methodologyVersionId: 'mver-1',
    methodologyId: 'meth-1',
    methodologyName: { 'ru-RU': 'Методология А', 'en-US': 'Methodology A' },
    methodologyActiveVersionNumber: 2,
    ...over,
  };
}

function setActiveProject(id: string | null) {
  useAuthStore.getState().setActiveProject(
    id ? { id, slug: 's', name: 'P', tenant_id: 't' } : null,
  );
}

function renderPage(
  activeProjectId: string | null = null,
  // Default to a MANAGER context (EVALUATION_READ + METHODOLOGY_READ) so the
  // existing deep-link assertions keep the per-sheet Matrix detail route.
  // Committee-scorer tests pass only EVALUATION_READ.
  permissions: string[] = [PERMISSIONS.EVALUATION_READ, PERMISSIONS.METHODOLOGY_READ],
) {
  // The deep-link no longer depends on the active project — leave it unset by
  // default. Tests that want to prove independence set it to a DIFFERENT id.
  signInWithPermissions(permissions);
  setActiveProject(activeProjectId);
  return render(
    renderWithProviders(
      <Routes>
        <Route path="/app/my-evaluations" element={<MyEvaluationsPage />} />
      </Routes>,
      ['/app/my-evaluations'],
    ),
  );
}

describe('<MyEvaluationsPage /> (Feature 1 + Part 4)', () => {
  beforeEach(() => {
    queryState.data = [];
    queryState.isLoading = false;
    queryState.error = null;
  });
  afterEach(() => signOut());

  it('renders the page heading', () => {
    renderPage();
    expect(screen.getByTestId('my-evaluations-page')).toBeInTheDocument();
  });

  it('shows a loading skeleton while loading', () => {
    queryState.isLoading = true;
    renderPage();
    expect(screen.getByTestId('my-evaluations-skeleton')).toBeInTheDocument();
  });

  it('renders rows with the localized title, code and a progress chip', () => {
    queryState.data = [row()];
    renderPage();
    expect(screen.getByText('Financial Analyst')).toBeInTheDocument();
    expect(screen.getByText('FIN-AN')).toBeInTheDocument();
    // ProgressChip shows the filled/total ratio.
    expect(screen.getByText('3/8')).toBeInTheDocument();
  });

  it("links each row to the by-factor scoring view built from the row's OWN project", () => {
    queryState.data = [row({ evaluationId: 'eval-42', projectId: 'proj-77' })];
    // A DIFFERENT project is active — the link must still use the row's project,
    // proving the deep-link is independent of which project is active.
    renderPage('proj-other');
    const link = screen.getByTestId('open-my-evaluation-eval-42');
    expect(link).toHaveAttribute(
      'href',
      '/app/projects/proj-77/evaluation?mode=by-factor',
    );
  });

  it('links every row regardless of the active project (no project active)', () => {
    queryState.data = [
      row({ evaluationId: 'eval-a', projectId: 'proj-a' }),
      row({ evaluationId: 'eval-b', projectId: 'proj-b', methodologyId: 'meth-2', methodologyName: { 'en-US': 'Methodology B' } }),
    ];
    renderPage(null);
    expect(screen.getByTestId('open-my-evaluation-eval-a')).toHaveAttribute(
      'href',
      '/app/projects/proj-a/evaluation?mode=by-factor',
    );
    expect(screen.getByTestId('open-my-evaluation-eval-b')).toHaveAttribute(
      'href',
      '/app/projects/proj-b/evaluation?mode=by-factor',
    );
  });

  it('committee scorer (no METHODOLOGY_READ): row links to the by-factor scoring view, NOT the per-sheet Matrix detail', () => {
    queryState.data = [row({ evaluationId: 'eval-42', projectId: 'proj-77' })];
    renderPage('proj-other', [PERMISSIONS.EVALUATION_READ]);
    const link = screen.getByTestId('open-my-evaluation-eval-42');
    // By-factor mode on the project Evaluation page — the evaluationId is NOT in
    // the URL (the K-sheet is server-scoped to the scorer's own assigned rows).
    expect(link).toHaveAttribute(
      'href',
      '/app/projects/proj-77/evaluation?mode=by-factor',
    );
  });

  it('shows the empty state when no evaluations are assigned', () => {
    queryState.data = [];
    renderPage();
    // Default test locale is ru-RU (matches the rest of the suite); accept
    // either the RU or EN empty-state title so the test is locale-robust.
    expect(
      screen.getByText(/no evaluations assigned to you yet|не назначено ни одной оценки/i),
    ).toBeInTheDocument();
  });

  it('shows an error state on failure', () => {
    queryState.error = new Error('boom');
    renderPage();
    expect(screen.getByRole('alert')).toBeInTheDocument();
  });

  it('renders a non-linkable guard row when projectId is unexpectedly missing', () => {
    // projectId is non-optional in the domain type, but the BE could omit it;
    // the page degrades gracefully to a non-linkable row instead of a broken URL.
    queryState.data = [
      row({ evaluationId: 'eval-7', projectId: undefined as unknown as string }),
    ];
    renderPage();
    expect(screen.queryByTestId('open-my-evaluation-eval-7')).not.toBeInTheDocument();
    expect(screen.getByTestId('my-evaluation-row-eval-7')).toBeInTheDocument();
  });

  it('shows NoAccessState without EVALUATION_READ', () => {
    signInWithPermissions([]);
    render(
      renderWithProviders(
        <Routes>
          <Route path="/app/my-evaluations" element={<MyEvaluationsPage />} />
        </Routes>,
        ['/app/my-evaluations'],
      ),
    );
    expect(screen.queryByTestId('my-evaluations-page')).not.toBeInTheDocument();
  });

  // ----- PART 4: grouping + filter tests -----

  it('groups rows into separate sections by methodologyId', () => {
    queryState.data = [
      row({
        evaluationId: 'eval-x1',
        projectId: 'proj-1',
        methodologyId: 'meth-A',
        methodologyName: { 'en-US': 'Alpha Methodology' },
        methodologyActiveVersionNumber: 1,
      }),
      row({
        evaluationId: 'eval-x2',
        projectId: 'proj-1',
        methodologyId: 'meth-B',
        methodologyName: { 'en-US': 'Beta Methodology' },
        methodologyActiveVersionNumber: 3,
        title: 'HR Manager',
        positionCode: 'HR-MGR',
      }),
    ];
    renderPage();
    // Both group headers visible
    expect(screen.getByTestId('my-evaluations-group-meth-A')).toBeInTheDocument();
    expect(screen.getByTestId('my-evaluations-group-meth-B')).toBeInTheDocument();
    // Both rows visible (both groups expanded by default)
    expect(screen.getByTestId('open-my-evaluation-eval-x1')).toBeInTheDocument();
    expect(screen.getByTestId('open-my-evaluation-eval-x2')).toBeInTheDocument();
  });

  it('methodology filter narrows visible rows to the selected methodology', () => {
    queryState.data = [
      row({
        evaluationId: 'eval-m1',
        methodologyId: 'meth-A',
        methodologyName: { 'en-US': 'Alpha Methodology' },
        title: 'Row A',
        positionCode: 'A-001',
      }),
      row({
        evaluationId: 'eval-m2',
        methodologyId: 'meth-B',
        methodologyName: { 'en-US': 'Beta Methodology' },
        title: 'Row B',
        positionCode: 'B-001',
      }),
    ];
    renderPage();

    // Both rows visible before filtering
    expect(screen.getByTestId('open-my-evaluation-eval-m1')).toBeInTheDocument();
    expect(screen.getByTestId('open-my-evaluation-eval-m2')).toBeInTheDocument();

    // Apply methodology filter: select meth-A
    const methodologySelect = screen.getByTestId('my-evaluations-filter-methodology');
    fireEvent.change(methodologySelect, { target: { value: 'meth-A' } });

    expect(screen.getByTestId('open-my-evaluation-eval-m1')).toBeInTheDocument();
    expect(screen.queryByTestId('open-my-evaluation-eval-m2')).not.toBeInTheDocument();
  });

  it('status filter narrows visible rows', () => {
    queryState.data = [
      row({ evaluationId: 'eval-s1', status: 'DRAFT' }),
      row({ evaluationId: 'eval-s2', status: 'SUBMITTED', methodologyId: 'meth-2', methodologyName: { 'en-US': 'M2' } }),
    ];
    renderPage();

    const statusSelect = screen.getByTestId('my-evaluations-filter-status');
    fireEvent.change(statusSelect, { target: { value: 'SUBMITTED' } });

    expect(screen.queryByTestId('open-my-evaluation-eval-s1')).not.toBeInTheDocument();
    expect(screen.getByTestId('open-my-evaluation-eval-s2')).toBeInTheDocument();
  });

  it('position search input filters rows by title/code (case-insensitive)', () => {
    queryState.data = [
      row({ evaluationId: 'eval-p1', title: 'Financial Analyst', positionCode: 'FIN-01' }),
      row({ evaluationId: 'eval-p2', title: 'HR Manager', positionCode: 'HR-MGR', methodologyId: 'meth-2', methodologyName: { 'en-US': 'M2' } }),
    ];
    renderPage();

    const searchInput = screen.getByTestId('my-evaluations-filter-search');
    fireEvent.change(searchInput, { target: { value: 'financial' } });

    expect(screen.getByTestId('open-my-evaluation-eval-p1')).toBeInTheDocument();
    expect(screen.queryByTestId('open-my-evaluation-eval-p2')).not.toBeInTheDocument();
  });

  it('shows no-match state when all rows are filtered out', () => {
    queryState.data = [row({ evaluationId: 'eval-nm1', title: 'CEO' })];
    renderPage();

    const searchInput = screen.getByTestId('my-evaluations-filter-search');
    fireEvent.change(searchInput, { target: { value: 'zzz-not-a-real-position' } });

    // Filter bar remains visible
    expect(screen.getByTestId('my-evaluations-filter-bar')).toBeInTheDocument();
    // No match state shown
    expect(screen.getByTestId('my-evaluations-filter-clear')).toBeInTheDocument();
    // Original row not visible
    expect(screen.queryByTestId('open-my-evaluation-eval-nm1')).not.toBeInTheDocument();
  });
});
