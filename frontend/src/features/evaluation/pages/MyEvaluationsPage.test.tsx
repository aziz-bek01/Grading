import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';
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
    ...over,
  };
}

function setActiveProject(id: string | null) {
  useAuthStore.getState().setActiveProject(
    id ? { id, slug: 's', name: 'P', tenant_id: 't' } : null,
  );
}

function renderPage(activeProjectId: string | null = null) {
  // The deep-link no longer depends on the active project — leave it unset by
  // default. Tests that want to prove independence set it to a DIFFERENT id.
  signInWithPermissions([PERMISSIONS.EVALUATION_READ]);
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

describe('<MyEvaluationsPage /> (Feature 1)', () => {
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

  it("links each row to the project-scoped sheet built from the row's OWN project", () => {
    queryState.data = [row({ evaluationId: 'eval-42', projectId: 'proj-77' })];
    // A DIFFERENT project is active — the link must still use the row's project,
    // proving the deep-link is independent of which project is active.
    renderPage('proj-other');
    const link = screen.getByTestId('open-my-evaluation-eval-42');
    expect(link).toHaveAttribute(
      'href',
      '/app/projects/proj-77/evaluation/eval-42',
    );
  });

  it('links every row regardless of the active project (no project active)', () => {
    queryState.data = [
      row({ evaluationId: 'eval-a', projectId: 'proj-a' }),
      row({ evaluationId: 'eval-b', projectId: 'proj-b' }),
    ];
    renderPage(null);
    expect(screen.getByTestId('open-my-evaluation-eval-a')).toHaveAttribute(
      'href',
      '/app/projects/proj-a/evaluation/eval-a',
    );
    expect(screen.getByTestId('open-my-evaluation-eval-b')).toHaveAttribute(
      'href',
      '/app/projects/proj-b/evaluation/eval-b',
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
});
