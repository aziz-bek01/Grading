import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { DepartmentDrawer } from './DepartmentDrawer';
import { renderWithProviders } from '@/test/testUtils';
import type { Department } from '../types/organizationTypes';

const items: Department[] = [
  { id: 'hq', project_id: 'p', parent_id: null, code: 'HQ', name: { 'ru-RU': 'Штаб' }, type: 'BRANCH', status: 'ACTIVE', updated_at: '' },
  { id: 'fin', project_id: 'p', parent_id: 'hq', code: 'FIN', name: { 'ru-RU': 'Финансы' }, type: 'DEPARTMENT', status: 'ACTIVE', updated_at: '' },
  { id: 'treas', project_id: 'p', parent_id: 'fin', code: 'TR', name: { 'ru-RU': 'Казн' }, type: 'UNIT', status: 'ACTIVE', updated_at: '' },
];

describe('<DepartmentDrawer />', () => {
  it('parent selector excludes self and descendants when editing', () => {
    render(
      renderWithProviders(
        <DepartmentDrawer
          open
          projectId="p"
          items={items}
          initial={items[1]} // editing FIN
          onClose={() => {}}
          onSubmit={vi.fn()}
        />,
      ),
    );
    const select = screen.getByTestId('dep-parent-select') as HTMLSelectElement;
    const optionValues = Array.from(select.options).map((o) => o.value);
    // self and descendant excluded
    expect(optionValues).not.toContain('fin');
    expect(optionValues).not.toContain('treas');
    // ancestor allowed
    expect(optionValues).toContain('hq');
  });

  it('blocks submission when code is missing', async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    render(
      renderWithProviders(
        <DepartmentDrawer open projectId="p" items={items} onClose={() => {}} onSubmit={onSubmit} />,
      ),
    );
    await user.click(screen.getByRole('button', { name: /Сохранить|Save/i }));
    expect(onSubmit).not.toHaveBeenCalled();
  });
});
