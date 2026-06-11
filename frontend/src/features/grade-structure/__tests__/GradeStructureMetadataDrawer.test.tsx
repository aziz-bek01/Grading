import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import { GradeStructureMetadataDrawer } from '../components/GradeStructureMetadataDrawer';
import type { GradeStructure } from '../types';

function structure(overrides: Partial<GradeStructure> = {}): GradeStructure {
  return {
    id: 'gs-1',
    project_id: 'proj-1',
    code: 'GS-1',
    name_i18n: { 'ru-RU': 'Структура' },
    structure_type: 'GRADE_14',
    status: 'DRAFT',
    gap_policy: 'STRICT_NO_GAPS',
    version_number: 1,
    grades: [],
    ...overrides,
  };
}

describe('GradeStructureMetadataDrawer (FE-5)', () => {
  beforeEach(() => signIn('super-admin'));
  afterEach(() => {
    signOut();
    vi.clearAllMocks();
  });

  it('shows the code read-only and never sends it; sends name + gap_policy in builder mode', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(
      renderWithProviders(
        <GradeStructureMetadataDrawer
          open
          editable
          structure={structure()}
          onClose={vi.fn()}
          onSubmit={onSubmit}
        />,
      ),
    );
    // Code is read-only.
    expect((screen.getByTestId('grade-metadata-code') as HTMLInputElement).readOnly).toBe(
      true,
    );
    // gap_policy select is shown in builder DRAFT mode.
    fireEvent.change(screen.getByTestId('grade-metadata-gap-policy'), {
      target: { value: 'ALLOW_GAPS_WARN' },
    });
    fireEvent.click(screen.getByText(/Сохранить|Save/));
    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    const patch = onSubmit.mock.calls[0]![0] as Record<string, unknown>;
    expect(patch).not.toHaveProperty('code');
    expect(patch.gap_policy).toBe('ALLOW_GAPS_WARN');
    expect(patch.name_i18n).toMatchObject({ 'ru-RU': 'Структура' });
  });

  it('hides the gap_policy field in list mode (editable unset)', () => {
    render(
      renderWithProviders(
        <GradeStructureMetadataDrawer
          open
          structure={structure()}
          onClose={vi.fn()}
          onSubmit={vi.fn()}
        />,
      ),
    );
    expect(screen.queryByTestId('grade-metadata-gap-policy')).not.toBeInTheDocument();
  });

  it('requires a name in at least one language', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(
      renderWithProviders(
        <GradeStructureMetadataDrawer
          open
          structure={structure({ name_i18n: {} })}
          onClose={vi.fn()}
          onSubmit={onSubmit}
        />,
      ),
    );
    fireEvent.click(screen.getByText(/Сохранить|Save/));
    await waitFor(() =>
      expect(screen.getByTestId('grade-metadata-error')).toBeInTheDocument(),
    );
    expect(onSubmit).not.toHaveBeenCalled();
  });
});
