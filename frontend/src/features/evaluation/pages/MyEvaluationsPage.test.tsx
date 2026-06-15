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

function renderPage(projectId: string | null = 'proj-1') {
  // signIn resets activeProject, so set it AFTER establishing the session.
  signInWithPermissions([PERMISSIONS.EVALUATION_READ]);
  setActiveProject(projectId);
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

  it('links each row to the project-scoped scoring sheet', () => {
    queryState.data = [row({ evaluationId: 'eval-42' })];
    renderPage();
    const link = screen.getByTestId('open-my-evaluation-eval-42');
    expect(link).toHaveAttribute(
      'href',
      '/app/projects/proj-1/evaluation/eval-42',
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

  it('renders the row without a link and a hint when no project is active', () => {
    queryState.data = [row({ evaluationId: 'eval-7' })];
    renderPage(null);
    expect(screen.queryByTestId('open-my-evaluation-eval-7')).not.toBeInTheDocument();
    expect(screen.getByTestId('my-evaluation-row-eval-7')).toBeInTheDocument();
    expect(screen.getByTestId('my-evaluations-no-project-hint')).toBeInTheDocument();
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
