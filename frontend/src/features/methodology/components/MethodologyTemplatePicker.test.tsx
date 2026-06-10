import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import { MethodologyTemplatePicker } from './MethodologyTemplatePicker';
import type { MethodologyTemplate } from '../types';

const builtins: MethodologyTemplate[] = [
  {
    id: null,
    code: 'CLASSIC_8_FACTOR',
    name_i18n: { 'ru-RU': 'Классическая 8-факторная' },
    factor_count: 8,
    default_scoring_mode: 'WEIGHTED_POINTS',
    source: 'BUILTIN',
    is_builtin: true,
  },
];

const custom: MethodologyTemplate = {
  id: 'tpl-1',
  code: 'TPL-CFO',
  name_i18n: { 'ru-RU': 'Шаблон CFO 2026' },
  factor_count: 8,
  default_scoring_mode: 'WEIGHTED_POINTS',
  source: 'CUSTOM',
  is_builtin: false,
};

vi.mock('../hooks/useMethodology', async () => {
  const actual = await vi.importActual<typeof import('../hooks/useMethodology')>(
    '../hooks/useMethodology',
  );
  return {
    ...actual,
    useMethodologyTemplates: () => ({
      data: { items: [...builtins, custom] },
      isError: false,
      isLoading: false,
    }),
  };
});

function renderPicker(overrides: Partial<Parameters<typeof MethodologyTemplatePicker>[0]> = {}) {
  const onSelect = vi.fn();
  const onRenameTemplate = vi.fn();
  const onArchiveTemplate = vi.fn();
  render(
    renderWithProviders(
      <MethodologyTemplatePicker
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

describe('MethodologyTemplatePicker (Epic E)', () => {
  beforeEach(() => signIn('super-admin'));
  afterEach(() => {
    signOut();
    vi.clearAllMocks();
  });

  it('lists built-in AND custom templates with source badges', async () => {
    renderPicker();
    await waitFor(() =>
      expect(screen.getByTestId('template-option-CLASSIC_8_FACTOR')).toBeInTheDocument(),
    );
    // Custom template row is present and selectable (create-from-custom).
    expect(screen.getByTestId('template-option-TPL-CFO')).toBeInTheDocument();
    expect(screen.getByText('Шаблон CFO 2026')).toBeInTheDocument();
  });

  it('selecting a custom template passes its concrete code (isCustom: true)', async () => {
    const { onSelect } = renderPicker();
    await waitFor(() => screen.getByTestId('template-option-TPL-CFO'));
    fireEvent.click(screen.getByTestId('template-option-TPL-CFO'));
    fireEvent.click(screen.getByTestId('template-picker-continue'));
    expect(onSelect).toHaveBeenCalledWith({ code: 'TPL-CFO', kind: 'CUSTOM', isCustom: true });
  });

  it('custom template offers rename + archive for METHODOLOGY_EDIT users', async () => {
    const { onRenameTemplate, onArchiveTemplate } = renderPicker();
    await waitFor(() => screen.getByTestId('template-option-TPL-CFO'));
    fireEvent.click(screen.getByTestId('template-TPL-CFO-rename'));
    expect(onRenameTemplate).toHaveBeenCalledWith(custom);
    fireEvent.click(screen.getByTestId('template-TPL-CFO-archive'));
    expect(onArchiveTemplate).toHaveBeenCalledWith(custom);
  });

  it('built-in templates expose NO rename/archive actions', async () => {
    renderPicker();
    await waitFor(() => screen.getByTestId('template-option-CLASSIC_8_FACTOR'));
    expect(screen.queryByTestId('template-CLASSIC_8_FACTOR-rename')).toBeNull();
    expect(screen.queryByTestId('template-CLASSIC_8_FACTOR-archive')).toBeNull();
  });

  it('viewer (no METHODOLOGY_EDIT) sees custom template but no manage actions', async () => {
    signOut();
    signIn('viewer');
    renderPicker();
    await waitFor(() => screen.getByTestId('template-option-TPL-CFO'));
    expect(screen.queryByTestId('template-TPL-CFO-rename')).toBeNull();
    expect(screen.queryByTestId('template-TPL-CFO-archive')).toBeNull();
  });
});
