import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { PositionTable } from './PositionTable';
import { renderWithProviders } from '@/test/testUtils';
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
];

describe('<PositionTable />', () => {
  it('renders title and department code', () => {
    render(renderWithProviders(<PositionTable projectId="p" rows={rows} departments={departments} />));
    expect(screen.getByText('Разработчик')).toBeInTheDocument();
    expect(screen.getByText(/IT · ИТ/)).toBeInTheDocument();
  });

  it('shows status badge', () => {
    render(renderWithProviders(<PositionTable projectId="p" rows={rows} departments={departments} />));
    expect(screen.getAllByLabelText(/Активно|Active/i).length).toBeGreaterThanOrEqual(1);
  });

  it('renders empty state when no rows', () => {
    render(renderWithProviders(<PositionTable projectId="p" rows={[]} departments={departments} />));
    expect(screen.getByText(/Позиций пока нет|No positions yet/i)).toBeInTheDocument();
  });
});
