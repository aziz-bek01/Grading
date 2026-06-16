import { afterEach, describe, it, expect, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Routes, Route } from 'react-router-dom';
import { ProjectTable } from './ProjectTable';
import { renderWithProviders, signIn, signInWithPermissions, signOut } from '@/test/testUtils';
import { PERMISSIONS } from '@/shared/types/permissions';
import type { Project } from '../types/projectTypes';

afterEach(() => signOut());

const rows: Project[] = [
  {
    id: 'p1',
    tenant_id: 't1',
    code: 'ACME-2026',
    name_i18n: { 'ru-RU': 'ACME 2026', 'en-US': 'ACME 2026' },
    status: 'ACTIVE',
    start_date: '2026-01-15',
    updated_at: '2026-05-10T00:00:00Z',
  },
  {
    id: 'p2',
    tenant_id: 't1',
    code: 'PILOT',
    name_i18n: { 'ru-RU': 'Пилот', 'en-US': 'Pilot' },
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

/** Action-column behaviour (edit / archive). */
function renderTable(
  data: Project[],
  handlers: { onEdit?: (p: Project) => void; onArchive?: (p: Project) => void } = {},
) {
  return renderWithProviders(
    <Routes>
      <Route
        path="/"
        element={<ProjectTable rows={data} onEdit={handlers.onEdit} onArchive={handlers.onArchive} />}
      />
      <Route path="/app/projects/:projectId/workspace" element={<div>WORKSPACE</div>} />
    </Routes>,
    ['/'],
  );
}

describe('<ProjectTable /> actions column', () => {
  it('renders Edit + Archive icon buttons with PROJECT_EDIT', async () => {
    signIn('super-admin');
    render(renderTable(rows, { onEdit: vi.fn(), onArchive: vi.fn() }));
    expect(await screen.findByTestId('project-edit-p1')).toBeInTheDocument();
    expect(screen.getByTestId('project-archive-p1')).toBeInTheDocument();
  });

  it('hides the actions when the user lacks PROJECT_EDIT', async () => {
    // Only PROJECT_READ — the actions column is mounted but the gate suppresses
    // the buttons (no DOM presence).
    signInWithPermissions([PERMISSIONS.PROJECT_READ]);
    render(renderTable(rows, { onEdit: vi.fn(), onArchive: vi.fn() }));
    expect(await screen.findByText('ACME-2026')).toBeInTheDocument();
    expect(screen.queryByTestId('project-edit-p1')).not.toBeInTheDocument();
    expect(screen.queryByTestId('project-archive-p1')).not.toBeInTheDocument();
  });

  it('Edit click does NOT navigate to the workspace (stopPropagation)', async () => {
    signIn('super-admin');
    const onEdit = vi.fn();
    const user = userEvent.setup();
    render(renderTable(rows, { onEdit, onArchive: vi.fn() }));

    await user.click(await screen.findByTestId('project-edit-p1'));
    expect(onEdit).toHaveBeenCalledWith(rows[0]);
    // Row navigation would have replaced the table with "WORKSPACE".
    expect(screen.queryByText('WORKSPACE')).not.toBeInTheDocument();
    expect(screen.getByText('ACME-2026')).toBeInTheDocument();
  });

  it('row click (outside the action buttons) DOES navigate to the workspace', async () => {
    signIn('super-admin');
    const user = userEvent.setup();
    render(renderTable(rows, { onEdit: vi.fn(), onArchive: vi.fn() }));
    await user.click(await screen.findByText('ACME-2026'));
    expect(screen.getByText('WORKSPACE')).toBeInTheDocument();
  });

  it.each(['ARCHIVED', 'LOCKED'] as const)('suppresses actions for %s projects', async (status) => {
    signIn('super-admin');
    render(renderTable([{ ...rows[0], status }], { onEdit: vi.fn(), onArchive: vi.fn() }));
    expect(await screen.findByTestId('project-actions-locked-p1')).toBeInTheDocument();
    expect(screen.queryByTestId('project-edit-p1')).not.toBeInTheDocument();
    expect(screen.queryByTestId('project-archive-p1')).not.toBeInTheDocument();
  });

  it('does not render an actions column when no handlers are passed', async () => {
    signIn('super-admin');
    render(renderTable(rows));
    const row = (await screen.findByText('ACME-2026')).closest('tr')!;
    expect(within(row).queryByTestId('project-edit-p1')).not.toBeInTheDocument();
  });
});
