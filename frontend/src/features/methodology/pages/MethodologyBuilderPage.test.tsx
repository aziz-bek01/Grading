import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { useSyncExternalStore } from 'react';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import { renderWithProviders, signIn, signInWithPermissions, signOut } from '@/test/testUtils';
import { PERMISSIONS } from '@/shared/types/permissions';
import { ApiError } from '@/shared/api/apiError';
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
    useRemoveFactor: () => ({ mutateAsync: removeFactorSpy }),
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
    // Level reorder — spies recorded on the shared captor so the test can assert
    // the COMPLETE re-indexed order payload was sent to the reorder endpoint.
    useReorderFactorLevels: () => ({ mutateAsync: reorderLevelSpy }),
    useApproveVersion: () => ({ mutateAsync: vi.fn() }),
    useLockVersion: () => ({ mutateAsync: vi.fn() }),
    useArchiveVersion: () => ({ mutateAsync: vi.fn() }),
    useCreateNewVersion: () => ({ mutateAsync: vi.fn() }),
    // Metadata edit — record the two distinct PATCH surfaces so the test asserts
    // both the container (type) and version (scoring_mode) patches were sent.
    useUpdateMethodology: () => ({ mutateAsync: updateMethodologySpy }),
    useUpdateMethodologyVersionMetadata: () => ({ mutateAsync: updateVersionMetadataSpy }),
  };
});

