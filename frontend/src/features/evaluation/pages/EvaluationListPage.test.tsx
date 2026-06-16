import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { Route, Routes } from 'react-router-dom';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import { EvaluationListPage } from './EvaluationListPage';
import type { Evaluation } from '../types';
import type { Methodology } from '@/features/methodology/types';
import type { Position } from '@/features/positions/types/positionTypes';
import type { Department } from '@/features/organization/types/organizationTypes';

// ---------- Fixtures ----------

const positions: Position[] = [
  {
    id: 'pos-1',
    project_id: 'proj-1',
    department_id: 'dep-fin',
    code: 'FIN-AN',
    title_i18n: { 'ru-RU': 'Финансовый аналитик', 'en-US': 'Financial Analyst' },
    status: 'ACTIVE',
  },
  {
    id: 'pos-2',
    project_id: 'proj-1',
    department_id: 'dep-it',
    code: 'IT-DEV',
    title_i18n: { 'ru-RU': 'Разработчик', 'en-US': 'Developer' },
    status: 'ACTIVE',
  },
  {
    id: 'pos-3',
    project_id: 'proj-1',
    department_id: 'dep-fin',
    code: 'FIN-MGR',
    title_i18n: { 'ru-RU': 'Финансовый менеджер', 'en-US': 'Finance Manager' },
    status: 'ACTIVE',
  },
  {
    id: 'pos-4',
    project_id: 'proj-1',
    department_id: 'dep-it',
    code: 'IT-LEAD',
    title_i18n: { 'ru-RU': 'Тимлид', 'en-US': 'Tech Lead' },
    status: 'ACTIVE',
  },
  // pos-5..pos-7 back the COMPLETE / APPROVED / LOCKED / ARCHIVED delete-visibility
  // matrix. Each carries a non-archived eval so none becomes an Add-positions
  // candidate — keeping the FE-2 candidate test (pos-3 is the sole candidate) stable.
  {
    id: 'pos-5',
    project_id: 'proj-1',
    department_id: 'dep-it',
    code: 'IT-QA',
    title_i18n: { 'ru-RU': 'Тестировщик', 'en-US': 'QA Engineer' },
    status: 'ACTIVE',
  },
  {
    id: 'pos-6',
    project_id: 'proj-1',
    department_id: 'dep-fin',
    code: 'FIN-CTR',
    title_i18n: { 'ru-RU': 'Контролёр', 'en-US': 'Controller' },
    status: 'ACTIVE',
  },
  {
    id: 'pos-7',
    project_id: 'proj-1',
    department_id: 'dep-fin',
    code: 'FIN-TR',
    title_i18n: { 'ru-RU': 'Казначей', 'en-US': 'Treasurer' },
    status: 'ACTIVE',
  },
];

const departments: Department[] = [
  {
    id: 'dep-fin',
    project_id: 'proj-1',
    parent_id: null,
    code: 'FIN',
    name_i18n: { 'ru-RU': 'Финансы', 'en-US': 'Finance' },
    type: 'DEPARTMENT',
    status: 'ACTIVE',
  },
  {
    id: 'dep-it',
    project_id: 'proj-1',
    parent_id: null,
    code: 'IT',
    name_i18n: { 'ru-RU': 'ИТ', 'en-US': 'IT' },
    type: 'DEPARTMENT',
    status: 'ACTIVE',
  },
];

const methodologies: Methodology[] = [
  {
    id: 'm-1',
    project_id: 'proj-1',
    code: 'M1',
    name_i18n: { 'ru-RU': 'CFO Финансы', 'en-US': 'CFO Finance' },
    methodology_type: 'CLASSIC_8_FACTOR',
    status: 'ACTIVE',
    active_version_id: 'v-1',
    active_version_number: 1,
    active_version_status: 'APPROVED',
  },
];

