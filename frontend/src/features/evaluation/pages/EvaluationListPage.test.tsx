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
];

const bulkCreateSpy = vi.fn();
const deleteSpy = vi.fn();

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
  });
  afterEach(() => signOut());

  it('FE-1: renders a DEPARTMENT column with the localized department name', async () => {
    renderPage();
    await waitFor(() =>
      expect(screen.getByText('Финансовый аналитик')).toBeInTheDocument(),
    );
    // Column header present.
    expect(screen.getByText('Подразделение')).toBeInTheDocument();
    // pos-1 → dep-fin → "Финансы"; pos-2 → dep-it → "ИТ".
    expect(screen.getByText('Финансы')).toBeInTheDocument();
    expect(screen.getByText('ИТ')).toBeInTheDocument();
  });

  it('FE-3: delete action is shown ONLY for DRAFT rows (with EVALUATION_EDIT)', async () => {
    renderPage();
    await waitFor(() =>
      expect(screen.getByText('Финансовый аналитик')).toBeInTheDocument(),
    );
    // eval-1 is DRAFT → delete visible; eval-2 is SUBMITTED → hidden.
    expect(screen.getByTestId('delete-evaluation-eval-1')).toBeInTheDocument();
    expect(
      screen.queryByTestId('delete-evaluation-eval-2'),
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
});
