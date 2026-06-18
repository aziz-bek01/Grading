import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { useState } from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import { EvaluationByFactorView } from './EvaluationByFactorView';
import type {
  Factor,
  Methodology,
  MethodologyVersion,
} from '@/features/methodology/types';
import type { Evaluation, EvaluationsByFactorFilters } from '../../types';

// ---------- Fixtures: two methodologies, each with its own active version ----------

function makeFactor(
  id: string,
  versionId: string,
  code: string,
  order: number,
): Factor {
  return {
    id,
    methodology_version_id: versionId,
    code,
    name_i18n: { 'ru-RU': code, 'en-US': code },
    weight: 10,
    max_points: 100,
    sort_order: order,
    required: true,
    levels: [],
  };
}

// 8-factor methodology (version v-8) — 2 factors for the test.
const factors8 = [
  makeFactor('f8-1', 'v-8', 'EDU', 0),
  makeFactor('f8-2', 'v-8', 'EXP', 1),
];
// HR-Lab 12-factor methodology (version v-12) — 2 factors for the test.
const factors12 = [
  makeFactor('f12-1', 'v-12', 'COMPLEX', 0),
  makeFactor('f12-2', 'v-12', 'IMPACT', 1),
];

const meth8: Methodology = {
  id: 'm-8',
  project_id: 'proj-1',
  code: 'M8',
  name_i18n: { 'ru-RU': '8 факторли методология', 'en-US': '8-factor' },
  methodology_type: 'CLASSIC_8_FACTOR',
  status: 'ACTIVE',
  active_version_id: 'v-8',
  active_version_number: 1,
  active_version_status: 'APPROVED',
};

const meth12: Methodology = {
  id: 'm-12',
  project_id: 'proj-1',
  code: 'M12',
  name_i18n: { 'ru-RU': 'Методология HR Lab', 'en-US': 'HR Lab' },
  methodology_type: 'CUSTOM',
  status: 'ACTIVE',
  active_version_id: 'v-12',
  active_version_number: 1,
  active_version_status: 'APPROVED',
};

const version8: MethodologyVersion = {
  id: 'v-8',
  methodology_id: 'm-8',
  project_id: 'proj-1',
  version_number: 1,
  status: 'APPROVED',
  scoring_mode: 'WEIGHTED_POINTS',
  target_total_points: 1000,
  factors: factors8,
  created_at: '2026-01-01T00:00:00Z',
  updated_at: '2026-01-01T00:00:00Z',
};

const version12: MethodologyVersion = {
  id: 'v-12',
  methodology_id: 'm-12',
  project_id: 'proj-1',
  version_number: 1,
  status: 'APPROVED',
  scoring_mode: 'DIRECT_POINTS',
  target_total_points: 0,
  factors: factors12,
  created_at: '2026-01-01T00:00:00Z',
  updated_at: '2026-01-01T00:00:00Z',
};

// Most evaluations belong to the 8-factor methodology (3 vs 1) → it is the
// default selection.
const evaluations: Evaluation[] = [
  { id: 'e1', project_id: 'proj-1', position_id: 'p1', methodology_version_id: 'v-8', status: 'INCOMPLETE' },
  { id: 'e2', project_id: 'proj-1', position_id: 'p2', methodology_version_id: 'v-8', status: 'INCOMPLETE' },
  { id: 'e3', project_id: 'proj-1', position_id: 'p3', methodology_version_id: 'v-8', status: 'DRAFT' },
  { id: 'e4', project_id: 'proj-1', position_id: 'p4', methodology_version_id: 'v-12', status: 'DRAFT' },
];

vi.mock('@/features/methodology/hooks/useMethodology', () => ({
  useMethodologies: () => ({
    data: { items: [meth8, meth12] },
    isLoading: false,
  }),
  // Manager path: useMyMethodologies is called unconditionally (Rules of Hooks)
  // but the manager has METHODOLOGY_READ so the /my endpoint is never actually
  // used. Return an inert empty result so the component does not crash.
  // NOTE: /my returns a plain array in `data`, NOT a {items} envelope.
  useMyMethodologies: () => ({ data: [], isLoading: false }),
  useMethodologyVersion: (versionId?: string) => ({
    data:
      versionId === 'v-8'
        ? version8
        : versionId === 'v-12'
          ? version12
          : undefined,
    isLoading: false,
  }),
}));

vi.mock('@/features/organization/hooks/useDepartmentTree', () => ({
  useDepartmentTree: () => ({ data: [], isLoading: false }),
}));

