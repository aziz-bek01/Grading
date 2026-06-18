import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { PositionTable } from './PositionTable';
import { renderWithProviders } from '@/test/testUtils';
import { signIn } from '@/test/testUtils';
import type { Position } from '../types/positionTypes';
import type { Department } from '@/features/organization/types/organizationTypes';

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
