import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import { ApiError } from '@/shared/api/apiError';
import { SaveAsTemplateDrawer } from './SaveAsTemplateDrawer';
import type { Methodology } from '../types';

const methodology: Methodology = {
  id: 'meth-1',
  project_id: 'proj-1',
  code: 'M1',
  name_i18n: { 'ru-RU': 'CFO Финансы' },
  methodology_type: 'CLASSIC_8_FACTOR',
  status: 'ACTIVE',
};

function renderDrawer(onSubmit: (p: unknown) => Promise<void>) {
  render(
    renderWithProviders(
      <SaveAsTemplateDrawer
        open
        methodology={methodology}
        onClose={vi.fn()}
        onSubmit={onSubmit as never}
      />,
    ),
  );
}

describe('SaveAsTemplateDrawer (Epic E)', () => {
  beforeEach(() => signIn('super-admin'));
  afterEach(() => {
    signOut();
    vi.clearAllMocks();
  });

  it('submits the entered code + localized name as the save-as-template payload', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    renderDrawer(onSubmit);

    fireEvent.change(screen.getByTestId('save-template-code'), {
      target: { value: 'tpl-cfo' },
    });
    // ru-RU is the default-active locale tab; it is pre-seeded from the source.
    fireEvent.click(screen.getByText(/Сохранить шаблон|Save template/));

    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({
        code: 'TPL-CFO',
        name_i18n: expect.objectContaining({ 'ru-RU': 'CFO Финансы' }),
      }),
    );
  });

  it('requires a template code before submitting', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    renderDrawer(onSubmit);
    fireEvent.click(screen.getByText(/Сохранить шаблон|Save template/));
    await waitFor(() =>
      expect(screen.getByTestId('save-template-code-error')).toBeInTheDocument(),
    );
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('surfaces 409 METHODOLOGY_TEMPLATE_CODE_EXISTS inline on the code field', async () => {
    const onSubmit = vi
      .fn()
      .mockRejectedValue(
        new ApiError(409, { code: 'METHODOLOGY_TEMPLATE_CODE_EXISTS', message: 'exists' }),
      );
    renderDrawer(onSubmit);

    fireEvent.change(screen.getByTestId('save-template-code'), {
      target: { value: 'DUP' },
    });
    fireEvent.click(screen.getByText(/Сохранить шаблон|Save template/));

    await waitFor(() =>
      expect(screen.getByTestId('save-template-code-error')).toHaveTextContent(
        /уже существует|already exists/i,
      ),
    );
  });
});
