import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { renderWithProviders, signInWithPermissions, signOut } from '@/test/testUtils';
import { PERMISSIONS } from '@/shared/types/permissions';
import { TemplatePicker, type TemplatePickerOption } from '../TemplatePicker';

interface FakeTemplate {
  id: string | null;
  code: string;
}

interface FakeSelection {
  code: string;
  isCustom: boolean;
}

function buildOptions(): TemplatePickerOption<FakeSelection, FakeTemplate>[] {
  return [
    {
      code: 'BUILTIN_A',
      title: 'Built-in A',
      body: 'Body A',
      icon: <span aria-hidden>A</span>,
      isCustom: false,
      canManage: false,
      selection: { code: 'BUILTIN_A', isCustom: false },
    },
    {
      code: 'TPL-1',
      title: 'Custom template',
      body: 'Body custom',
      icon: <span aria-hidden>C</span>,
      isCustom: true,
      canManage: true,
      selection: { code: 'TPL-1', isCustom: true },
      template: { id: 'tpl-1', code: 'TPL-1' },
    },
  ];
}

function renderPicker(
  overrides: Partial<Parameters<typeof TemplatePicker<FakeSelection, FakeTemplate>>[0]> = {},
) {
  const onSelect = vi.fn();
  const onCancel = vi.fn();
  const onRenameTemplate = vi.fn();
  const onArchiveTemplate = vi.fn();
  render(
    renderWithProviders(
      <TemplatePicker<FakeSelection, FakeTemplate>
        titleId="fake-picker-title"
        title="Pick a template"
        body="Choose one to continue"
        options={buildOptions()}
        customBadgeLabel="Custom"
        builtinBadgeLabel="Built-in"
        renameLabel="Rename"
        archiveLabel="Archive"
        permission={PERMISSIONS.GRADE_EDIT}
        onCancel={onCancel}
        onSelect={onSelect}
        onRenameTemplate={onRenameTemplate}
        onArchiveTemplate={onArchiveTemplate}
        testIdPrefix="fake-template"
        {...overrides}
      />,
    ),
  );
  return { onSelect, onCancel, onRenameTemplate, onArchiveTemplate };
}

describe('<TemplatePicker /> (shared)', () => {
  beforeEach(() => signInWithPermissions([PERMISSIONS.GRADE_EDIT]));
  afterEach(() => {
    signOut();
    vi.clearAllMocks();
  });

  it('renders every option with the correct source badge', () => {
    renderPicker();
    expect(screen.getByTestId('fake-template-option-BUILTIN_A')).toBeInTheDocument();
    expect(screen.getByTestId('fake-template-option-TPL-1')).toBeInTheDocument();
    expect(screen.getByText('Built-in')).toBeInTheDocument();
    expect(screen.getByText('Custom')).toBeInTheDocument();
  });

  it('the continue button is disabled until a row is selected', () => {
    renderPicker();
    expect(screen.getByTestId('fake-template-picker-continue')).toBeDisabled();
    fireEvent.click(screen.getByTestId('fake-template-option-BUILTIN_A'));
    expect(screen.getByTestId('fake-template-picker-continue')).not.toBeDisabled();
  });

  it('continue hands back the exact selection payload for the chosen row', () => {
    const { onSelect } = renderPicker();
    fireEvent.click(screen.getByTestId('fake-template-option-TPL-1'));
    fireEvent.click(screen.getByTestId('fake-template-picker-continue'));
    expect(onSelect).toHaveBeenCalledWith({ code: 'TPL-1', isCustom: true });
  });

  it('cancel calls onCancel', () => {
    const { onCancel } = renderPicker();
    fireEvent.click(screen.getByText(/Отмена|Cancel/));
    expect(onCancel).toHaveBeenCalledTimes(1);
  });

  it('manageable custom rows expose rename + archive for the gating permission', () => {
    const { onRenameTemplate, onArchiveTemplate } = renderPicker();
    fireEvent.click(screen.getByTestId('fake-template-TPL-1-rename'));
    expect(onRenameTemplate).toHaveBeenCalledWith({ id: 'tpl-1', code: 'TPL-1' });
    fireEvent.click(screen.getByTestId('fake-template-TPL-1-archive'));
    expect(onArchiveTemplate).toHaveBeenCalledWith({ id: 'tpl-1', code: 'TPL-1' });
  });

  it('non-manageable (built-in) rows never render manage actions', () => {
    renderPicker();
    expect(screen.queryByTestId('fake-template-BUILTIN_A-rename')).toBeNull();
    expect(screen.queryByTestId('fake-template-BUILTIN_A-archive')).toBeNull();
  });

  it('hides manage actions when the caller lacks the gating permission (UX-only)', () => {
    signOut();
    signInWithPermissions([]);
    renderPicker();
    expect(screen.queryByTestId('fake-template-TPL-1-rename')).toBeNull();
    expect(screen.queryByTestId('fake-template-TPL-1-archive')).toBeNull();
  });
});
