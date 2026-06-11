import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import { ApiError } from '@/shared/api/apiError';
import { SaveAsGradeTemplateDrawer } from '../components/SaveAsGradeTemplateDrawer';
import type { GradeStructure } from '../types';

const structure: GradeStructure = {
  id: 'gs-1',
  project_id: 'proj-1',
  code: 'GS-1',
  name_i18n: { 'ru-RU': '14-грейдовая структура' },
  structure_type: 'GRADE_14',
  status: 'DRAFT',
  gap_policy: 'STRICT_NO_GAPS',
  version_number: 1,
  grades: [],
};

function renderDrawer(onSubmit: (p: unknown) => Promise<void>) {
  render(
    renderWithProviders(
      <SaveAsGradeTemplateDrawer
        open
        structure={structure}
        onClose={vi.fn()}
        onSubmit={onSubmit as never}
      />,
    ),
  );
}

describe('SaveAsGradeTemplateDrawer (FE-10)', () => {
  beforeEach(() => signIn('super-admin'));
  afterEach(() => {
    signOut();
    vi.clearAllMocks();
  });

  it('submits the entered code + seeded localized name', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    renderDrawer(onSubmit);
    fireEvent.change(screen.getByTestId('save-grade-template-code'), {
      target: { value: 'gs-tpl-1' },
    });
    fireEvent.click(screen.getByText(/Сохранить шаблон|Save template/));
    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({
        code: 'GS-TPL-1',
        name_i18n: expect.objectContaining({ 'ru-RU': '14-грейдовая структура' }),
      }),
    );
  });

  it('requires a template code', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    renderDrawer(onSubmit);
    fireEvent.click(screen.getByText(/Сохранить шаблон|Save template/));
    await waitFor(() =>
      expect(screen.getByTestId('save-grade-template-code-error')).toBeInTheDocument(),
    );
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('surfaces 409 GRADE_TEMPLATE_CODE_EXISTS inline without losing input', async () => {
    const onSubmit = vi
      .fn()
      .mockRejectedValue(
        new ApiError(409, { code: 'GRADE_TEMPLATE_CODE_EXISTS', message: 'exists' }),
      );
    renderDrawer(onSubmit);
    fireEvent.change(screen.getByTestId('save-grade-template-code'), {
      target: { value: 'DUP' },
    });
    fireEvent.click(screen.getByText(/Сохранить шаблон|Save template/));
    await waitFor(() =>
      expect(screen.getByTestId('save-grade-template-code-error')).toHaveTextContent(
        /уже существует|already exists/i,
      ),
    );
    // Input is preserved after the conflict.
    expect((screen.getByTestId('save-grade-template-code') as HTMLInputElement).value).toBe(
      'DUP',
    );
  });
});
