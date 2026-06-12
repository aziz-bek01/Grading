import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, within } from '@testing-library/react';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import { OpenPanelDialog } from './OpenPanelDialog';
import type { Methodology } from '@/features/methodology/types';
import type { Department } from '@/features/organization/types/organizationTypes';
import type { Position } from '@/features/positions/types/positionTypes';
import type { Panel } from '../../panelTypes';

// ----------------------------------------------------------------------------
// The wizard fetches its own data through these hooks; we mock them so the
// component tests are deterministic (mirrors the codebase's hook-mock pattern).
// usePositions is called twice: once for the all-project count map (no
// departmentId) and once for the department cut (with departmentId) — the mock
// honours the departmentId param so we can assert the "narrowing" behaviour.
// ----------------------------------------------------------------------------

const departments: Department[] = [
  {
    id: 'dep-ops',
    project_id: 'proj-1',
    parent_id: null,
    code: 'OPS',
    name_i18n: { 'ru-RU': 'Операции', 'en-US': 'Operations' },
    type: 'DEPARTMENT',
    status: 'ACTIVE',
  },
  {
    id: 'dep-hr',
    project_id: 'proj-1',
    parent_id: null,
    code: 'HR',
    name_i18n: { 'ru-RU': 'HR', 'en-US': 'HR' },
    type: 'DEPARTMENT',
    status: 'ACTIVE',
  },
];

const allPositions: Position[] = [
  // OPS — 5 positions, one already paneled for v-1.
  { id: 'p-ops-1', project_id: 'proj-1', department_id: 'dep-ops', code: 'OPS-1', title_i18n: { 'en-US': 'Ops 1' }, status: 'ACTIVE' },
  { id: 'p-ops-2', project_id: 'proj-1', department_id: 'dep-ops', code: 'OPS-2', title_i18n: { 'en-US': 'Ops 2' }, status: 'ACTIVE' },
  { id: 'p-ops-3', project_id: 'proj-1', department_id: 'dep-ops', code: 'OPS-3', title_i18n: { 'en-US': 'Ops 3' }, status: 'ACTIVE' },
  { id: 'p-ops-4', project_id: 'proj-1', department_id: 'dep-ops', code: 'OPS-4', title_i18n: { 'en-US': 'Ops 4' }, status: 'ACTIVE' },
  { id: 'p-ops-5', project_id: 'proj-1', department_id: 'dep-ops', code: 'OPS-5', title_i18n: { 'en-US': 'Ops 5' }, status: 'ACTIVE' },
  // HR — 3 positions.
  { id: 'p-hr-1', project_id: 'proj-1', department_id: 'dep-hr', code: 'HR-1', title_i18n: { 'en-US': 'HR 1' }, status: 'ACTIVE' },
  { id: 'p-hr-2', project_id: 'proj-1', department_id: 'dep-hr', code: 'HR-2', title_i18n: { 'en-US': 'HR 2' }, status: 'ACTIVE' },
  { id: 'p-hr-3', project_id: 'proj-1', department_id: 'dep-hr', code: 'HR-3', title_i18n: { 'en-US': 'HR 3' }, status: 'ACTIVE' },
];

// p-ops-1 already has an active panel for v-1 → disabled + marked in Step 2.
const panels: Panel[] = [
  {
    id: 'panel-existing',
    project_id: 'proj-1',
    position_id: 'p-ops-1',
    position_title_i18n: { 'en-US': 'Ops 1' },
    methodology_version_id: 'v-1',
    status: 'COLLECTING',
    min_evaluators: 3,
    evaluator_count: 3,
    completed_count: 0,
    created_at: '2026-05-01T00:00:00Z',
  },
];

const methodologies: Methodology[] = [
  {
    id: 'm-1',
    project_id: 'proj-1',
    code: 'M1',
    name_i18n: { 'ru-RU': 'Методология 1', 'en-US': 'Methodology 1' },
    methodology_type: 'CLASSIC_8_FACTOR',
    status: 'ACTIVE',
    active_version_id: 'v-1',
    active_version_number: 1,
  } as Methodology,
];

let usePositionsCalls: Array<Record<string, unknown> | null> = [];
const suggestionState = { data: undefined as unknown, isError: false };

