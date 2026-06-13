import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { render, screen, within, fireEvent } from '@testing-library/react';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import {
  DepartmentSingleSelectTree,
  type DepartmentCoverage,
} from './DepartmentSingleSelectTree';
import type { Department } from '../types/organizationTypes';

const departments: Department[] = [
  { id: 'd-root', project_id: 'p1', parent_id: null, code: 'R001', name_i18n: { 'en-US': 'Head office' }, type: 'BRANCH', status: 'ACTIVE' },
  { id: 'd-ops', project_id: 'p1', parent_id: 'd-root', code: 'OPS', name_i18n: { 'en-US': 'Operations' }, type: 'DEPARTMENT', status: 'ACTIVE' },
];

// T4 — coverage map: the parent R001 has NO direct positions but its subtree
// rolls up 7; OPS has 4 direct (= subtree, a leaf).
const coverage: Record<string, DepartmentCoverage> = {
  'd-root': { positionCount: 0, paneledCount: 0, subtreeCount: 7 },
  'd-ops': { positionCount: 4, paneledCount: 0, subtreeCount: 4 },
};

describe('<DepartmentSingleSelectTree /> coverage badges (T4 PD-1)', () => {
  beforeEach(() => signIn('super-admin'));
  afterEach(() => {
    signOut();
    vi.restoreAllMocks();
  });

  function setup(onSelect = vi.fn()) {
    render(
      renderWithProviders(
        <DepartmentSingleSelectTree
          items={departments}
          selectedId={null}
          onSelect={onSelect}
          countsOf={(id) => coverage[id]}
        />,
      ),
    );
    return { onSelect };
  }

  it('renders the DIRECT position count as the primary badge', () => {
    setup();
    const opsCoverage = screen.getByTestId('dept-coverage-OPS');
    expect(opsCoverage).toHaveTextContent('4');
  });

  it('renders a secondary ↳ subtree figure for a parent that only holds descendants (R001 ↳ 7, not 0)', () => {
    setup();
    const rootCoverage = screen.getByTestId('dept-coverage-R001');
    // Direct count chip reads 0…
    expect(rootCoverage).toHaveTextContent('0');
    // …and the ↳ subtree figure reads 7 (rolled up), so the parent is not 0.
    expect(within(rootCoverage).getByTestId('dept-subtree-R001')).toHaveTextContent('7');
  });

  it('omits the subtree figure for a leaf where subtree == direct', () => {
    setup();
    // OPS subtree (4) equals its direct count (4) → no secondary ↳ figure.
    expect(screen.queryByTestId('dept-subtree-OPS')).not.toBeInTheDocument();
  });

  it('selecting a department fires onSelect with its id', () => {
    const { onSelect } = setup();
    fireEvent.click(screen.getByTestId('dept-select-OPS'));
    expect(onSelect).toHaveBeenCalledWith('d-ops');
  });
});