const reorderLevelSpy = vi.fn();
const updateMethodologySpy = vi.fn();
const updateVersionMetadataSpy = vi.fn();
const removeFactorSpy = vi.fn();

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
    reorderLevelSpy.mockReset();
    updateMethodologySpy.mockReset();
    updateVersionMetadataSpy.mockReset();
    removeFactorSpy.mockReset();
    removeFactorSpy.mockResolvedValue(undefined);
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
    // Read-only APPROVED requires a user WITHOUT METHODOLOGY_EDIT_APPROVED
    // (super-admin now gets the FE-1 approved-edit affordance instead).
    signInWithPermissions([
      PERMISSIONS.METHODOLOGY_READ,
      PERMISSIONS.METHODOLOGY_EDIT,
      PERMISSIONS.METHODOLOGY_APPROVE,
    ]);
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

  it('reorders a level via the down arrow (ISSUE 1a — arrows no longer no-op)', async () => {
    const user = userEvent.setup();
    (globalThis as { __methodology?: Methodology }).__methodology = baseMethodology;
    versionStore.version = {
      ...baseVersion,
      status: 'DRAFT',
      factors: [
        {
          ...baseVersion.factors[0],
          levels: [
            { id: 'lvl-a', factor_id: 'f-A', code: 'A', level_order: 0, points: 10, scale_value: 0, label_i18n: { 'ru-RU': 'Низкий' } },
            { id: 'lvl-b', factor_id: 'f-A', code: 'B', level_order: 1, points: 20, scale_value: 0, label_i18n: { 'ru-RU': 'Высокий' } },
          ],
        },
      ],
    };
    renderPage();

    await waitFor(() => expect(screen.getByTestId('factor-A-edit')).toBeInTheDocument());
    await user.click(screen.getByTestId('factor-A-edit'));
    await waitFor(() => expect(screen.getByTestId('level-row-A')).toBeInTheDocument());

    // Move the first level (A) DOWN.
    await user.click(screen.getByTestId('level-A-move-down'));

    // The reorder mutation fires with the COMPLETE re-indexed id order [B, A]
    // matching the backend ReorderRequest contract ({ ordered_ids: [...] }).
    await waitFor(() => expect(reorderLevelSpy).toHaveBeenCalledTimes(1));
    expect(reorderLevelSpy.mock.calls[0][0]).toEqual({
      ordered_ids: ['lvl-b', 'lvl-a'],
    });
  });

  it('edits methodology metadata: PATCHes container type + version scoring_mode (ISSUE 2)', async () => {
    const user = userEvent.setup();
    (globalThis as { __methodology?: Methodology }).__methodology = baseMethodology;
    versionStore.version = { ...baseVersion, status: 'DRAFT', scoring_mode: 'WEIGHTED_POINTS' };
    renderPage();

    await waitFor(() => expect(screen.getByTestId('action-edit-metadata')).toBeInTheDocument());
    await user.click(screen.getByTestId('action-edit-metadata'));

    // The builder-mode drawer exposes the type + scoring-mode selects.
    await waitFor(() => expect(screen.getByTestId('metadata-scoring-mode')).toBeInTheDocument());
    expect(screen.getByTestId('metadata-type')).toBeInTheDocument();

    // Switch scoring_mode → a change warning appears.
    await user.selectOptions(screen.getByTestId('metadata-scoring-mode'), 'DIRECT_POINTS');
    expect(screen.getByTestId('metadata-scoring-change-warning')).toBeInTheDocument();

    // Switch the container type too.
    await user.selectOptions(screen.getByTestId('metadata-type'), 'CUSTOM');

    // Save → both PATCH surfaces fire (container first, then version). Scope to
    // the drawer dialog so the header "Save as template" button isn't matched.
    const drawer = screen.getByRole('dialog');
    await user.click(within(drawer).getByRole('button', { name: /^(save|сохранить|saqlash|сақлаш)$/i }));

    await waitFor(() => expect(updateMethodologySpy).toHaveBeenCalledTimes(1));
    expect(updateMethodologySpy.mock.calls[0][0].methodology_type).toBe('CUSTOM');
    expect(updateVersionMetadataSpy).toHaveBeenCalledTimes(1);
    expect(updateVersionMetadataSpy.mock.calls[0][0].scoring_mode).toBe('DIRECT_POINTS');
  });

  it('hides the Edit metadata affordance for non-DRAFT versions (readOnly path)', async () => {
    // A non-super-admin (no METHODOLOGY_EDIT_APPROVED) keeps APPROVED read-only.
    signInWithPermissions([
      PERMISSIONS.METHODOLOGY_READ,
      PERMISSIONS.METHODOLOGY_EDIT,
    ]);
    (globalThis as { __methodology?: Methodology }).__methodology = baseMethodology;
    versionStore.version = { ...baseVersion, status: 'APPROVED', approved_at: '2026-04-12T10:00:00Z' };
    renderPage();
    await waitFor(() => expect(screen.getByTestId('locked-methodology-header')).toBeInTheDocument());
    expect(screen.queryByTestId('action-edit-metadata')).toBeNull();
  });

  // ── FE-1 — approved-version edit affordance ────────────────────────────────

  it('FE-1: super admin on APPROVED gets editable factor table + warning banner', async () => {
    // super-admin is signed in by default (holds METHODOLOGY_EDIT_APPROVED).
    (globalThis as { __methodology?: Methodology }).__methodology = baseMethodology;
    versionStore.version = { ...baseVersion, status: 'APPROVED', approved_at: '2026-04-12T10:00:00Z' };
    renderPage();

    await waitFor(() => expect(screen.getByTestId('approved-edit-banner')).toBeInTheDocument());
    // Factor edit controls are enabled (not the read-only label).
    expect(screen.getByTestId('factor-A-edit')).toBeInTheDocument();
    // The DRAFT-only lifecycle actions stay hidden on an APPROVED version.
    expect(screen.queryByTestId('action-approve')).toBeNull();
    expect(screen.queryByTestId('action-edit-metadata')).toBeNull();
  });

  it('FE-1: first edit on APPROVED is gated by a confirm dialog', async () => {
    const user = userEvent.setup();
    (globalThis as { __methodology?: Methodology }).__methodology = baseMethodology;
    versionStore.version = { ...baseVersion, status: 'APPROVED', approved_at: '2026-04-12T10:00:00Z' };
    renderPage();

    await waitFor(() => expect(screen.getByTestId('approved-edit-banner')).toBeInTheDocument());

    // Clicking edit does NOT open the editor immediately — a confirm intercepts.
    await user.click(screen.getByTestId('factor-A-edit'));
    const dialog = await screen.findByRole('dialog');
    expect(dialog).toBeInTheDocument();
    // The editor drawer is not open yet.
    expect(screen.queryByTestId('factor-level-editor')).toBeNull();

    // Acknowledge → the deferred edit action runs (the factor editor opens with
    // its level-editor section).
    await user.click(within(dialog).getByRole('button', { name: /understand|continue|продолж|давом/i }));
    await waitFor(() => expect(screen.getByTestId('factor-level-editor')).toBeInTheDocument());
  });

  it('FE-1: non-super-admin (METHODOLOGY_EDIT only) on APPROVED is read-only', async () => {
    signInWithPermissions([
      PERMISSIONS.METHODOLOGY_READ,
      PERMISSIONS.METHODOLOGY_EDIT,
    ]);
    (globalThis as { __methodology?: Methodology }).__methodology = baseMethodology;
    versionStore.version = { ...baseVersion, status: 'APPROVED', approved_at: '2026-04-12T10:00:00Z' };
    renderPage();

    await waitFor(() => expect(screen.getByTestId('locked-methodology-header')).toBeInTheDocument());
    expect(screen.queryByTestId('approved-edit-banner')).toBeNull();
    expect(screen.queryByTestId('factor-A-edit')).toBeNull();
  });

  it('FE-1: LOCKED stays read-only even for a super admin (no carve-out)', async () => {
    (globalThis as { __methodology?: Methodology }).__methodology = baseMethodology;
    versionStore.version = {
      ...baseVersion,
      status: 'LOCKED',
      approved_at: '2026-04-12T10:00:00Z',
      locked_at: '2026-04-12T10:00:00Z',
    };
    renderPage();
    await waitFor(() => expect(screen.getByTestId('locked-methodology-header')).toBeInTheDocument());
    expect(screen.queryByTestId('approved-edit-banner')).toBeNull();
    expect(screen.queryByTestId('factor-A-edit')).toBeNull();
  });

  // ── FE-2 — delete → deprecate UX ───────────────────────────────────────────

  it('FE-2: removing a referenced factor surfaces a non-alarming deprecate notice', async () => {
    const user = userEvent.setup();
    removeFactorSpy.mockRejectedValueOnce(
      new ApiError(409, { code: 'FACTOR_REFERENCED_BY_EVALUATIONS', message: 'referenced' }),
    );
    (globalThis as { __methodology?: Methodology }).__methodology = baseMethodology;
    versionStore.version = { ...baseVersion, status: 'APPROVED', approved_at: '2026-04-12T10:00:00Z' };
    renderPage();

    await waitFor(() => expect(screen.getByTestId('approved-edit-banner')).toBeInTheDocument());

    // First action is gated; acknowledge the confirm, then confirm the
    // remove itself (FE-049 — the remove action opens a second ConfirmDialog
    // instead of window.confirm).
    await user.click(screen.getByTestId('factor-A-remove'));
    const gateDialog = await screen.findByRole('dialog');
    await user.click(within(gateDialog).getByRole('button', { name: /understand|continue|продолж|давом/i }));

    const removeDialog = await screen.findByRole('dialog');
    await user.click(
      within(removeDialog).getByRole('button', { name: /delete|удалить|ўчириш|o.chirish/i }),
    );

    await waitFor(() => expect(screen.getByTestId('deprecate-notice')).toBeInTheDocument());
    expect(removeFactorSpy).toHaveBeenCalled();
  });

  it('FE-2: renders a deprecated badge on a soft-deprecated factor', async () => {
    (globalThis as { __methodology?: Methodology }).__methodology = baseMethodology;
    versionStore.version = {
      ...baseVersion,
      status: 'APPROVED',
      approved_at: '2026-04-12T10:00:00Z',
      factors: [
        { ...baseVersion.factors[0], deprecated_at: '2026-05-10T10:00:00Z', deprecated_by: 'u-1' },
      ],
    };
    renderPage();
    await waitFor(() =>
      expect(screen.getByTestId('factor-row-A').getAttribute('data-deprecated')).toBe('true'),
    );
  });
});