// pos-1 is already evaluated (DRAFT) for v-1 → not a candidate for v-1.
const evaluations: Evaluation[] = [
  {
    id: 'eval-1',
    project_id: 'proj-1',
    position_id: 'pos-1',
    methodology_version_id: 'v-1',
    status: 'DRAFT',
    displayed_total_score: 0,
  },
  {
    id: 'eval-2',
    project_id: 'proj-1',
    position_id: 'pos-2',
    methodology_version_id: 'v-1',
    status: 'SUBMITTED',
    displayed_total_score: 42.5,
    submitted_at: '2026-05-01T10:00:00Z',
  },
  // INCOMPLETE is now a deletable (pre-submission) status — mirrors the owner's
  // report that "Неполная" rows must offer "Удалить".
  {
    id: 'eval-3',
    project_id: 'proj-1',
    position_id: 'pos-4',
    methodology_version_id: 'v-1',
    status: 'INCOMPLETE',
    displayed_total_score: 12,
  },
  // COMPLETE — the third pre-submission status — is also deletable.
  {
    id: 'eval-4',
    project_id: 'proj-1',
    position_id: 'pos-5',
    methodology_version_id: 'v-1',
    status: 'COMPLETE',
    displayed_total_score: 55,
  },
  // Post-submission statuses keep the Archive path → delete must be HIDDEN.
  {
    id: 'eval-5',
    project_id: 'proj-1',
    position_id: 'pos-6',
    methodology_version_id: 'v-1',
    status: 'APPROVED',
    displayed_total_score: 61,
  },
  {
    id: 'eval-6',
    project_id: 'proj-1',
    position_id: 'pos-7',
    methodology_version_id: 'v-1',
    status: 'LOCKED',
    displayed_total_score: 70,
  },
  // ARCHIVED — shares pos-5 (which already carries the non-archived COMPLETE
  // eval-4) so it adds an ARCHIVED row to the delete-visibility matrix WITHOUT
  // turning any position into a fresh Add-positions candidate.
  {
    id: 'eval-7',
    project_id: 'proj-1',
    position_id: 'pos-5',
    methodology_version_id: 'v-1',
    status: 'ARCHIVED',
    displayed_total_score: 5,
  },
];

const bulkCreateSpy = vi.fn();
const deleteSpy = vi.fn();
// Panel commission bulk-create — captured so we can assert the page sets
// start_evaluations:true on the payload (create AND start the commission).
const bulkCreatePanelsSpy = vi.fn();

vi.mock('../hooks/useEvaluation', async () => {
  const actual = await vi.importActual<typeof import('../hooks/useEvaluation')>(
    '../hooks/useEvaluation',
  );
  return {
    ...actual,
    useEvaluations: () => ({ data: { items: evaluations }, isLoading: false }),
    useBulkCreateEvaluations: () => ({ mutateAsync: bulkCreateSpy }),
    useDeleteEvaluation: () => ({ mutateAsync: deleteSpy }),
  };
});

vi.mock('@/features/positions/hooks/usePositions', () => ({
  usePositions: () => ({ data: { items: positions }, isLoading: false }),
}));

vi.mock('@/features/organization/hooks/useDepartmentTree', () => ({
  useDepartmentTree: () => ({ data: departments, isLoading: false }),
}));

vi.mock('@/features/methodology/hooks/useMethodology', () => ({
  useMethodologies: () => ({ data: { items: methodologies }, isLoading: false }),
}));

// ---- Panel-commission wizard hooks (only exercised by the commission test) ----
// usePanels is mocked here (empty coverage) AND useBulkCreatePanels is replaced
// with a spy so we assert the page-built payload — the real fetcher never runs.
vi.mock('../hooks/usePanels', () => ({
  usePanels: () => ({ data: { items: [] }, isLoading: false, isError: false }),
  useBulkCreatePanels: () => ({ mutateAsync: bulkCreatePanelsSpy }),
  useRosterSuggestions: () => ({ data: undefined, isError: false, isLoading: false }),
}));

vi.mock('@/features/organization/hooks/useDepartmentPositionCounts', () => ({
  useDepartmentPositionCounts: () => ({
    data: new Map([
      ['dep-fin', { directCount: 4, subtreeCount: 4 }],
      ['dep-it', { directCount: 3, subtreeCount: 3 }],
    ]),
    isLoading: false,
    isError: false,
  }),
}));

vi.mock('@/features/users-access/hooks/useUsers', () => ({
  useUsers: () => ({
    data: {
      items: [
        { id: 'u1', full_name: 'Evaluator One', status: 'ACTIVE' },
        { id: 'u2', full_name: 'Evaluator Two', status: 'ACTIVE' },
        { id: 'u3', full_name: 'Evaluator Three', status: 'ACTIVE' },
      ],
    },
    isLoading: false,
    isError: false,
  }),
}));

function renderPage() {
  return render(
    renderWithProviders(
      <Routes>
        <Route
          path="/app/projects/:projectId/evaluation"
          element={<EvaluationListPage />}
        />
      </Routes>,
      ['/app/projects/proj-1/evaluation'],
    ),
  );
}

