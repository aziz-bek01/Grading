import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { DepartmentTree } from './DepartmentTree';
import { renderWithProviders } from '@/test/testUtils';
import type { Department } from '../types/organizationTypes';

const items: Department[] = [
  { id: 'hq', project_id: 'p', parent_id: null, code: 'HQ', name: { 'ru-RU': 'Штаб' }, type: 'BRANCH', status: 'ACTIVE', updated_at: '' },
  { id: 'fin', project_id: 'p', parent_id: 'hq', code: 'FIN', name: { 'ru-RU': 'Финансы' }, type: 'DEPARTMENT', status: 'ACTIVE', updated_at: '' },
  { id: 'old', project_id: 'p', parent_id: 'hq', code: 'OLD', name: { 'ru-RU': 'Старое' }, type: 'UNIT', status: 'ARCHIVED', updated_at: '' },
];

describe('<DepartmentTree />', () => {
  it('hides archived items by default', () => {
    render(renderWithProviders(<DepartmentTree items={items} selectedId={null} onSelect={() => {}} showArchived={false} query="" />));
    expect(screen.queryByText('OLD')).not.toBeInTheDocument();
    expect(screen.getByText('FIN')).toBeInTheDocument();
  });

  it('shows archived when toggled on', () => {
    render(renderWithProviders(<DepartmentTree items={items} selectedId={null} onSelect={() => {}} showArchived query="" />));
    expect(screen.getByText('OLD')).toBeInTheDocument();
  });

  it('filters by query (case insensitive)', () => {
    render(renderWithProviders(<DepartmentTree items={items} selectedId={null} onSelect={() => {}} showArchived={false} query="fin" />));
    expect(screen.getByText('FIN')).toBeInTheDocument();
    expect(screen.queryByText('HQ')).not.toBeInTheDocument();
  });

  it('selects on click', async () => {
    const onSelect = vi.fn();
    const user = userEvent.setup();
    render(renderWithProviders(<DepartmentTree items={items} selectedId={null} onSelect={onSelect} showArchived={false} query="" />));
    await user.click(screen.getByText('FIN'));
    expect(onSelect).toHaveBeenCalledWith('fin');
  });
});
