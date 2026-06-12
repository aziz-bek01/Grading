import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import { DepartmentPanelProgress } from './DepartmentPanelProgress';
import type { Department } from '@/features/organization/types/organizationTypes';
import type { Position } from '@/features/positions/types/positionTypes';
import type { Panel } from '../../panelTypes';

const departments: Department[] = [
  { id: 'd-ops', project_id: 'p1', parent_id: null, code: 'OPS', name_i18n: { 'en-US': 'Operations' }, type: 'DEPARTMENT', status: 'ACTIVE' },
  { id: 'd-hr', project_id: 'p1', parent_id: null, code: 'HR', name_i18n: { 'en-US': 'HR' }, type: 'DEPARTMENT', status: 'ACTIVE' },
];

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

describe('<DepartmentPanelProgress /> (FE-7)', () => {
  beforeEach(() => signIn('super-admin'));
  afterEach(() => signOut());

  it('shows per-department X of Y coverage derived from already-loaded data', () => {
    render(
      renderWithProviders(
        <DepartmentPanelProgress
          departments={departments}
          positions={positions}
          panels={[panel('pn1', 'o1'), panel('pn2', 'o2')]}
        />,
      ),
    );
    // OPS: 2 of 3 paneled.
    expect(screen.getByTestId('dept-progress-count-OPS')).toHaveTextContent('2');
    expect(screen.getByTestId('dept-progress-count-OPS')).toHaveTextContent('3');
    // HR: 0 of 2 paneled.
    const hr = screen.getByTestId('dept-progress-count-HR');
    expect(hr).toHaveTextContent('0');
    expect(hr).toHaveTextContent('2');
  });

  it('ignores ARCHIVED panels in the coverage count', () => {
    render(
      renderWithProviders(
        <DepartmentPanelProgress
          departments={departments}
          positions={positions}
          panels={[{ ...panel('pn1', 'o1'), status: 'ARCHIVED' }]}
        />,
      ),
    );
    // The single ARCHIVED panel does not count → OPS 0 of 3.
    const ops = within(screen.getByTestId('dept-progress-OPS')).getByTestId('dept-progress-count-OPS');
    expect(ops).toHaveTextContent('0');
  });

  it('renders nothing when there are no departments with positions', () => {
    const { container } = render(
      renderWithProviders(
        <DepartmentPanelProgress departments={[]} positions={[]} panels={[]} />,
      ),
    );
    expect(container.querySelector('[data-testid="dept-progress-list"]')).toBeNull();
  });
});
