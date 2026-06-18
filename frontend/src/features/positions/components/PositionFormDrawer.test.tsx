import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { PositionFormDrawer } from './PositionFormDrawer';
import { renderWithProviders } from '@/test/testUtils';
import type { Department } from '@/features/organization/types/organizationTypes';
import type { Position } from '../types/positionTypes';

const departments: Department[] = [
  { id: 'd1', project_id: 'p', parent_id: null, code: 'IT', name_i18n: { 'ru-RU': 'ИТ' }, type: 'DIVISION', status: 'ACTIVE', updated_at: '' },
  { id: 'd2', project_id: 'p', parent_id: null, code: 'OLD', name_i18n: { 'ru-RU': 'Старое' }, type: 'UNIT', status: 'ARCHIVED', updated_at: '' },
];

const editPosition: Position = {
  id: 'pos1',
  project_id: 'p',
  department_id: 'd1',
  code: 'SWE',
  title_i18n: { 'ru-RU': 'Разработчик' },
  function: 'Technology',
  status: 'ACTIVE',
};

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

  it('calls onSubmit with valid values and closes on success', async () => {
    const onClose = vi.fn();
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(
      renderWithProviders(
        <PositionFormDrawer
          open
          projectId="p"
          departments={departments}
          onClose={onClose}
          onSubmit={onSubmit}
        />,
      ),
    );
    // Select a department
    await user.selectOptions(screen.getByTestId('pos-department-select'), 'd1');
    await user.type(screen.getByTestId('pos-code'), 'SWE');
    // Fill required title in ru-RU tab
    await user.type(screen.getByTestId('locale-input-ru-RU'), 'Разработчик');
    await user.click(screen.getByRole('button', { name: /Сохранить|Save/i }));
    await new Promise((r) => setTimeout(r, 0));
    expect(onSubmit).toHaveBeenCalledTimes(1);
    expect(onClose).toHaveBeenCalled();
  });

  it('renders the error banner when an error prop is passed', () => {
    render(
      renderWithProviders(
        <PositionFormDrawer
          open
          projectId="p"
          departments={departments}
          error="Этот код уже используется. Выберите другой."
          onClose={() => {}}
          onSubmit={vi.fn()}
        />,
      ),
    );
    expect(screen.getByTestId('position-form-error')).toHaveTextContent(
      'Этот код уже используется. Выберите другой.',
    );
  });

  it('keeps the drawer open and does not call onClose when onSubmit rejects', async () => {
    const onClose = vi.fn();
    const onSubmit = vi.fn().mockRejectedValue(new Error('mock error'));
    const user = userEvent.setup();
    render(
      renderWithProviders(
        <PositionFormDrawer
          open
          projectId="p"
          departments={departments}
          onClose={onClose}
          onSubmit={onSubmit}
        />,
      ),
    );
    await user.selectOptions(screen.getByTestId('pos-department-select'), 'd1');
    await user.type(screen.getByTestId('pos-code'), 'SWE');
    await user.type(screen.getByTestId('locale-input-ru-RU'), 'Разработчик');
    await user.click(screen.getByRole('button', { name: /Сохранить|Save/i }));
    await new Promise((r) => setTimeout(r, 0));
    // Drawer stays open (onClose not called) because onSubmit rejected
    expect(onClose).not.toHaveBeenCalled();
  });

  it('in EDIT mode disables the code input and shows the immutable hint', () => {
    render(
      renderWithProviders(
        <PositionFormDrawer
          open
          projectId="p"
          departments={departments}
          initial={editPosition}
          onClose={() => {}}
          onSubmit={vi.fn()}
        />,
      ),
    );
    const code = screen.getByTestId('pos-code');
    expect(code).toHaveValue('SWE');
    expect(code).toBeDisabled();
    expect(
      screen.getByText(/нельзя изменить|cannot be changed|ўзгартириб бўлмайди|o'zgartirib bo'lmaydi/i),
    ).toBeInTheDocument();
  });

  it('in EDIT mode prefills fields from the initial position', () => {
    render(
      renderWithProviders(
        <PositionFormDrawer
          open
          projectId="p"
          departments={departments}
          initial={editPosition}
          onClose={() => {}}
          onSubmit={vi.fn()}
        />,
      ),
    );
    expect(screen.getByTestId('pos-code')).toHaveValue('SWE');
    expect(screen.getByDisplayValue('Разработчик')).toBeInTheDocument();
  });
});