vi.mock('@/features/organization/hooks/useDepartmentTree', () => ({
  useDepartmentTree: () => ({ data: departments, isLoading: false, isError: false }),
}));

vi.mock('@/features/positions/hooks/usePositions', () => ({
  usePositions: (params: Record<string, unknown> | null) => {
    usePositionsCalls.push(params);
    if (!params) return { data: undefined, isLoading: false, isError: false };
    const deptId = params.departmentId as string | undefined;
    const items = deptId
      ? allPositions.filter((p) => p.department_id === deptId)
      : allPositions;
    return { data: { items }, isLoading: false, isError: false };
  },
}));

vi.mock('@/features/evaluation/hooks/usePanels', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../hooks/usePanels')>();
  return {
    ...actual,
    usePanels: () => ({ data: { items: panels }, isLoading: false, isError: false }),
    useRosterSuggestions: () => ({
      data: suggestionState.data,
      isError: suggestionState.isError,
      isLoading: false,
    }),
  };
});

vi.mock('@/features/users-access/hooks/useUsers', () => ({
  useUsers: () => ({
    data: {
      items: [
        { id: 'u1', full_name: 'Evaluator One', status: 'ACTIVE' },
        { id: 'u2', full_name: 'Evaluator Two', status: 'ACTIVE' },
        { id: 'u3', full_name: 'Evaluator Three', status: 'ACTIVE' },
        { id: 'u4', full_name: 'Evaluator Four', status: 'ACTIVE' },
        { id: 'u5', full_name: 'Evaluator Five', status: 'ACTIVE' },
        { id: 'dd-1', full_name: 'Suggested Director', status: 'ACTIVE' },
      ],
    },
    isLoading: false,
    isError: false,
  }),
}));

function setup(
  onConfirm = vi.fn().mockResolvedValue({ created: 1, failed: [] }),
  extra: Partial<React.ComponentProps<typeof OpenPanelDialog>> = {},
) {
  const onCopyRosterToNext = vi.fn();
  render(
    renderWithProviders(
      <OpenPanelDialog
        open
        projectId="proj-1"
        methodologies={methodologies}
        defaultVersionId="v-1"
        onConfirm={onConfirm}
        onCopyRosterToNext={onCopyRosterToNext}
        onClose={vi.fn()}
        {...extra}
      />,
    ),
  );
  return { onConfirm, onCopyRosterToNext };
}

/** Advance Step 1 → Step 2 by selecting a department then clicking Next. */
function gotoStep2(deptCode = 'OPS') {
  fireEvent.click(screen.getByTestId(`dept-select-${deptCode}`));
  fireEvent.click(screen.getByTestId('wizard-next'));
}

/** Fill the three mandatory roster seats with distinct users. */
function fillTrio() {
  fireEvent.change(screen.getByTestId('open-panel-picker-0'), { target: { value: 'u1' } });
  fireEvent.change(screen.getByTestId('open-panel-picker-1'), { target: { value: 'u2' } });
  fireEvent.change(screen.getByTestId('open-panel-picker-2'), { target: { value: 'u3' } });
}

