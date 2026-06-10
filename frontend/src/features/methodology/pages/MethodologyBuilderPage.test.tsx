import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { useSyncExternalStore } from 'react';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import { MethodologyBuilderPage } from './MethodologyBuilderPage';
import type { Methodology, MethodologyVersion, FactorLevel } from '../types';

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

/**
 * Tiny reactive store so the mocked `useMethodologyVersion` re-renders when a
 * mutation appends a level — mirroring the real query-invalidation refetch.
 * This is the missing coverage that let the stale-`editorFactor` bug ship: the
 * server persisted the level but the OPEN drawer kept rendering the snapshot.
 */
type VersionStore = {
  version?: MethodologyVersion;
  subs: Set<() => void>;
  set(next: MethodologyVersion): void;
};
const versionStore: VersionStore = {
  version: undefined,
  subs: new Set(),
  set(next) {
    this.version = next;
    this.subs.forEach((fn) => fn());
  },
};
function useVersionFromStore() {
  return useSyncExternalStore(
    (cb) => {
      versionStore.subs.add(cb);
      return () => versionStore.subs.delete(cb);
    },
    () => versionStore.version,
  );
}

vi.mock('../hooks/useMethodology', async () => {
  const actual = await vi.importActual<typeof import('../hooks/useMethodology')>('../hooks/useMethodology');
  return {
    ...actual,
    useMethodology: () => ({ data: (globalThis as { __methodology?: Methodology }).__methodology, isLoading: false, error: null, refetch: vi.fn() }),
    useMethodologyVersion: () => ({ data: useVersionFromStore(), isLoading: false, error: null, refetch: vi.fn() }),
    useMethodologyVersions: () => ({ data: [], isLoading: false }),
    useAddFactor: () => ({ mutateAsync: vi.fn() }),
    useUpdateFactor: () => ({ mutateAsync: vi.fn() }),
    useRemoveFactor: () => ({ mutateAsync: vi.fn() }),
    useReorderFactors: () => ({ mutateAsync: vi.fn() }),
    // Persists the level into the store (like the real backend) so a refetch
    // would reveal it — the page must then re-derive editorFactor and show it.
    useAddFactorLevel: () => ({
      mutateAsync: vi.fn(async (vars: { factorId: string; payload: Omit<FactorLevel, 'id' | 'factor_id'> }) => {
        const current = versionStore.version!;
        const nextLevel: FactorLevel = {
          id: 'lvl-new',
          factor_id: vars.factorId,
          code: vars.payload.code,
          level_order: vars.payload.level_order ?? 0,
          points: vars.payload.points,
          scale_value: vars.payload.scale_value,
          label_i18n: vars.payload.label_i18n,
          description_i18n: vars.payload.description_i18n,
        };
        versionStore.set({
          ...current,
          factors: current.factors.map((f) =>
            f.id === vars.factorId ? { ...f, levels: [...f.levels, nextLevel] } : f,
          ),
        });
        return nextLevel;
      }),
    }),
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
  beforeEach(() => {
    signIn('super-admin');
    versionStore.version = undefined;
  });
  afterEach(() => {
    signOut();
    delete (globalThis as { __methodology?: Methodology }).__methodology;
    versionStore.version = undefined;
    versionStore.subs.clear();
  });

  it('DRAFT shows Save/Approve/Archive actions + WeightSumVisualizer', async () => {
    (globalThis as { __methodology?: Methodology }).__methodology = baseMethodology;
    versionStore.version = { ...baseVersion, status: 'DRAFT' };
    renderPage();
    await waitFor(() => expect(screen.getByTestId('methodology-header')).toBeInTheDocument());
    expect(screen.getByTestId('action-approve')).toBeInTheDocument();
    expect(screen.getByTestId('action-archive')).toBeInTheDocument();
    expect(screen.getByTestId('weight-sum-visualizer')).toBeInTheDocument();
    expect(screen.queryByTestId('locked-methodology-header')).toBeNull();
  });

  it('APPROVED shows LockedMethodologyHeader + read-only factor table', async () => {
    (globalThis as { __methodology?: Methodology }).__methodology = baseMethodology;
    versionStore.version = {
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
    versionStore.version = {
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

  it('persists a new level and REFLECTS it in the open factor drawer (EPIC-A regression)', async () => {
    const user = userEvent.setup();
    (globalThis as { __methodology?: Methodology }).__methodology = baseMethodology;
    versionStore.version = { ...baseVersion, status: 'DRAFT' };
    renderPage();

    await waitFor(() => expect(screen.getByTestId('factor-A-edit')).toBeInTheDocument());

    // Open the factor drawer for factor A.
    await user.click(screen.getByTestId('factor-A-edit'));
    await waitFor(() => expect(screen.getByTestId('factor-level-editor')).toBeInTheDocument());

    // The new-level form: fill code + ru-RU label, then add. The form has two
    // localized-tab groups (label + description) — the first ru-RU input is the
    // label, which is the only field the add button requires.
    const newForm = screen.getByTestId('level-new-form');
    const codeInput = within(newForm).getByPlaceholderText('A');
    await user.type(codeInput, 'L1');
    const ruInput = within(newForm).getAllByTestId('locale-input-ru-RU')[0];
    await user.type(ruInput, 'Уровень 1');

    await user.click(screen.getByTestId('level-add'));

    // The persisted level must now appear inside the STILL-OPEN drawer — this is
    // the assertion that fails against the stale-snapshot bug.
    await waitFor(() => expect(screen.getByTestId('level-row-L1')).toBeInTheDocument());
    expect(screen.getByText('Уровень 1')).toBeInTheDocument();
  });
});
