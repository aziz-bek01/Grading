import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import { EvaluationByFactorView } from './EvaluationByFactorView';
import type { Factor, MethodologyVersion } from '@/features/methodology/types';
import type { Methodology } from '@/features/methodology/types';

const factor: Factor = {
  id: 'f-1',
  methodology_version_id: 'v-1',
  code: 'EDUCATION',
  name_i18n: {
    'ru-RU': 'Образование',
    'uz-Cyrl-UZ': 'Образование',
    'uz-Latn-UZ': 'Образование',
    'en-US': 'Образование',
  },
  weight: 10,
  max_points: 100,
  sort_order: 0,
  required: true,
  levels: [],
};

const methodology: Methodology = {
  id: 'm-1',
  project_id: 'proj-1',
  code: 'M1',
  name_i18n: {
    'ru-RU': 'CFO Финансы',
    'uz-Cyrl-UZ': 'CFO Финансы',
    'uz-Latn-UZ': 'CFO Финансы',
    'en-US': 'CFO Финансы',
  },
  methodology_type: 'CLASSIC_8_FACTOR',
  status: 'ACTIVE',
  active_version_id: 'v-1',
  active_version_number: 3,
  active_version_status: 'APPROVED',
};

const version: MethodologyVersion = {
  id: 'v-1',
  methodology_id: 'm-1',
  project_id: 'proj-1',
  version_number: 3,
  status: 'APPROVED',
  scoring_mode: 'WEIGHTED_POINTS',
  target_total_points: 1000,
  factors: [factor],
  created_at: '2026-01-01T00:00:00Z',
  updated_at: '2026-01-01T00:00:00Z',
};

vi.mock('@/features/methodology/hooks/useMethodology', () => ({
  useMethodologies: () => ({ data: { items: [methodology] }, isLoading: false }),
  useMethodologyVersion: () => ({ data: version, isLoading: false }),
}));

vi.mock('@/features/organization/hooks/useDepartmentTree', () => ({
  useDepartmentTree: () => ({ data: [], isLoading: false }),
}));

// The view reuses the evaluations list ONLY to compute a default selection /
// selector options — never for the rows. Mock it empty so this header test
// stays isolated from MSW evaluation fixtures.
vi.mock('../../hooks/useEvaluation', () => ({
  useEvaluations: () => ({ data: { items: [] }, isLoading: false }),
}));

vi.mock('../../hooks/useEvaluationsByFactor', () => ({
  useEvaluationsByFactor: () => ({
    data: { items: [], page: 0, size: 25, total_elements: 0, total_pages: 1 },
    isLoading: false,
    isError: false,
    isFetching: false,
    refetch: vi.fn(),
  }),
  useBulkScoreSet: () => ({ mutateAsync: vi.fn() }),
  useBulkSubmit: () => ({ mutateAsync: vi.fn() }),
}));

function renderView() {
  return render(
    renderWithProviders(
      <EvaluationByFactorView
        projectId="proj-1"
        factorIdFromUrl="f-1"
        onFactorChange={() => {}}
      />,
    ),
  );
}

describe('EvaluationByFactorView — methodology header (FE-7)', () => {
  beforeEach(() => signIn('super-admin'));
  afterEach(() => signOut());

  it('shows the methodology name + v{active_version_number} + scoring-mode badge', async () => {
    renderView();
    await waitFor(() =>
      expect(
        screen.getByTestId('byfactor-methodology-header'),
      ).toBeInTheDocument(),
    );
    const header = screen.getByTestId('byfactor-methodology-header');
    expect(header).toHaveTextContent('CFO Финансы');
    expect(header).toHaveTextContent('v3');
    // Scoring-mode badge maps WEIGHTED_POINTS to its localized label.
    const badge = screen.getByTestId('byfactor-scoring-mode-badge');
    expect(badge).toBeInTheDocument();
    expect(badge.textContent?.trim().length).toBeGreaterThan(0);
  });

  it('renders the localized factor tab name (FE-8) within the header strip', async () => {
    renderView();
    await waitFor(() =>
      expect(screen.getByTestId('factor-tab-name-EDUCATION')).toBeInTheDocument(),
    );
    expect(screen.getByTestId('factor-tab-name-EDUCATION')).toHaveTextContent(
      'Образование',
    );
  });
});
