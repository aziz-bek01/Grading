import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { renderWithProviders } from '@/test/testUtils';
import { RenameTemplateDrawer } from '../RenameTemplateDrawer';

function renderDrawer(overrides: Partial<Parameters<typeof RenameTemplateDrawer>[0]> = {}) {
  const onSubmit = vi.fn().mockResolvedValue(undefined);
  const onClose = vi.fn();
  render(
    renderWithProviders(
      <RenameTemplateDrawer
        title="Rename template"
        subtitle="Update the template name"
        code="TPL-1"
        codeImmutableHint="Code cannot change"
        initialName={{ 'ru-RU': 'Шаблон' }}
        initialDescription={{}}
        validateName={(name) => Object.values(name).some((v) => v?.trim())}
        nameRequiredError="Name is required"
        notFoundError="Template no longer exists"
        genericError="Rename failed"
        isNotFoundError={() => false}
        onClose={onClose}
        onSubmit={onSubmit}
        testIdPrefix="fake-rename-template"
        {...overrides}
      />,
    ),
  );
  return { onSubmit, onClose };
}

describe('<RenameTemplateDrawer /> (shared)', () => {
  it('shows the immutable code read-only', () => {
    renderDrawer();
    const input = screen.getByTestId('fake-rename-template-code') as HTMLInputElement;
    expect(input.readOnly).toBe(true);
    expect(input.value).toBe('TPL-1');
    expect(screen.getByText('Code cannot change')).toBeInTheDocument();
  });

  it('requires a valid name (per validateName) before submitting', async () => {
    const { onSubmit } = renderDrawer({ initialName: {}, validateName: () => false });
    fireEvent.click(screen.getByText(/Сохранить|Save/));
    await waitFor(() =>
      expect(screen.getByTestId('fake-rename-template-error')).toHaveTextContent(
        'Name is required',
      ),
    );
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('submits name + description only (code is never part of the payload)', async () => {
    const { onSubmit } = renderDrawer();
    fireEvent.click(screen.getByText(/Сохранить|Save/));
    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    const payload = onSubmit.mock.calls[0]![0] as Record<string, unknown>;
    expect(payload).not.toHaveProperty('code');
    expect(payload.name_i18n).toEqual({ 'ru-RU': 'Шаблон' });
  });

  it('maps a 404 rejection to notFoundError', async () => {
    const onSubmit = vi.fn().mockRejectedValue(new Error('gone'));
    renderDrawer({ onSubmit, isNotFoundError: () => true });
    fireEvent.click(screen.getByText(/Сохранить|Save/));
    await waitFor(() =>
      expect(screen.getByTestId('fake-rename-template-error')).toHaveTextContent(
        'Template no longer exists',
      ),
    );
  });

  it('maps any other rejection to genericError', async () => {
    const onSubmit = vi.fn().mockRejectedValue(new Error('network down'));
    renderDrawer({ onSubmit, isNotFoundError: () => false });
    fireEvent.click(screen.getByText(/Сохранить|Save/));
    await waitFor(() =>
      expect(screen.getByTestId('fake-rename-template-error')).toHaveTextContent(
        'Rename failed',
      ),
    );
  });
});
