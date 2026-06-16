import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import { DepartmentPanelProgress } from './DepartmentPanelProgress';
import type { DepartmentPositionCount } from '@/features/organization/hooks/useDepartmentPositionCounts';
import type { Department } from '@/features/organization/types/organizationTypes';
import type { Position } from '@/features/positions/types/positionTypes';
import type { Panel } from '../../panelTypes';

// T4 — the strip now reads the ONE shared server-authoritative counts hook for
// the position TOTAL + subtree roll-up (no FE bucketing of a page-capped list).
// We mock the hook so the direct + subtree figures are deterministic.
const countsState = { data: new Map<string, DepartmentPositionCount>() };

vi.mock('@/features/organization/hooks/useDepartmentPositionCounts', () => ({
  useDepartmentPositionCounts: () => ({ data: countsState.data, isLoading: false, isError: false }),
}));

const departments: Department[] = [
  { id: 'd-root', project_id: 'p1', parent_id: null, code: 'R001', name_i18n: { 'en-US': 'Head office' }, type: 'BRANCH', status: 'ACTIVE' },
  { id: 'd-ops', project_id: 'p1', parent_id: 'd-root', code: 'OPS', name_i18n: { 'en-US': 'Operations' }, type: 'DEPARTMENT', status: 'ACTIVE' },
  { id: 'd-hr', project_id: 'p1', parent_id: 'd-root', code: 'HR', name_i18n: { 'en-US': 'HR' }, type: 'DEPARTMENT', status: 'ACTIVE' },
];

// Positions are used ONLY for panel→department attribution (paneled count), not
// for the position total (that comes from the mocked counts hook).
const positions: Position[] = [
  { id: 'o1', project_id: 'p1', department_id: 'd-ops', code: 'O1', title_i18n: {}, status: 'ACTIVE' },
  { id: 'o2', project_id: 'p1', department_id: 'd-ops', code: 'O2', title_i18n: {}, status: 'ACTIVE' },
  { id: 'o3', project_id: 'p1', department_id: 'd-ops', code: 'O3', title_i18n: {}, status: 'ACTIVE' },
  { id: 'h1', project_id: 'p1', department_id: 'd-hr', code: 'H1', title_i18n: {}, status: 'ACTIVE' },
  { id: 'h2', project_id: 'p1', department_id: 'd-hr', code: 'H2', title_i18n: {}, status: 'ACTIVE' },
];

const panel = (id: string, positionId: string): Panel => ({
  id,
  project_id: 'p1',
  position_id: positionId,
  position_title_i18n: {},
  methodology_version_id: 'v1',
  status: 'COLLECTING',
  min_evaluators: 3,
  evaluator_count: 0,
  completed_count: 0,
  created_at: 'x',
});

describe('<DepartmentPanelProgress /> (FE-7 + T4)', () => {
  beforeEach(() => {
    signIn('super-admin');
    // R001 (parent) holds NO direct positions but its subtree rolls up 5.
    countsState.data = new Map<string, DepartmentPositionCount>([
      ['d-root', { directCount: 0, subtreeCount: 5 }],
      ['d-ops', { directCount: 3, subtreeCount: 3 }],
      ['d-hr', { directCount: 2, subtreeCount: 2 }],
    ]);
  });
  afterEach(() => {
    signOut();
    vi.restoreAllMocks();
  });

  it('shows per-department X of Y coverage with Y from the shared BE counts hook', () => {
    render(
      renderWithProviders(
        <DepartmentPanelProgress
          projectId="p1"
          departments={departments}
          positions={positions}
          panels={[panel('pn1', 'o1'), panel('pn2', 'o2')]}
        />,
      ),
    );
    // OPS: 2 paneled of 3 direct positions.
    expect(screen.getByTestId('dept-progress-count-OPS')).toHaveTextContent('2');
    expect(screen.getByTestId('dept-progress-count-OPS')).toHaveTextContent('3');
    // HR: 0 of 2.
    const hr = screen.getByTestId('dept-progress-count-HR');
    expect(hr).toHaveTextContent('0');
    expect(hr).toHaveTextContent('2');
  });

  it('rolls up the subtree for a parent with no direct positions (R001 ↳ 5, not "0 of 0")', () => {
    render(
      renderWithProviders(
        <DepartmentPanelProgress
          projectId="p1"
          departments={departments}
          positions={positions}
          panels={[]}
        />,
      ),
    );
    // PD-3: a parent with 0 direct positions but subtree > 0 must NOT read as
    // "0 of 0 ↳5" (which looks empty). It renders the dedicated subtree-only
    // label carrying the 5 and explicitly NOT the bare coverage line.
    const root = screen.getByTestId('dept-progress-R001');
    const cell = within(root).getByTestId('dept-progress-count-R001');
    expect(within(cell).getByTestId('dept-progress-subtree-only-R001')).toHaveTextContent('5');
    // The bare "X of Y" subtree chip is reserved for units WITH direct positions.
    expect(within(root).queryByTestId('dept-progress-subtree-R001')).toBeNull();
  });

  it('keeps the "X of Y ↳N" coverage + subtree chip for a unit WITH direct positions', () => {
    // OPS has direct positions AND extra descendants → the normal coverage line
    // plus the ↳ subtree chip (NOT the subtree-only label).
    countsState.data = new Map<string, DepartmentPositionCount>([
      ['d-ops', { directCount: 3, subtreeCount: 7 }],
    ]);
    render(
      renderWithProviders(
        <DepartmentPanelProgress
          projectId="p1"
          departments={departments}
          positions={positions}
          panels={[panel('pn1', 'o1')]}
        />,
      ),
    );
    const cell = screen.getByTestId('dept-progress-count-OPS');
    // Coverage figures present (1 of 3) ...
    expect(cell).toHaveTextContent('1');
    expect(cell).toHaveTextContent('3');
    // ... plus the subtree roll-up chip carrying 7 ...
    expect(within(cell).getByTestId('dept-progress-subtree-OPS')).toHaveTextContent('7');
    // ... and NOT the subtree-only label (that is reserved for 0 direct).
    expect(within(cell).queryByTestId('dept-progress-subtree-only-OPS')).toBeNull();
  });

  it('ignores ARCHIVED panels in the coverage count', () => {
    render(
      renderWithProviders(
        <DepartmentPanelProgress
          projectId="p1"
          departments={departments}
          positions={positions}
          panels={[{ ...panel('pn1', 'o1'), status: 'ARCHIVED' }]}
        />,
      ),
    );
    const ops = within(screen.getByTestId('dept-progress-OPS')).getByTestId('dept-progress-count-OPS');
    expect(ops).toHaveTextContent('0');
  });

  it('renders nothing when no department has subtree positions', () => {
    countsState.data = new Map();
    const { container } = render(
      renderWithProviders(
        <DepartmentPanelProgress projectId="p1" departments={departments} positions={[]} panels={[]} />,
      ),
    );
    expect(container.querySelector('[data-testid="dept-progress-list"]')).toBeNull();
  });
});
