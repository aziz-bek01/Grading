import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { PositionTable } from './PositionTable';
import { renderWithProviders } from '@/test/testUtils';
import { signIn } from '@/test/testUtils';
import type { Position } from '../types/positionTypes';
import type { Department } from '@/features/organization/types/organizationTypes';
import type { JobProfileStatusByPosition } from '@/features/job-profiles/types';

const departments: Department[] = [
  { id: 'd1', project_id: 'p', parent_id: null, code: 'IT', name_i18n: { 'ru-RU': 'ИТ' }, type: 'DIVISION', status: 'ACTIVE', updated_at: '' },
];

const rows: Position[] = [
  {
    id: 'pos1',
    project_id: 'p',
    department_id: 'd1',
    code: 'SWE',
    title_i18n: { 'ru-RU': 'Разработчик', 'en-US': 'Software Engineer' },
    function: 'Technology',
    job_family: 'IT',
    job_level: 'L5',
    status: 'ACTIVE',
    updated_at: '2026-05-01T00:00:00Z',
  },
  {
    id: 'pos2',
    project_id: 'p',
    department_id: 'd1',
    code: 'QA',
    title_i18n: { 'ru-RU': 'Тестировщик', 'en-US': 'QA Engineer' },
    status: 'ARCHIVED',
    updated_at: '2026-04-01T00:00:00Z',
  },
];

describe('<PositionTable />', () => {
  it('renders title and department code', () => {
    render(renderWithProviders(<PositionTable projectId="p" rows={rows} departments={departments} />));
    expect(screen.getByText('Разработчик')).toBeInTheDocument();
    // Both rows share the same department, so multiple matches are expected
    expect(screen.getAllByText(/IT · ИТ/).length).toBeGreaterThanOrEqual(1);
  });

  it('shows status badge', () => {
    render(renderWithProviders(<PositionTable projectId="p" rows={rows} departments={departments} />));
    expect(screen.getAllByLabelText(/Активно|Active/i).length).toBeGreaterThanOrEqual(1);
  });

  it('renders empty state when no rows', () => {
    render(renderWithProviders(<PositionTable projectId="p" rows={[]} departments={departments} />));
    expect(screen.getByText(/Позиций пока нет|No positions yet/i)).toBeInTheDocument();
  });

  it('calls onEdit when Edit button is clicked on an active row', async () => {
    signIn('super-admin');
    const onEdit = vi.fn();
    const user = userEvent.setup();
    render(
      renderWithProviders(
        <PositionTable
          projectId="p"
          rows={rows}
          departments={departments}
          onEdit={onEdit}
          onArchive={vi.fn()}
        />,
      ),
    );
    await user.click(screen.getByTestId('position-edit-pos1'));
    expect(onEdit).toHaveBeenCalledWith(rows[0]);
  });

  it('calls onArchive when Archive button is clicked on an active row', async () => {
    signIn('super-admin');
    const onArchive = vi.fn();
    const user = userEvent.setup();
    render(
      renderWithProviders(
        <PositionTable
          projectId="p"
          rows={rows}
          departments={departments}
          onEdit={vi.fn()}
          onArchive={onArchive}
        />,
      ),
    );
    await user.click(screen.getByTestId('position-archive-pos1'));
    expect(onArchive).toHaveBeenCalledWith(rows[0]);
  });

  // PAGE-2: the "Job profile" column is entirely opt-in — omitted by default
  // (all tests above pass no `jobProfileByPositionId`), so it must not
  // appear unless the caller explicitly supplies the map.
  it('does not render a "Job profile" column when jobProfileByPositionId is omitted', () => {
    render(renderWithProviders(<PositionTable projectId="p" rows={rows} departments={departments} />));
    expect(screen.queryByText(/Должностной профиль|Job profile/i)).not.toBeInTheDocument();
  });

  it('renders job profile status per row when jobProfileByPositionId is provided', () => {
    const approvedProfile: JobProfileStatusByPosition = {
      position_id: 'pos1',
      status: 'APPROVED',
      job_profile_id: 'jp-1',
      revision_number: 1,
    };
    const jobProfileByPositionId = new Map<string, JobProfileStatusByPosition | null | undefined>([
      ['pos1', approvedProfile], // has an active profile
      ['pos2', null], // confirmed no active profile
    ]);
    render(
      renderWithProviders(
        <PositionTable
          projectId="p"
          rows={rows}
          departments={departments}
          jobProfileByPositionId={jobProfileByPositionId}
        />,
      ),
    );
    expect(screen.getByText(/Должностной профиль|Job profile/i)).toBeInTheDocument();
    expect(screen.getByText(/Утверждено|Approved/i)).toBeInTheDocument();
    expect(screen.getByText(/Отсутствует|Missing/i)).toBeInTheDocument();
  });

  it('renders a neutral loading placeholder for rows missing from jobProfileByPositionId', () => {
    // pos2 intentionally absent from the map — still "loading" from the
    // caller's perspective; must never be guessed as present or missing.
    const jobProfileByPositionId = new Map<string, JobProfileStatusByPosition | null | undefined>([
      ['pos1', null],
    ]);
    render(
      renderWithProviders(
        <PositionTable
          projectId="p"
          rows={rows}
          departments={departments}
          jobProfileByPositionId={jobProfileByPositionId}
        />,
      ),
    );
    expect(screen.getByTestId('position-job-profile-loading-pos2')).toBeInTheDocument();
  });

  // QA (Medium): a failed bulk `GET /job-profiles/statuses` request must be
  // distinguishable from "still loading" — both leave every id `undefined`
  // in the map, so the table needs the sibling `jobProfileStatusError` flag
  // to tell them apart instead of showing an indefinite "…" placeholder.
  it('renders a distinguishable "load error" affordance (not the loading placeholder) when jobProfileStatusError is true', () => {
    // Every id is STILL undefined in the map — exactly what the hook returns
    // once the bulk request has failed (the 3-state contract never guesses).
    const jobProfileByPositionId = new Map<string, JobProfileStatusByPosition | null | undefined>();
    render(
      renderWithProviders(
        <PositionTable
          projectId="p"
          rows={rows}
          departments={departments}
          jobProfileByPositionId={jobProfileByPositionId}
          jobProfileStatusError
        />,
      ),
    );
    expect(screen.getByTestId('position-job-profile-error-pos1')).toBeInTheDocument();
    expect(screen.getByTestId('position-job-profile-error-pos2')).toBeInTheDocument();
    // The neutral loading placeholder must NOT appear once the request has
    // actually errored — that would be indistinguishable from "still loading".
    expect(screen.queryByTestId('position-job-profile-loading-pos1')).not.toBeInTheDocument();
    expect(screen.queryByTestId('position-job-profile-loading-pos2')).not.toBeInTheDocument();
    expect(screen.getAllByText(/Ошибка загрузки|Load error/i).length).toBeGreaterThanOrEqual(1);
  });

  it('shows dash instead of action buttons for ARCHIVED rows', () => {
    signIn('super-admin');
    render(
      renderWithProviders(
        <PositionTable
          projectId="p"
          rows={rows}
          departments={departments}
          onEdit={vi.fn()}
          onArchive={vi.fn()}
        />,
      ),
    );
    // The archived row (pos2) should show the locked dash, not action buttons
    expect(screen.getByTestId('position-actions-locked-pos2')).toBeInTheDocument();
    expect(screen.queryByTestId('position-edit-pos2')).not.toBeInTheDocument();
    expect(screen.queryByTestId('position-archive-pos2')).not.toBeInTheDocument();
  });
});