vi.mock('../../hooks/useEvaluation', () => ({
  useEvaluations: () => ({ data: { items: evaluations }, isLoading: false }),
}));

// Capture the filters the rows query is invoked with so we can assert the
// factorId follows the SELECTED version (BE scopes rows by the factor's
// own version, so passing the right factor is how the FE scopes).
const byFactorFilters: EvaluationsByFactorFilters[] = [];

vi.mock('../../hooks/useEvaluationsByFactor', () => ({
  useEvaluationsByFactor: (filters: EvaluationsByFactorFilters) => {
    byFactorFilters.push(filters);
    return {
      data: { items: [], page: 0, size: 25, total_elements: 0, total_pages: 1 },
      isLoading: false,
      isError: false,
      isFetching: false,
      refetch: vi.fn(),
    };
  },
  useBulkScoreSet: () => ({ mutateAsync: vi.fn() }),
  useBulkSubmit: () => ({ mutateAsync: vi.fn() }),
}));

/**
 * Controlled host that mirrors how EvaluationListPage wires the URL params:
 * methodology + factor live in state, and switching the selector swaps the
 * methodology and clears the factor (the parent contract).
 */
function Host() {
  const [methodologyId, setMethodologyId] = useState<string | null>(null);
  const [factorId, setFactorId] = useState<string | null>(null);
  return (
    <EvaluationByFactorView
      projectId="proj-1"
      factorIdFromUrl={factorId}
      onFactorChange={setFactorId}
      methodologyIdFromUrl={methodologyId}
      onMethodologyChange={(id) => {
        setMethodologyId(id);
        setFactorId(null);
      }}
    />
  );
}

function renderHost() {
  return render(renderWithProviders(<Host />));
}

describe('EvaluationByFactorView — methodology selector (version scoping)', () => {
  beforeEach(() => {
    signIn('super-admin');
    byFactorFilters.length = 0;
  });
  afterEach(() => signOut());

  it('renders a selector (project has >1 methodology) defaulting to the one with the most evaluations', async () => {
    renderHost();
    const select = await screen.findByTestId('byfactor-methodology-select');
    // Default = m-8 (3 evaluations) over m-12 (1).
    expect((select as HTMLSelectElement).value).toBe('m-8');
    // Header reflects the default name.
    const header = screen.getByTestId('byfactor-methodology-header');
    expect(header).toHaveTextContent('8 факторли методология');
  });

  it('shows ONLY the selected version factor tabs and requests rows for one of its factors', async () => {
    renderHost();
    await screen.findByTestId('byfactor-methodology-select');
    // Tabs come from v-8 (EDU/EXP), NOT v-12 (COMPLEX/IMPACT).
    await waitFor(() =>
      expect(screen.getByTestId('factor-tab-EDU')).toBeInTheDocument(),
    );
    expect(screen.getByTestId('factor-tab-EXP')).toBeInTheDocument();
    expect(screen.queryByTestId('factor-tab-COMPLEX')).not.toBeInTheDocument();
    // The rows query was invoked with a v-8 factor id.
    await waitFor(() =>
      expect(
        byFactorFilters.some((f) => f.factorId === 'f8-1'),
      ).toBe(true),
    );
    expect(byFactorFilters.some((f) => f.factorId === 'f12-1')).toBe(false);
  });

  it('switching the selector swaps factor tabs AND the rows request to the other version', async () => {
    renderHost();
    const select = await screen.findByTestId('byfactor-methodology-select');
    byFactorFilters.length = 0;
    fireEvent.change(select, { target: { value: 'm-12' } });

    // Header + selector now reflect HR Lab.
    await waitFor(() =>
      expect(
        (screen.getByTestId('byfactor-methodology-select') as HTMLSelectElement)
          .value,
      ).toBe('m-12'),
    );
    expect(screen.getByTestId('byfactor-methodology-header')).toHaveTextContent(
      'Методология HR Lab',
    );
    // Tabs now come from v-12; the v-8 tabs are gone.
    await waitFor(() =>
      expect(screen.getByTestId('factor-tab-COMPLEX')).toBeInTheDocument(),
    );
    expect(screen.queryByTestId('factor-tab-EDU')).not.toBeInTheDocument();
    // The rows query now uses a v-12 factor id (NOT a v-8 one).
    await waitFor(() =>
      expect(
        byFactorFilters.some((f) => f.factorId === 'f12-1'),
      ).toBe(true),
    );
    expect(byFactorFilters.some((f) => f.factorId === 'f8-1')).toBe(false);
  });
});
