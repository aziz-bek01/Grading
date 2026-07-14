/**
 * AssignRoleDialog — single-role picker now DATA-DRIVEN from GET /roles.
 *
 * Verifies the picker renders from the API catalog (not the deleted constants),
 * roles the caller cannot grant are disabled with a reason, already-assigned
 * roles are excluded, and selecting an assignable role submits the right code.
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { AxiosAdapter } from 'axios';
import { AssignRoleDialog } from './AssignRoleDialog';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import { httpClient } from '@/shared/api/httpClient';
import { createMockAdapter } from '@/shared/api/mocks/handlers';

const ORIGINAL_ADAPTER = httpClient.defaults.adapter as AxiosAdapter | undefined;

beforeEach(() => {
  httpClient.defaults.adapter = createMockAdapter(ORIGINAL_ADAPTER);
});

afterEach(() => {
  httpClient.defaults.adapter = ORIGINAL_ADAPTER;
  signOut();
});

const SUBMIT = /Добавить роль|Add role|Rol qo'shish|Рол қўшиш/i;

/** Waits for the single-select to render its data-driven options. */
async function findRoleSelect(): Promise<HTMLSelectElement> {
  return (await screen.findByTestId('assign-role-code')) as HTMLSelectElement;
}

describe('<AssignRoleDialog />', () => {
  beforeEach(() => {
    signOut();
    signIn('super-admin');
  });

  it('renders options from the API catalog, not a hardcoded list', async () => {
    render(
      renderWithProviders(
        <AssignRoleDialog
          open
          alreadyAssigned={[]}
          tenantBrand="ACME Holdings"
          onClose={() => {}}
          onSubmit={() => {}}
        />,
      ),
    );
    const select = await findRoleSelect();
    const values = Array.from(select.options).map((o) => o.value);
    // Codes present only because GET /roles returned them.
    expect(values).toContain('CLIENT_HR_DIRECTOR');
    expect(values).toContain('VIEWER');
    expect(values).toContain('HRLAB_CONSULTANT');
  });

  it('excludes already-assigned roles from the picker', async () => {
    render(
      renderWithProviders(
        <AssignRoleDialog
          open
          alreadyAssigned={['VIEWER']}
          tenantBrand="ACME Holdings"
          onClose={() => {}}
          onSubmit={() => {}}
        />,
      ),
    );
    const select = await findRoleSelect();
    const values = Array.from(select.options).map((o) => o.value);
    expect(values).not.toContain('VIEWER');
    expect(values).toContain('CLIENT_HR_DIRECTOR');
  });

  it('submits the selected role code', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(
      renderWithProviders(
        <AssignRoleDialog
          open
          alreadyAssigned={[]}
          tenantBrand="ACME Holdings"
          onClose={() => {}}
          onSubmit={onSubmit}
        />,
      ),
    );
    const select = await findRoleSelect();
    await user.selectOptions(select, 'CLIENT_HR_SPECIALIST');
    await user.click(screen.getByRole('button', { name: SUBMIT }));
    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    expect(onSubmit.mock.calls[0][0]).toEqual({ role_code: 'CLIENT_HR_SPECIALIST' });
  });

  it('refuses submission when no role is chosen', async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    render(
      renderWithProviders(
        <AssignRoleDialog
          open
          alreadyAssigned={[]}
          tenantBrand="ACME Holdings"
          onClose={() => {}}
          onSubmit={onSubmit}
        />,
      ),
    );
    // Leave the placeholder selected (no role) then submit.
    await findRoleSelect();
    await user.click(screen.getByRole('button', { name: SUBMIT }));
    expect(onSubmit).not.toHaveBeenCalled();
    expect(await screen.findByRole('alert')).toBeInTheDocument();
  });

  it('wires aria-invalid + aria-describedby onto the role select once it errors', async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    render(
      renderWithProviders(
        <AssignRoleDialog
          open
          alreadyAssigned={[]}
          tenantBrand="ACME Holdings"
          onClose={() => {}}
          onSubmit={onSubmit}
        />,
      ),
    );
    const select = await findRoleSelect();
    // Clean before any submit attempt — aria-invalid must not appear at all.
    expect(select).not.toHaveAttribute('aria-invalid');
    expect(select).not.toHaveAttribute('aria-describedby');

    await user.click(screen.getByRole('button', { name: SUBMIT }));

    await waitFor(() => expect(select).toHaveAttribute('aria-invalid', 'true'));
    const describedBy = select.getAttribute('aria-describedby');
    expect(describedBy).toBeTruthy();
    expect(document.getElementById(describedBy!)).toHaveAttribute('role', 'alert');
  });
});

describe('<AssignRoleDialog /> as client admin (non-super)', () => {
  beforeEach(() => {
    signOut();
    signIn('consultant');
  });

  it('renders platform/HRLab roles as disabled options', async () => {
    render(
      renderWithProviders(
        <AssignRoleDialog
          open
          alreadyAssigned={[]}
          tenantBrand="ACME Holdings"
          onClose={() => {}}
          onSubmit={() => {}}
        />,
      ),
    );
    const select = await findRoleSelect();
    const hrlabOption = within(select).getByRole('option', {
      name: /HRLab Super Admin/i,
    }) as HTMLOptionElement;
    expect(hrlabOption.disabled).toBe(true);
    // The disabled option's label carries the localized reason hint.
    expect(hrlabOption.textContent).toMatch(/Только HRLab Super Admin/i);
    // A tenant-scope role remains selectable.
    const viewer = within(select).getByRole('option', { name: /Наблюдатель/i }) as HTMLOptionElement;
    expect(viewer.disabled).toBe(false);
  });
});
