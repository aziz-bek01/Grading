import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ProjectFormDrawer } from './ProjectFormDrawer';
import { renderWithProviders } from '@/test/testUtils';
import type { Project } from '../types/projectTypes';

const editProject: Project = {
  id: 'p1',
  code: 'ACME-2026',
  name_i18n: { 'ru-RU': 'Грейдинг ACME' },
  status: 'ACTIVE',
};

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

  it('in EDIT mode disables the code input and shows the immutable hint', () => {
    render(
      renderWithProviders(
        <ProjectFormDrawer open initial={editProject} onClose={() => {}} onSubmit={vi.fn()} />,
      ),
    );
    const code = screen.getByTestId('project-code');
    expect(code).toHaveValue('ACME-2026');
    expect(code).toBeDisabled();
    // The hint explains the code is the immutable identifier (not the format hint).
    expect(
      screen.getByText(/нельзя изменить|cannot be changed|ўзгартириб бўлмайди|oʻzgartirib boʻlmaydi/i),
    ).toBeInTheDocument();
  });

  it('in EDIT mode prefills name_i18n and round-trips a rename to onSubmit', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(
      renderWithProviders(
        <ProjectFormDrawer open initial={editProject} onClose={() => {}} onSubmit={onSubmit} />,
      ),
    );
    const ru = screen.getByDisplayValue('Грейдинг ACME');
    await user.clear(ru);
    await user.type(ru, 'Грейдинг ACME v2');
    await user.click(screen.getByRole('button', { name: /Сохранить|Save/i }));
    await new Promise((r) => setTimeout(r, 0));
    expect(onSubmit).toHaveBeenCalledTimes(1);
    expect(onSubmit.mock.calls[0][0].name_i18n['ru-RU']).toBe('Грейдинг ACME v2');
  });

  it('renders the error banner when an error prop is passed', () => {
    render(
      renderWithProviders(
        <ProjectFormDrawer
          open
          error="Этот код уже используется."
          onClose={() => {}}
          onSubmit={vi.fn()}
        />,
      ),
    );
    expect(screen.getByTestId('project-form-error')).toHaveTextContent('Этот код уже используется.');
  });
});
