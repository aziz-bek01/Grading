import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import { GradeStructureTemplatePicker } from '../components/GradeStructureTemplatePicker';
import type { GradeStructureTemplate } from '../types';

const builtins: GradeStructureTemplate[] = [
  {
    id: null,
    code: 'GRADE_14',
    source: 'BUILTIN',
    is_builtin: true,
    name_i18n: { 'ru-RU': '14-грейдовая структура' },
    structure_type: 'GRADE_14',
    grade_count: 14,
  },
  {
    id: null,
    code: 'GRADE_16',
    source: 'BUILTIN',
    is_builtin: true,
    name_i18n: { 'ru-RU': '16-грейдовая структура' },
    structure_type: 'GRADE_16',
    grade_count: 16,
  },
];

const custom: GradeStructureTemplate = {
  id: 'gtpl-1',
  code: 'GS-TPL-BANK',
  source: 'CUSTOM',
  is_builtin: false,
  name_i18n: { 'ru-RU': 'Банковская сетка' },
  structure_type: 'CUSTOM',
  grade_count: 12,
};

vi.mock('../hooks/useGradeStructure', async () => {
  const actual = await vi.importActual<typeof import('../hooks/useGradeStructure')>(
    '../hooks/useGradeStructure',
  );
  return {
    ...actual,
    useGradeTemplates: () => ({
      data: { items: [...builtins, custom] },
      isError: false,
      isLoading: false,
    }),
  };
});

function renderPicker(
  overrides: Partial<Parameters<typeof GradeStructureTemplatePicker>[0]> = {},
) {
  const onSelect = vi.fn();
  const onRenameTemplate = vi.fn();
  const onArchiveTemplate = vi.fn();
  render(
    renderWithProviders(
      <GradeStructureTemplatePicker
        open
        onCancel={vi.fn()}
        onSelect={onSelect}
        onRenameTemplate={onRenameTemplate}
        onArchiveTemplate={onArchiveTemplate}
        {...overrides}
      />,
    ),
  );
  return { onSelect, onRenameTemplate, onArchiveTemplate };
}

describe('GradeStructureTemplatePicker (FE-9)', () => {
  beforeEach(() => signIn('super-admin'));
  afterEach(() => {
    signOut();
    vi.clearAllMocks();
  });

  it('lists built-in AND custom templates from the catalog', async () => {
    renderPicker();
    await waitFor(() =>
      expect(screen.getByTestId('gs-template-option-GRADE_14')).toBeInTheDocument(),
    );
    expect(screen.getByTestId('gs-template-option-GRADE_16')).toBeInTheDocument();
    expect(screen.getByTestId('gs-template-option-GS-TPL-BANK')).toBeInTheDocument();
    expect(screen.getByText('Банковская сетка')).toBeInTheDocument();
  });

  it('selecting a custom template passes its concrete code (isCustom: true)', async () => {
    const { onSelect } = renderPicker();
    await waitFor(() => screen.getByTestId('gs-template-option-GS-TPL-BANK'));
    fireEvent.click(screen.getByTestId('gs-template-option-GS-TPL-BANK'));
    fireEvent.click(screen.getByTestId('gs-template-picker-continue'));
    expect(onSelect).toHaveBeenCalledWith({
      code: 'GS-TPL-BANK',
      structureType: 'CUSTOM',
      isCustom: true,
    });
  });

  it('custom template offers rename + archive for GRADE_EDIT users', async () => {
    const { onRenameTemplate, onArchiveTemplate } = renderPicker();
    await waitFor(() => screen.getByTestId('gs-template-option-GS-TPL-BANK'));
    fireEvent.click(screen.getByTestId('gs-template-GS-TPL-BANK-rename'));
    expect(onRenameTemplate).toHaveBeenCalledWith(custom);
    fireEvent.click(screen.getByTestId('gs-template-GS-TPL-BANK-archive'));
    expect(onArchiveTemplate).toHaveBeenCalledWith(custom);
  });

  it('built-in templates expose NO rename/archive actions', async () => {
    renderPicker();
    await waitFor(() => screen.getByTestId('gs-template-option-GRADE_14'));
    expect(screen.queryByTestId('gs-template-GRADE_14-rename')).toBeNull();
    expect(screen.queryByTestId('gs-template-GRADE_14-archive')).toBeNull();
  });

  it('viewer (no GRADE_EDIT) sees the custom template but no manage actions', async () => {
    signOut();
    signIn('viewer');
    renderPicker();
    await waitFor(() => screen.getByTestId('gs-template-option-GS-TPL-BANK'));
    expect(screen.queryByTestId('gs-template-GS-TPL-BANK-rename')).toBeNull();
    expect(screen.queryByTestId('gs-template-GS-TPL-BANK-archive')).toBeNull();
  });
});