describe('<OpenPanelDialog /> — dept-first wizard', () => {
  beforeEach(() => {
    signIn('super-admin');
    usePositionsCalls = [];
    suggestionState.data = undefined;
    suggestionState.isError = false;
    try {
      window.localStorage.clear();
    } catch {
      /* ignore */
    }
  });
  afterEach(() => {
    signOut();
    vi.restoreAllMocks();
  });

  it('Step 1 shows a department tree (not a flat position dropdown) and requires a selection to advance', () => {
    setup();
    expect(screen.getByTestId('wizard-step-1')).toBeInTheDocument();
    expect(screen.queryByTestId('open-panel-position-select')).not.toBeInTheDocument();
    expect(screen.getByTestId('dept-select-OPS')).toBeInTheDocument();
    expect(screen.getByTestId('dept-select-HR')).toBeInTheDocument();
    // Next disabled until a department is chosen.
    expect((screen.getByTestId('wizard-next') as HTMLButtonElement).disabled).toBe(true);
    fireEvent.click(screen.getByTestId('dept-select-OPS'));
    expect((screen.getByTestId('wizard-next') as HTMLButtonElement).disabled).toBe(false);
  });

  it('Step 1 shows per-department position count + already-paneled count badges', () => {
    setup();
    // OPS has 5 positions, 1 already paneled for v-1.
    const opsCoverage = screen.getByTestId('dept-coverage-OPS');
    expect(opsCoverage).toHaveTextContent('5');
    expect(screen.getByTestId('dept-paneled-OPS')).toBeInTheDocument();
  });

  it('Step 2 fetches ONLY the chosen department positions (server-filtered, no full scan)', () => {
    setup();
    gotoStep2('OPS');
    // The department cut was requested with departmentId=dep-ops.
    expect(
      usePositionsCalls.some((c) => c?.departmentId === 'dep-ops'),
    ).toBe(true);
    // Only OPS rows render (HR rows absent).
    expect(screen.getByTestId('wizard-position-row-OPS-2')).toBeInTheDocument();
    expect(screen.queryByTestId('wizard-position-row-HR-1')).not.toBeInTheDocument();
  });

  it('Step 2 DISABLES + marks already-paneled positions (visible, not hidden)', () => {
    setup();
    gotoStep2('OPS');
    // p-ops-1 is already paneled for v-1 → row present, checkbox disabled, marked.
    const row = screen.getByTestId('wizard-position-row-OPS-1');
    expect(row).toBeInTheDocument();
    expect((screen.getByTestId('wizard-position-check-OPS-1') as HTMLInputElement).disabled).toBe(true);
    expect(screen.getByTestId('wizard-position-paneled-OPS-1')).toBeInTheDocument();
  });

  it('select-all selects only the NOT-already-paneled positions', () => {
    setup();
    gotoStep2('OPS');
    fireEvent.click(screen.getByTestId('wizard-select-all'));
    // 4 selectable (OPS-2..5); OPS-1 stays unchecked (disabled/paneled).
    expect((screen.getByTestId('wizard-position-check-OPS-2') as HTMLInputElement).checked).toBe(true);
    expect((screen.getByTestId('wizard-position-check-OPS-5') as HTMLInputElement).checked).toBe(true);
    expect((screen.getByTestId('wizard-position-check-OPS-1') as HTMLInputElement).checked).toBe(false);
  });

  it('supports 4 external seats (1 mandatory + 3 additional) and caps adding beyond 4', () => {
    setup();
    gotoStep2('OPS');
    fireEvent.click(screen.getByTestId('wizard-select-all'));
    fireEvent.click(screen.getByTestId('wizard-next'));
    // Add 3 ADDITIONAL externals → 4 external seats total.
    fireEvent.click(screen.getByTestId('open-panel-add-evaluator'));
    fireEvent.click(screen.getByTestId('open-panel-add-evaluator'));
    fireEvent.click(screen.getByTestId('open-panel-add-evaluator'));
    expect(screen.getAllByTestId('panel-role-chip-ADDITIONAL')).toHaveLength(3);
    // The add button is now capped (disabled).
    expect((screen.getByTestId('open-panel-add-evaluator') as HTMLButtonElement).disabled).toBe(true);
  });

  it('keeps Confirm disabled until the mandatory trio is filled (min-3 UI mirror)', () => {
    setup();
    gotoStep2('OPS');
    fireEvent.click(screen.getByTestId('wizard-select-all'));
    fireEvent.click(screen.getByTestId('wizard-next'));
    const confirm = screen.getByTestId('open-panel-confirm') as HTMLButtonElement;
    expect(confirm.disabled).toBe(true);
    fireEvent.change(screen.getByTestId('open-panel-picker-0'), { target: { value: 'u1' } });
    fireEvent.change(screen.getByTestId('open-panel-picker-1'), { target: { value: 'u2' } });
    expect(confirm.disabled).toBe(true); // only 2 of 3
    fireEvent.change(screen.getByTestId('open-panel-picker-2'), { target: { value: 'u3' } });
    expect(confirm.disabled).toBe(false);
  });

  it('Confirm fires exactly ONE bulk call with the version, all selected positions and the shared roster', async () => {
    const onConfirm = vi.fn().mockResolvedValue({ created: 4, failed: [] });
    setup(onConfirm);
    gotoStep2('OPS');
    fireEvent.click(screen.getByTestId('wizard-select-all'));
    fireEvent.click(screen.getByTestId('wizard-next'));
    fillTrio();
    fireEvent.click(screen.getByTestId('open-panel-confirm'));
    await screen.findByTestId('open-panel-result');

    expect(onConfirm).toHaveBeenCalledTimes(1);
    const [versionId, positionIds, roster] = onConfirm.mock.calls[0];
    expect(versionId).toBe('v-1');
    // 4 selectable OPS positions (OPS-1 is already paneled → excluded).
    expect([...positionIds].sort()).toEqual(['p-ops-2', 'p-ops-3', 'p-ops-4', 'p-ops-5']);
    // The wizard passes PanelEvaluatorDraft[] (role); the page maps role →
    // evaluator_role for the wire.
    expect(roster.map((r: { role: string }) => r.role)).toEqual([
      'HR_DIRECTOR',
      'DEPARTMENT_DIRECTOR',
      'EXTERNAL_EXPERT',
    ]);
  });

  it('surfaces per-position failures in the result block, including ROSTER_PARTIAL seat failures', async () => {
    const onConfirm = vi.fn().mockResolvedValue({
      created: 3,
      failed: [
        { position_id: 'p-ops-2', error_code: 'ALREADY_EXISTS', message: 'dup' },
        {
          position_id: 'p-ops-3',
          error_code: 'ROSTER_PARTIAL',
          message: 'seat failed',
          seat_failures: [
            { evaluator_role: 'EXTERNAL_EXPERT', evaluator_user_id: 'u3', reason: 'withdrawn' },
          ],
        },
      ],
    });
    setup(onConfirm);
    gotoStep2('OPS');
    fireEvent.click(screen.getByTestId('wizard-select-all'));
    fireEvent.click(screen.getByTestId('wizard-next'));
    fillTrio();
    fireEvent.click(screen.getByTestId('open-panel-confirm'));

    const result = await screen.findByTestId('open-panel-result');
    expect(within(result).getByTestId('open-panel-fail-p-ops-2')).toBeInTheDocument();
    const partial = within(result).getByTestId('open-panel-fail-p-ops-3');
    expect(partial).toHaveTextContent('withdrawn');
  });

  it('on partial failure drops succeeded + roster-partial rows, keeping only fully-failed rows for retry', async () => {
    const onConfirm = vi.fn().mockResolvedValue({
      created: 3,
      failed: [{ position_id: 'p-ops-2', error_code: 'ALREADY_EXISTS', message: 'dup' }],
    });
    setup(onConfirm);
    gotoStep2('OPS');
    fireEvent.click(screen.getByTestId('wizard-select-all'));
    fireEvent.click(screen.getByTestId('wizard-next'));
    fillTrio();
    fireEvent.click(screen.getByTestId('open-panel-confirm'));
    await screen.findByTestId('open-panel-result');
    // selected_count is only shown on step 2 — go back to inspect retained selection.
    fireEvent.click(screen.getByTestId('wizard-back'));
    expect((screen.getByTestId('wizard-position-check-OPS-2') as HTMLInputElement).checked).toBe(true);
    expect((screen.getByTestId('wizard-position-check-OPS-4') as HTMLInputElement).checked).toBe(false);
  });

  it('pre-fills the DEPT_DIRECTOR seat from the advisory suggestion (editable)', () => {
    suggestionState.data = {
      department_id: 'dep-ops',
      dept_director_candidates: [{ user_id: 'dd-1', full_name: 'Suggested Director' }],
    };
    setup();
    gotoStep2('OPS');
    fireEvent.click(screen.getByTestId('wizard-select-all'));
    fireEvent.click(screen.getByTestId('wizard-next'));
    // Seat 1 is DEPARTMENT_DIRECTOR — pre-filled with the suggestion.
    expect((screen.getByTestId('open-panel-picker-1') as HTMLSelectElement).value).toBe('dd-1');
    expect(screen.getByTestId('open-panel-suggested-1')).toBeInTheDocument();
    // Still editable — pick a different user.
    fireEvent.change(screen.getByTestId('open-panel-picker-1'), { target: { value: 'u2' } });
    expect((screen.getByTestId('open-panel-picker-1') as HTMLSelectElement).value).toBe('u2');
  });

  it('a suggestion-fetch error leaves the DEPT_DIRECTOR seat empty + editable (not blocked)', () => {
    suggestionState.isError = true;
    setup();
    gotoStep2('OPS');
    fireEvent.click(screen.getByTestId('wizard-select-all'));
    fireEvent.click(screen.getByTestId('wizard-next'));
    expect(screen.getByTestId('wizard-suggestion-error')).toBeInTheDocument();
    expect((screen.getByTestId('open-panel-picker-1') as HTMLSelectElement).value).toBe('');
  });

  it('shows a fully-paneled empty state for a department whose positions are all paneled', () => {
    // Panel every HR position for v-1.
    panels.push(
      { id: 'pn-hr-1', project_id: 'proj-1', position_id: 'p-hr-1', position_title_i18n: {}, methodology_version_id: 'v-1', status: 'COLLECTING', min_evaluators: 3, evaluator_count: 0, completed_count: 0, created_at: 'x' },
      { id: 'pn-hr-2', project_id: 'proj-1', position_id: 'p-hr-2', position_title_i18n: {}, methodology_version_id: 'v-1', status: 'COLLECTING', min_evaluators: 3, evaluator_count: 0, completed_count: 0, created_at: 'x' },
      { id: 'pn-hr-3', project_id: 'proj-1', position_id: 'p-hr-3', position_title_i18n: {}, methodology_version_id: 'v-1', status: 'COLLECTING', min_evaluators: 3, evaluator_count: 0, completed_count: 0, created_at: 'x' },
    );
    try {
      setup();
      gotoStep2('HR');
      expect(screen.getByTestId('wizard-positions-fully-paneled')).toBeInTheDocument();
    } finally {
      panels.splice(1); // restore to just panel-existing
    }
  });

  it('copy-roster keeps HR + externals, clears DEPT_DIRECTOR, and fires the copy callback', async () => {
    const onConfirm = vi.fn().mockResolvedValue({ created: 4, failed: [{ position_id: 'p-ops-2', error_code: 'ALREADY_EXISTS', message: 'x' }] });
    const { onCopyRosterToNext } = setup(onConfirm);
    gotoStep2('OPS');
    fireEvent.click(screen.getByTestId('wizard-select-all'));
    fireEvent.click(screen.getByTestId('wizard-next'));
    fillTrio();
    fireEvent.click(screen.getByTestId('open-panel-confirm'));
    await screen.findByTestId('open-panel-result');
    fireEvent.click(screen.getByTestId('open-panel-copy-roster'));

    expect(onCopyRosterToNext).toHaveBeenCalledTimes(1);
    const seed = onCopyRosterToNext.mock.calls[0][0];
    const byRole = Object.fromEntries(
      seed.rows.map((r: { evaluator_role?: string; role: string; evaluator_user_id: string | null }) => [r.role, r.evaluator_user_id]),
    );
    expect(byRole.HR_DIRECTOR).toBe('u1');
    expect(byRole.EXTERNAL_EXPERT).toBe('u3');
    expect(byRole.DEPARTMENT_DIRECTOR).toBe(null);
  });

  it('seeds the roster from rosterSeed (copy-roster reopen) — HR + externals retained, dept director empty', () => {
    setup(undefined, {
      rosterSeed: {
        rows: [
          { role: 'HR_DIRECTOR', evaluator_user_id: 'u1', evaluator_name: 'Evaluator One' },
          { role: 'DEPARTMENT_DIRECTOR', evaluator_user_id: null, evaluator_name: null },
          { role: 'EXTERNAL_EXPERT', evaluator_user_id: 'u3', evaluator_name: 'Evaluator Three' },
        ],
      },
    });
    gotoStep2('OPS');
    fireEvent.click(screen.getByTestId('wizard-select-all'));
    fireEvent.click(screen.getByTestId('wizard-next'));
    expect((screen.getByTestId('open-panel-picker-0') as HTMLSelectElement).value).toBe('u1');
    expect((screen.getByTestId('open-panel-picker-1') as HTMLSelectElement).value).toBe('');
    expect((screen.getByTestId('open-panel-picker-2') as HTMLSelectElement).value).toBe('u3');
  });
});
