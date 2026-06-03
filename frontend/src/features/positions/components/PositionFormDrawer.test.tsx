import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { PositionFormDrawer } from './PositionFormDrawer';
import { renderWithProviders } from '@/test/testUtils';
import type { Department } from '@/features/organization/types/organizationTypes';

const departments: Department[] = [
  { id: 'd1', project_id: 'p', parent_id: null, code: 'IT', name_i18n: { 'ru-RU': 'ИТ' }, type: 'DIVISION', status: 'ACTIVE', updated_at: '' },
  { id: 'd2', project_id: 'p', parent_id: null, code: 'OLD', name_i18n: { 'ru-RU': 'Старое' }, type: 'UNIT', status: 'ARCHIVED', updated_at: '' },
];

describe('<PositionFormDrawer />', () => {
  it('lists only non-archived departments in selector', () => {
    render(
      renderWithProviders(
        <PositionFormDrawer
          open
          projectId="p"
          departments={departments}
          onClose={() => {}}
          onSubmit={vi.fn()}
        />,
      ),
    );
    const select = screen.getByTestId('pos-department-select') as HTMLSelectElement;
    const values = Array.from(select.options).map((o) => o.value);
    expect(values).toContain('d1');
    expect(values).not.toContain('d2');
  });

  it('requires department + primary-locale title before submit', async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    render(
      renderWithProviders(
        <PositionFormDrawer
          open
          projectId="p"
          departments={departments}
          onClose={() => {}}
          onSubmit={onSubmit}
        />,
      ),
    );
    await user.type(screen.getByTestId('pos-code'), 'SWE');
    await user.click(screen.getByRole('button', { name: /Сохранить|Save/i }));
    expect(onSubmit).not.toHaveBeenCalled();
  });
});
