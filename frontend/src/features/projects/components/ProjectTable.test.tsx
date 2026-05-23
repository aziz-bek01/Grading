import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ProjectTable } from './ProjectTable';
import { renderWithProviders } from '@/test/testUtils';
import type { Project } from '../types/projectTypes';

const rows: Project[] = [
  {
    id: 'p1',
    tenant_id: 't1',
    code: 'ACME-2026',
    name: { 'ru-RU': 'ACME 2026', 'en-US': 'ACME 2026' },
    status: 'ACTIVE',
    start_date: '2026-01-15',
    updated_at: '2026-05-10T00:00:00Z',
  },
  {
    id: 'p2',
    tenant_id: 't1',
    code: 'PILOT',
    name: { 'ru-RU': 'Пилот', 'en-US': 'Pilot' },
    status: 'APPROVED',
    updated_at: '2026-04-01T00:00:00Z',
  },
];

describe('<ProjectTable />', () => {
  it('renders one row per project with code + name', () => {
    render(renderWithProviders(<ProjectTable rows={rows} />));
    expect(screen.getByText('ACME-2026')).toBeInTheDocument();
    expect(screen.getByText('PILOT')).toBeInTheDocument();
  });

  it('renders status badges', () => {
    render(renderWithProviders(<ProjectTable rows={rows} />));
    // StatusBadge uses aria-label = translated status
    expect(screen.getAllByLabelText(/Утверждено|Активно|Approved|Active/i).length).toBeGreaterThanOrEqual(1);
  });

  it('shows empty state when no rows', () => {
    render(renderWithProviders(<ProjectTable rows={[]} />));
    expect(screen.getByText(/проектов ещё нет|no projects/i)).toBeInTheDocument();
  });
});
