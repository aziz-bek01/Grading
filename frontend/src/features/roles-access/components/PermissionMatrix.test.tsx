/**
 * PermissionMatrix — grouping, restricted-lock, read-only and toggle behavior.
 *
 * Pure presentational test: no network. Verifies the matrix groups by resource,
 * locks restricted rows, disables everything in read-only mode, and reports the
 * right code on toggle (the page owns the replace-set; this just emits events).
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
});
