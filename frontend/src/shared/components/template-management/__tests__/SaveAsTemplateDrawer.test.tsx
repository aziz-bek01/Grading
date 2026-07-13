import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { renderWithProviders } from '@/test/testUtils';
import { SaveAsTemplateDrawer } from '../SaveAsTemplateDrawer';

function renderDrawer(overrides: Partial<Parameters<typeof SaveAsTemplateDrawer>[0]> = {}) {
  const onSubmit = vi.fn().mockResolvedValue(undefined);
  const onClose = vi.fn();
  render(
    renderWithProviders(
      <SaveAsTemplateDrawer
        title="Save as template"
        subtitle="Snapshot this entity"
        submitLabel="Save template"
        codeLabel="Template code"
        codePlaceholder="TPL-2026"
        hint="Visible to the whole tenant"
        initialName={{ 'ru-RU': 'Исходное имя' }}
        initialDescription={{}}
        validateName={(name) => Object.values(name).some((v) => v?.trim())}
        nameRequiredError="Name is required"
        codeRequiredError="Code is required"
        duplicateCodeError="That code already exists"
        genericError="Save failed"
        isDuplicateCodeError={() => false}
        fieldCodeError={() => undefined}
        onClose={onClose}
        onSubmit={onSubmit}
        testIdPrefix="fake-save-template"
        {...overrides}
      />,
    ),
  );
  return { onSubmit, onClose };
}

describe('<SaveAsTemplateDrawer /> (shared)', () => {
  it('requires a code before submitting', async () => {
    const { onSubmit } = renderDrawer();
    fireEvent.click(screen.getByText('Save template'));
    await waitFor(() =>
      expect(screen.getByTestId('fake-save-template-code-error')).toHaveTextContent(
        'Code is required',
      ),
    );
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('requires a valid name (per validateName) once a code is entered', async () => {
    const { onSubmit } = renderDrawer({ initialName: {}, validateName: () => false });
    fireEvent.change(screen.getByTestId('fake-save-template-code'), {
      target: { value: 'tpl-1' },
    });
    fireEvent.click(screen.getByText('Save template'));
    await waitFor(() =>
      expect(screen.getByTestId('fake-save-template-error')).toHaveTextContent(
        'Name is required',
      ),
    );
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('uppercases + trims the code and submits the seeded name', async () => {
    const { onSubmit } = renderDrawer();
    fireEvent.change(screen.getByTestId('fake-save-template-code'), {
      target: { value: 'tpl-cfo' },
    });
    fireEvent.click(screen.getByText('Save template'));
    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    expect(onSubmit).toHaveBeenCalledWith({
      code: 'TPL-CFO',
      name_i18n: { 'ru-RU': 'Исходное имя' },
      description_i18n: {},
    });
  });

  it('surfaces a duplicate-code conflict inline without losing the entered code', async () => {
    const onSubmit = vi.fn().mockRejectedValue(new Error('409 conflict'));
    renderDrawer({ onSubmit, isDuplicateCodeError: () => true });
    fireEvent.change(screen.getByTestId('fake-save-template-code'), {
      target: { value: 'dup' },
    });
    fireEvent.click(screen.getByText('Save template'));
    await waitFor(() =>
      expect(screen.getByTestId('fake-save-template-code-error')).toHaveTextContent(
        'That code already exists',
      ),
    );
    expect((screen.getByTestId('fake-save-template-code') as HTMLInputElement).value).toBe(
      'DUP',
    );
  });

  it('falls back to a backend field-level code error when not a duplicate conflict', async () => {
    const onSubmit = vi.fn().mockRejectedValue(new Error('validation'));
    renderDrawer({
      onSubmit,
      isDuplicateCodeError: () => false,
      fieldCodeError: () => 'Code must be alphanumeric',
    });
    fireEvent.change(screen.getByTestId('fake-save-template-code'), {
      target: { value: 'bad code' },
    });
    fireEvent.click(screen.getByText('Save template'));
    await waitFor(() =>
      expect(screen.getByTestId('fake-save-template-code-error')).toHaveTextContent(
        'Code must be alphanumeric',
      ),
    );
  });

  it('falls back to the generic error when neither duplicate nor field errors apply', async () => {
    const onSubmit = vi.fn().mockRejectedValue(new Error('network down'));
    renderDrawer({ onSubmit });
    fireEvent.change(screen.getByTestId('fake-save-template-code'), {
      target: { value: 'tpl-1' },
    });
    fireEvent.click(screen.getByText('Save template'));
    await waitFor(() =>
      expect(screen.getByTestId('fake-save-template-error')).toHaveTextContent('Save failed'),
    );
  });
});
