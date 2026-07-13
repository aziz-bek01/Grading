import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { renderWithProviders } from '@/test/testUtils';
import { TemplateMetadataDrawer, type MetadataPatchResult } from '../TemplateMetadataDrawer';
import type { LocalizedString } from '@/shared/types/common';

interface FakePatch {
  name_i18n: LocalizedString;
  description_i18n?: LocalizedString;
  extra?: string;
}

function renderDrawer(
  overrides: Partial<Parameters<typeof TemplateMetadataDrawer<FakePatch>>[0]> = {},
) {
  const onSubmit = vi.fn().mockResolvedValue(undefined);
  const onClose = vi.fn();
  render(
    renderWithProviders(
      <TemplateMetadataDrawer<FakePatch>
        title="Edit metadata"
        subtitle="Update the entity"
        code="ENT-1"
        codeImmutableHint="The code cannot be changed"
        initialName={{ 'en-US': 'Widget' }}
        initialDescription={{}}
        validateName={(name) => Object.values(name).some((v) => v?.trim())}
        nameRequiredError="Name is required"
        buildPatch={({ name_i18n, description_i18n }) => ({
          ok: true,
          patch: { name_i18n, description_i18n },
        })}
        mapError={() => 'Save failed'}
        onClose={onClose}
        onSubmit={onSubmit}
        testIdPrefix="fake-metadata"
        {...overrides}
      />,
    ),
  );
  return { onSubmit, onClose };
}

describe('<TemplateMetadataDrawer /> (shared)', () => {
  it('shows the immutable code read-only with its hint', () => {
    renderDrawer();
    const input = screen.getByTestId('fake-metadata-code') as HTMLInputElement;
    expect(input.readOnly).toBe(true);
    expect(input.value).toBe('ENT-1');
    expect(screen.getByText('The code cannot be changed')).toBeInTheDocument();
  });

  it('renders the entity-specific extra-fields slot', () => {
    renderDrawer({ children: <div data-testid="extra-field">Extra field</div> });
    expect(screen.getByTestId('extra-field')).toBeInTheDocument();
  });

  it('blocks submit and shows nameRequiredError when validateName fails', async () => {
    const { onSubmit } = renderDrawer({ initialName: {}, validateName: () => false });
    fireEvent.click(screen.getByText(/Сохранить|Save/));
    await waitFor(() =>
      expect(screen.getByTestId('fake-metadata-error')).toHaveTextContent('Name is required'),
    );
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('blocks submit and surfaces buildPatch validation errors (entity-specific extra fields)', async () => {
    const { onSubmit } = renderDrawer({
      buildPatch: (): MetadataPatchResult<FakePatch> => ({
        ok: false,
        error: 'Target total must be positive',
      }),
    });
    fireEvent.click(screen.getByText(/Сохранить|Save/));
    await waitFor(() =>
      expect(screen.getByTestId('fake-metadata-error')).toHaveTextContent(
        'Target total must be positive',
      ),
    );
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('submits the patch built by buildPatch on success', async () => {
    const { onSubmit } = renderDrawer({
      buildPatch: ({ name_i18n, description_i18n }) => ({
        ok: true,
        patch: { name_i18n, description_i18n, extra: 'computed' },
      }),
    });
    fireEvent.click(screen.getByText(/Сохранить|Save/));
    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({ name_i18n: { 'en-US': 'Widget' }, extra: 'computed' }),
    );
    expect(screen.queryByTestId('fake-metadata-error')).not.toBeInTheDocument();
  });

  it('maps a rejected onSubmit through mapError and shows it inline', async () => {
    const onSubmit = vi.fn().mockRejectedValue(new Error('boom'));
    renderDrawer({ onSubmit, mapError: () => 'Custom mapped failure' });
    fireEvent.click(screen.getByText(/Сохранить|Save/));
    await waitFor(() =>
      expect(screen.getByTestId('fake-metadata-error')).toHaveTextContent(
        'Custom mapped failure',
      ),
    );
  });

  it('calls onClose when the drawer close affordance is used', () => {
    const { onClose } = renderDrawer();
    fireEvent.click(screen.getByText(/Отмена|Cancel/));
    expect(onClose).toHaveBeenCalledTimes(1);
  });
});
