/**
 * PermissionMatrix — grouping, restricted-lock, read-only, not-held-lock,
 * and toggle behavior.
 *
 * Pure presentational test: no network. Verifies the matrix groups by resource,
 * locks restricted rows, locks rows the caller does not hold, disables everything
 * in read-only mode, and reports the right code on toggle (the page owns the
 * replace-set; this just emits events).
 */
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { I18nProvider } from '@/app/providers/I18nProvider';
import { PermissionMatrix } from './PermissionMatrix';
import type { RolePermissionItem } from '@/features/users-access/api/rolesApi';

const ITEMS: RolePermissionItem[] = [
  { code: 'PROJECT_READ', resource: 'PROJECT', action: 'READ', granted: true, restricted: false },
  { code: 'PROJECT_EDIT', resource: 'PROJECT', action: 'EDIT', granted: false, restricted: false },
  { code: 'SALARY_VIEW', resource: 'SALARY', action: 'VIEW', granted: false, restricted: true },
  { code: 'AUDIT_READ', resource: 'AUDIT', action: 'READ', granted: true, restricted: false },
];

function setup(
  props: Partial<React.ComponentProps<typeof PermissionMatrix>> = {},
  selected = new Set<string>(['PROJECT_READ', 'AUDIT_READ']),
) {
  const onToggle = vi.fn();
  const onToggleGroup = vi.fn();
  render(
    <I18nProvider>
      <PermissionMatrix
        items={ITEMS}
        selected={selected}
        readOnly={false}
        onToggle={onToggle}
        onToggleGroup={onToggleGroup}
        {...props}
      />
    </I18nProvider>,
  );
  return { onToggle, onToggleGroup };
}

describe('<PermissionMatrix />', () => {
  it('groups permissions by resource (module)', () => {
    setup();
    expect(screen.getByTestId('permission-group-PROJECT')).toBeInTheDocument();
    expect(screen.getByTestId('permission-group-SALARY')).toBeInTheDocument();
    expect(screen.getByTestId('permission-group-AUDIT')).toBeInTheDocument();
  });

  it('locks restricted rows (disabled + lock hint) and never togglable', () => {
    const { onToggle } = setup();
    const restricted = screen.getByTestId('permission-checkbox-SALARY_VIEW') as HTMLInputElement;
    expect(restricted.disabled).toBe(true);
    expect(screen.getByTestId('permission-restricted-SALARY_VIEW')).toBeInTheDocument();
    // Its checked state mirrors `granted` (false here), not `selected`.
    expect(restricted.checked).toBe(false);
    expect(onToggle).not.toHaveBeenCalled();
  });

  it('reflects the selected set on non-restricted rows', () => {
    setup();
    expect((screen.getByTestId('permission-checkbox-PROJECT_READ') as HTMLInputElement).checked).toBe(true);
    expect((screen.getByTestId('permission-checkbox-PROJECT_EDIT') as HTMLInputElement).checked).toBe(false);
  });

  it('emits onToggle with the code and next value', async () => {
    const user = userEvent.setup();
    const { onToggle } = setup();
    await user.click(screen.getByTestId('permission-checkbox-PROJECT_EDIT'));
    expect(onToggle).toHaveBeenCalledWith('PROJECT_EDIT', true);
  });

  it('module header toggles every non-restricted code in that group', async () => {
    const user = userEvent.setup();
    const { onToggleGroup } = setup();
    await user.click(screen.getByTestId('permission-group-toggle-PROJECT'));
    // PROJECT module has PROJECT_READ + PROJECT_EDIT (both non-restricted).
    const [codes] = onToggleGroup.mock.calls[0];
    expect(new Set(codes)).toEqual(new Set(['PROJECT_READ', 'PROJECT_EDIT']));
  });

  it('disables ALL checkboxes when read-only', () => {
    setup({ readOnly: true });
    expect((screen.getByTestId('permission-checkbox-PROJECT_READ') as HTMLInputElement).disabled).toBe(true);
    expect((screen.getByTestId('permission-checkbox-PROJECT_EDIT') as HTMLInputElement).disabled).toBe(true);
    expect((screen.getByTestId('permission-group-toggle-PROJECT') as HTMLInputElement).disabled).toBe(true);
  });

  // -----------------------------------------------------------------------
  // "Caller does not hold" locking
  // -----------------------------------------------------------------------

  it('locks a row when callerPermissions does not include its code', () => {
    // Caller holds PROJECT_READ but NOT PROJECT_EDIT.
    setup({ callerPermissions: new Set(['PROJECT_READ', 'AUDIT_READ']) });
    const unheld = screen.getByTestId('permission-checkbox-PROJECT_EDIT') as HTMLInputElement;
    expect(unheld.disabled).toBe(true);
    // The not-held hint badge should be visible.
    expect(screen.getByTestId('permission-not-held-PROJECT_EDIT')).toBeInTheDocument();
    // The restricted badge should NOT appear (different lock reason).
    expect(screen.queryByTestId('permission-restricted-PROJECT_EDIT')).not.toBeInTheDocument();
  });

  it('not-held locked row mirrors its granted state, not selected', () => {
    // PROJECT_EDIT has granted=false; it should render unchecked even if we
    // somehow put it in selected (which the parent's init logic prevents).
    const selectedWithUnheld = new Set(['PROJECT_READ', 'AUDIT_READ', 'PROJECT_EDIT']);
    setup(
      { callerPermissions: new Set(['PROJECT_READ', 'AUDIT_READ']) },
      selectedWithUnheld,
    );
    // PROJECT_EDIT is not held → locked → reflects granted=false, ignores selected.
    const cb = screen.getByTestId('permission-checkbox-PROJECT_EDIT') as HTMLInputElement;
    expect(cb.checked).toBe(false);
    expect(cb.disabled).toBe(true);
  });

  it('module select-all excludes not-held codes', async () => {
    const user = userEvent.setup();
    // Caller holds PROJECT_READ but not PROJECT_EDIT.
    const { onToggleGroup } = setup({ callerPermissions: new Set(['PROJECT_READ', 'AUDIT_READ']) });
    await user.click(screen.getByTestId('permission-group-toggle-PROJECT'));
    // Only PROJECT_READ should be in the togglable set — PROJECT_EDIT is excluded.
    const [codes] = onToggleGroup.mock.calls[0];
    expect(codes).toContain('PROJECT_READ');
    expect(codes).not.toContain('PROJECT_EDIT');
  });

  it('module select-all header is disabled when all togglable codes are held but none remain', () => {
    // Caller holds nothing in PROJECT → toggle should be disabled (no togglable rows).
    setup({ callerPermissions: new Set(['AUDIT_READ']) });
    const header = screen.getByTestId('permission-group-toggle-PROJECT') as HTMLInputElement;
    expect(header.disabled).toBe(true);
  });

  it('when callerPermissions is omitted all rows are treated as held (backward compat)', () => {
    // No callerPermissions prop → all non-restricted rows are togglable.
    setup();
    expect((screen.getByTestId('permission-checkbox-PROJECT_EDIT') as HTMLInputElement).disabled).toBe(false);
    expect(screen.queryByTestId('permission-not-held-PROJECT_EDIT')).not.toBeInTheDocument();
  });
});