describe('EvaluationListPage — Item 1 (department / add / delete)', () => {
  beforeEach(() => {
    signIn('super-admin');
    bulkCreateSpy.mockReset();
    deleteSpy.mockReset();
    bulkCreatePanelsSpy.mockReset();
  });
  afterEach(() => signOut());

  it('FE-1: renders a DEPARTMENT column with the localized department name', async () => {
    renderPage();
    await waitFor(() =>
      expect(screen.getByText('Финансовый аналитик')).toBeInTheDocument(),
    );
    // Column header present.
    expect(screen.getByText('Подразделение')).toBeInTheDocument();
    // Several rows resolve to dep-fin ("Финансы") and dep-it ("ИТ") — assert the
    // localized names render at least once each (exact counts are fixture churn).
    expect(screen.getAllByText('Финансы').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('ИТ').length).toBeGreaterThanOrEqual(1);
  });

  it('FE-3: delete action is shown for EVERY pre-submission row and hidden for post-submission (with EVALUATION_EDIT)', async () => {
    renderPage();
    await waitFor(() =>
      expect(screen.getByText('Финансовый аналитик')).toBeInTheDocument(),
    );
    // Pre-submission set (mirrors EvaluationStatus.isDeletable on the BE):
    // eval-1 DRAFT, eval-3 INCOMPLETE, eval-4 COMPLETE → delete VISIBLE.
    expect(screen.getByTestId('delete-evaluation-eval-1')).toBeInTheDocument(); // DRAFT
    expect(screen.getByTestId('delete-evaluation-eval-3')).toBeInTheDocument(); // INCOMPLETE
    expect(screen.getByTestId('delete-evaluation-eval-4')).toBeInTheDocument(); // COMPLETE
    // Post-submission set keeps the Archive path → delete HIDDEN.
    expect(
      screen.queryByTestId('delete-evaluation-eval-2'), // SUBMITTED
    ).not.toBeInTheDocument();
    expect(
      screen.queryByTestId('delete-evaluation-eval-5'), // APPROVED
    ).not.toBeInTheDocument();
    expect(
      screen.queryByTestId('delete-evaluation-eval-6'), // LOCKED
    ).not.toBeInTheDocument();
    expect(
      screen.queryByTestId('delete-evaluation-eval-7'), // ARCHIVED
    ).not.toBeInTheDocument();
  });

  it('FE-3: delete is hidden entirely for a viewer (no EVALUATION_EDIT)', async () => {
    signOut();
    signIn('viewer');
    renderPage();
    await waitFor(() =>
      expect(screen.getByText('Финансовый аналитик')).toBeInTheDocument(),
    );
    expect(
      screen.queryByTestId('delete-evaluation-eval-1'),
    ).not.toBeInTheDocument();
  });

  it('FE-3: confirming delete with a >=5-char reason calls deleteEvaluation', async () => {
    deleteSpy.mockResolvedValue(undefined);
    renderPage();
    await waitFor(() =>
      expect(screen.getByTestId('delete-evaluation-eval-1')).toBeInTheDocument(),
    );
    fireEvent.click(screen.getByTestId('delete-evaluation-eval-1'));
    // The confirm button lives inside the dialog — scope the query to it so it
    // doesn't collide with the row-level "Удалить" action button.
    const dialog = screen.getByRole('dialog');
    const reason = screen.getByTestId('confirm-dialog-reason');
    const confirmBtn = () =>
      within(dialog).getByRole('button', { name: /Удалить|Delete/i });
    // Short reason: confirm disabled — clicking does nothing.
    fireEvent.change(reason, { target: { value: 'dup' } });
    fireEvent.click(confirmBtn());
    expect(deleteSpy).not.toHaveBeenCalled();
    // Valid reason → confirm fires the delete with id + reason.
    fireEvent.change(reason, { target: { value: 'Duplicate draft' } });
    fireEvent.click(confirmBtn());
    await waitFor(() =>
      expect(deleteSpy).toHaveBeenCalledWith({
        id: 'eval-1',
        reason: 'Duplicate draft',
      }),
    );
  });

  it('FE-3: deleting an INCOMPLETE row fires the SAME deleteEvaluation call', async () => {
    deleteSpy.mockResolvedValue(undefined);
    renderPage();
    await waitFor(() =>
      expect(screen.getByTestId('delete-evaluation-eval-3')).toBeInTheDocument(),
    );
    // eval-3 is INCOMPLETE — newly deletable. The confirm + reason flow and the
    // mutation payload are identical to the DRAFT path (only the visibility
    // guard widened).
    fireEvent.click(screen.getByTestId('delete-evaluation-eval-3'));
    const dialog = screen.getByRole('dialog');
    fireEvent.change(screen.getByTestId('confirm-dialog-reason'), {
      target: { value: 'Incomplete duplicate' },
    });
    fireEvent.click(within(dialog).getByRole('button', { name: /Удалить|Delete/i }));
    await waitFor(() =>
      expect(deleteSpy).toHaveBeenCalledWith({
        id: 'eval-3',
        reason: 'Incomplete duplicate',
      }),
    );
  });

  it('FE-2: Add-positions dialog shows ONLY candidates without a non-archived eval for the version', async () => {
    renderPage();
    fireEvent.click(screen.getByTestId('add-positions-open'));
    await waitFor(() =>
      expect(screen.getByTestId('add-positions-list')).toBeInTheDocument(),
    );
    // pos-1 (DRAFT eval for v-1) and pos-2 (SUBMITTED eval for v-1) are already
    // evaluated → NOT candidates. Only pos-3 (FIN-MGR) remains.
    expect(screen.getByTestId('add-positions-row-FIN-MGR')).toBeInTheDocument();
    expect(
      screen.queryByTestId('add-positions-row-FIN-AN'),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByTestId('add-positions-row-IT-DEV'),
    ).not.toBeInTheDocument();
    // Candidate row carries the department name (FE-1 map reuse).
    expect(screen.getByTestId('add-positions-row-FIN-MGR')).toHaveTextContent(
      'Финансы',
    );
  });

  it('FE-2: confirming a selection calls bulkCreate once and surfaces partial failures inline', async () => {
    bulkCreateSpy.mockResolvedValue({
      created: 0,
      failed: [
        {
          position_id: 'pos-3',
          error_code: 'ALREADY_EXISTS',
          message: 'dup',
        },
      ],
    });
    renderPage();
    fireEvent.click(screen.getByTestId('add-positions-open'));
    await waitFor(() =>
      expect(screen.getByTestId('add-positions-row-FIN-MGR')).toBeInTheDocument(),
    );
    fireEvent.click(screen.getByTestId('add-positions-check-FIN-MGR'));
    fireEvent.click(screen.getByTestId('add-positions-confirm'));
    await waitFor(() => expect(bulkCreateSpy).toHaveBeenCalledTimes(1));
    expect(bulkCreateSpy).toHaveBeenCalledWith({
      items: [{ position_id: 'pos-3', methodology_version_id: 'v-1' }],
    });
    // Partial-fail summary is shown inline (does not close the dialog).
    await waitFor(() =>
      expect(screen.getByTestId('add-positions-result')).toBeInTheDocument(),
    );
    expect(screen.getByTestId('add-positions-result')).toHaveTextContent(
      'ALREADY_EXISTS',
    );
  });

  it('FE-2: Add-positions CTA hidden for a viewer (no EVALUATION_EDIT)', async () => {
    signOut();
    signIn('viewer');
    renderPage();
    await waitFor(() =>
      expect(screen.getByText('Финансовый аналитик')).toBeInTheDocument(),
    );
    expect(screen.queryByTestId('add-positions-open')).not.toBeInTheDocument();
  });

  it('Commission: the panel wizard payload sets start_evaluations:true (create AND start)', async () => {
    bulkCreatePanelsSpy.mockResolvedValue({ created: 1, failed: [] });
    renderPage();
    // Open the dept-first panel wizard.
    fireEvent.click(screen.getByTestId('open-panel-cta'));
    // Step 1 — pick a department, advance.
    await waitFor(() =>
      expect(screen.getByTestId('dept-select-IT')).toBeInTheDocument(),
    );
    fireEvent.click(screen.getByTestId('dept-select-IT'));
    fireEvent.click(screen.getByTestId('wizard-next'));
    // Step 2 — select all candidate positions, advance.
    await waitFor(() =>
      expect(screen.getByTestId('wizard-select-all')).toBeInTheDocument(),
    );
    fireEvent.click(screen.getByTestId('wizard-select-all'));
    fireEvent.click(screen.getByTestId('wizard-next'));
    // Step 3 — fill the mandatory trio, then confirm.
    await waitFor(() =>
      expect(screen.getByTestId('open-panel-picker-0')).toBeInTheDocument(),
    );
    fireEvent.change(screen.getByTestId('open-panel-picker-0'), { target: { value: 'u1' } });
    fireEvent.change(screen.getByTestId('open-panel-picker-1'), { target: { value: 'u2' } });
    fireEvent.change(screen.getByTestId('open-panel-picker-2'), { target: { value: 'u3' } });
    fireEvent.click(screen.getByTestId('open-panel-confirm'));

    await waitFor(() => expect(bulkCreatePanelsSpy).toHaveBeenCalledTimes(1));
    const payload = bulkCreatePanelsSpy.mock.calls[0][0];
    expect(payload.start_evaluations).toBe(true);
    expect(payload.methodology_version_id).toBe('v-1');
    // No tenant_id is ever assembled into the payload.
    expect(payload).not.toHaveProperty('tenant_id');
  });

  // PD-4 — the two header CTAs (expert-commission panel vs. single-position
  // sheets) carry a one-line helper + per-button tooltips so they are not
  // confused.
  it('PD-4: renders a CTA helper line and per-button tooltips distinguishing the two flows', async () => {
    renderPage();
    await waitFor(() =>
      expect(screen.getByText('Финансовый аналитик')).toBeInTheDocument(),
    );
    expect(screen.getByTestId('evaluation-cta-helper')).toBeInTheDocument();
    expect(screen.getByTestId('open-panel-cta')).toHaveAttribute('title');
    expect(screen.getByTestId('add-positions-open')).toHaveAttribute('title');
  });
});
