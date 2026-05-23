import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ProjectFormDrawer } from './ProjectFormDrawer';
import { renderWithProviders } from '@/test/testUtils';

describe('<ProjectFormDrawer />', () => {
  it('refuses submission when primary-locale name is missing', async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    render(renderWithProviders(<ProjectFormDrawer open onClose={() => {}} onSubmit={onSubmit} />));
    await user.type(screen.getByTestId('project-code'), 'NEW1');
    await user.click(screen.getByRole('button', { name: /Сохранить|Save/i }));
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('switches between locale tabs', async () => {
    const user = userEvent.setup();
    render(renderWithProviders(<ProjectFormDrawer open onClose={() => {}} onSubmit={vi.fn()} />));
    // ru-RU input visible by default
    expect(screen.getByTestId('locale-input-ru-RU')).toBeVisible();
    // switch to English
    await user.click(screen.getByRole('tab', { name: /English/i }));
    expect(screen.getByTestId('locale-input-en-US')).toBeVisible();
  });

  it('calls onSubmit with valid values', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(renderWithProviders(<ProjectFormDrawer open onClose={() => {}} onSubmit={onSubmit} />));
    await user.type(screen.getByTestId('project-code'), 'NEW1');
    const ruInput = screen.getByTestId('locale-input-ru-RU');
    await user.type(ruInput, 'Новый проект');
    await user.click(screen.getByRole('button', { name: /Сохранить|Save/i }));
    // submission is async — assert it eventually fires
    await new Promise((r) => setTimeout(r, 0));
    expect(onSubmit).toHaveBeenCalled();
  });
});
