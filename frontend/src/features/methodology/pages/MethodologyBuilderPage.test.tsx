import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { Route, Routes } from 'react-router-dom';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import { MethodologyBuilderPage } from './MethodologyBuilderPage';
import type { Methodology, MethodologyVersion } from '../types';

const baseMethodology: Methodology = {
  id: 'meth-1',
  project_id: 'proj-1',
  code: 'M1',
  name_i18n: { 'ru-RU': 'Тест методология', 'en-US': 'Test methodology' },
  methodology_type: 'CLASSIC_8_FACTOR',
  status: 'ACTIVE',
  latest_version_id: 'v-1',
  active_version_id: 'v-1',
  active_version_number: 1,
  active_version_status: 'DRAFT',
  created_at: '2026-02-01T10:00:00Z',
  updated_at: '2026-05-01T10:00:00Z',
};

const baseVersion: MethodologyVersion = {
  id: 'v-1',
  methodology_id: 'meth-1',
  project_id: 'proj-1',
  version_number: 1,
  status: 'DRAFT',
  scoring_mode: 'WEIGHTED_POINTS',
  target_total_points: 100,
  factors: [
    {
      id: 'f-A',
      methodology_version_id: 'v-1',
      code: 'A',
      name_i18n: { 'ru-RU': 'Фактор A' },
      weight: 100,
      max_points: 100,
      sort_order: 0,
      required: true,
      levels: [],
    },
  ],
  created_at: '2026-02-01T10:00:00Z',
  updated_at: '2026-05-01T10:00:00Z',
};

vi.mock('../hooks/useMethodology', async () => {
  const actual = await vi.importActual<typeof import('../hooks/useMethodology')>('../hooks/useMethodology');
  return {
    ...actual,
    useMethodology: () => ({ data: (globalThis as { __methodology?: Methodology }).__methodology, isLoading: false, error: null, refetch: vi.fn() }),
    useMethodologyVersion: () => ({ data: (globalThis as { __version?: MethodologyVersion }).__version, isLoading: false, error: null, refetch: vi.fn() }),
    useMethodologyVersions: () => ({ data: [], isLoading: false }),
    useAddFactor: () => ({ mutateAsync: vi.fn() }),
    useUpdateFactor: () => ({ mutateAsync: vi.fn() }),
    useRemoveFactor: () => ({ mutateAsync: vi.fn() }),
    useReorderFactors: () => ({ mutateAsync: vi.fn() }),
    useAddFactorLevel: () => ({ mutateAsync: vi.fn() }),
    useUpdateFactorLevel: () => ({ mutateAsync: vi.fn() }),
    useRemoveFactorLevel: () => ({ mutateAsync: vi.fn() }),
    useReorderFactorLevels: () => ({ mutateAsync: vi.fn() }),
    useApproveVersion: () => ({ mutateAsync: vi.fn() }),
    useLockVersion: () => ({ mutateAsync: vi.fn() }),
    useArchiveVersion: () => ({ mutateAsync: vi.fn() }),
    useCreateNewVersion: () => ({ mutateAsync: vi.fn() }),
  };
});

function renderPage() {
  return render(
    renderWithProviders(
      <Routes>
        <Route
          path="/app/projects/:projectId/methodology/:methodologyId/versions/:versionId/edit"
          element={<MethodologyBuilderPage />}
        />
      </Routes>,
      ['/app/projects/proj-1/methodology/meth-1/versions/v-1/edit'],
    ),
  );
}

describe('MethodologyBuilderPage', () => {
  beforeEach(() => signIn('super-admin'));
  afterEach(() => {
    signOut();
    delete (globalThis as { __methodology?: Methodology }).__methodology;
    delete (globalThis as { __version?: MethodologyVersion }).__version;
  });

  it('DRAFT shows Save/Approve/Archive actions + WeightSumVisualizer', async () => {
    (globalThis as { __methodology?: Methodology }).__methodology = baseMethodology;
    (globalThis as { __version?: MethodologyVersion }).__version = { ...baseVersion, status: 'DRAFT' };
    renderPage();
    await waitFor(() => expect(screen.getByTestId('methodology-header')).toBeInTheDocument());
    expect(screen.getByTestId('action-approve')).toBeInTheDocument();
    expect(screen.getByTestId('action-archive')).toBeInTheDocument();
    expect(screen.getByTestId('weight-sum-visualizer')).toBeInTheDocument();
    expect(screen.queryByTestId('locked-methodology-header')).toBeNull();
  });

  it('APPROVED shows LockedMethodologyHeader + read-only factor table', async () => {
    (globalThis as { __methodology?: Methodology }).__methodology = baseMethodology;
    (globalThis as { __version?: MethodologyVersion }).__version = {
      ...baseVersion,
      status: 'APPROVED',
      approved_at: '2026-04-12T10:00:00Z',
      approved_by: 'Approver',
    };
    renderPage();
    await waitFor(() => expect(screen.getByTestId('locked-methodology-header')).toBeInTheDocument());
    expect(screen.queryByTestId('action-approve')).toBeNull();
    expect(screen.queryByTestId('factor-A-edit')).toBeNull();
    expect(screen.getByTestId('locked-create-new-version')).toBeInTheDocument();
  });

  it('LOCKED shows lock icon + locked metadata + read-only', async () => {
    (globalThis as { __methodology?: Methodology }).__methodology = baseMethodology;
    (globalThis as { __version?: MethodologyVersion }).__version = {
      ...baseVersion,
      status: 'LOCKED',
      approved_at: '2026-04-12T10:00:00Z',
      approved_by: 'Approver',
      locked_at: '2026-04-12T10:00:00Z',
      locked_by: 'Approver',
    };
    renderPage();
    await waitFor(() => expect(screen.getByTestId('locked-methodology-header')).toBeInTheDocument());
    expect(screen.getByTestId('locked-methodology-header').getAttribute('data-status')).toBe('LOCKED');
    expect(screen.getByTestId('locked-actor-time').textContent).toContain('Approver');
    // Weight visualizer hidden for non-DRAFT.
    expect(screen.queryByTestId('weight-sum-visualizer')).toBeNull();
  });
});
