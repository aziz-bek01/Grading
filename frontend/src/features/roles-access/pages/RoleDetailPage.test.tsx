/**
 * RoleDetailPage — the per-role permission matrix editor (slice E2).
 *
 * Drives the real hooks against the in-process mock adapter (the same wiring as
 * AssignRoleDialog.test.tsx). Covers: grouped matrix render, restricted rows
 * locked, toggling + save sending the correct REPLACE-set payload, read-only
 * mode for a system role as a non-super caller, and error-code → message
 * mapping (PERMISSION_RESTRICTED / PERMISSION_NOT_HELD_BY_CALLER).
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import type { AxiosAdapter } from 'axios';
import { RoleDetailPage } from './RoleDetailPage';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import { httpClient } from '@/shared/api/httpClient';
import { createMockAdapter } from '@/shared/api/mocks/handlers';
import { ApiError } from '@/shared/api/apiError';

const ORIGINAL_ADAPTER = httpClient.defaults.adapter as AxiosAdapter | undefined;

function renderAt(roleCode: string) {
  return render(
    renderWithProviders(
      <Routes>
        <Route path="/app/roles/:roleCode" element={<RoleDetailPage />} />
      </Routes>,
      [`/app/roles/${roleCode}`],
    ),
  );
}

beforeEach(() => {
  httpClient.defaults.adapter = createMockAdapter(ORIGINAL_ADAPTER);
});

afterEach(() => {
  httpClient.defaults.adapter = ORIGINAL_ADAPTER;
  vi.restoreAllMocks();
  signOut();
});

describe('<RoleDetailPage /> as super-admin', () => {
  beforeEach(() => {
    signOut();
    signIn('super-admin');
  });

  it('renders the matrix grouped by module', async () => {
    renderAt('CLIENT_HR_DIRECTOR');
    expect(await screen.findByTestId('permission-group-PROJECT')).toBeInTheDocument();
    expect(screen.getByTestId('permission-group-METHODOLOGY')).toBeInTheDocument();
    expect(screen.getByTestId('permission-group-SALARY')).toBeInTheDocument();
  });

  it('locks restricted rows', async () => {
    renderAt('CLIENT_HR_DIRECTOR');
    const salary = (await screen.findByTestId('permission-checkbox-SALARY_VIEW')) as HTMLInputElement;
    expect(salary.disabled).toBe(true);
    expect(screen.getByTestId('permission-restricted-SALARY_VIEW')).toBeInTheDocument();
  });

  it('save sends the full REPLACE-set of checked, non-restricted codes', async () => {
    const user = userEvent.setup();
    const putSpy = vi.spyOn(httpClient, 'put');
    renderAt('CLIENT_HR_DIRECTOR');

    // Seeded grants include POSITION_CREATE; turn it OFF and turn METHODOLOGY_EDIT ON.
    const posCreate = (await screen.findByTestId('permission-checkbox-POSITION_CREATE')) as HTMLInputElement;
    expect(posCreate.checked).toBe(true);
    await user.click(posCreate); // revoke

    const methEdit = screen.getByTestId('permission-checkbox-METHODOLOGY_EDIT') as HTMLInputElement;
    expect(methEdit.checked).toBe(false);
    await user.click(methEdit); // grant

    await user.click(screen.getByTestId('role-save-button'));

    await waitFor(() => expect(putSpy).toHaveBeenCalledTimes(1));
    const [url, body] = putSpy.mock.calls[0];
    expect(url).toBe('/roles/CLIENT_HR_DIRECTOR/permissions');
    const codes = (body as { permission_codes: string[] }).permission_codes;
    // Replace-set: METHODOLOGY_EDIT present, POSITION_CREATE absent, restricted
    // SALARY_VIEW never sent.
    expect(codes).toContain('METHODOLOGY_EDIT');
    expect(codes).not.toContain('POSITION_CREATE');
    expect(codes).not.toContain('SALARY_VIEW');
    // A still-granted seed remains in the set.
    expect(codes).toContain('PROJECT_READ');

    expect(await screen.findByTestId('role-save-success')).toBeInTheDocument();
  });

  it('Save/Cancel are disabled until the matrix is dirty', async () => {
    const user = userEvent.setup();
    renderAt('VIEWER');
    const save = (await screen.findByTestId('role-save-button')) as HTMLButtonElement;
    const cancel = screen.getByTestId('role-cancel-button') as HTMLButtonElement;
    expect(save.disabled).toBe(true);
    expect(cancel.disabled).toBe(true);

    await user.click(screen.getByTestId('permission-checkbox-METHODOLOGY_EDIT'));
    expect(save.disabled).toBe(false);
    expect(cancel.disabled).toBe(false);

    // Cancel reverts to the server snapshot and re-disables the buttons.
    await user.click(cancel);
    expect((screen.getByTestId('role-save-button') as HTMLButtonElement).disabled).toBe(true);
  });

  it('maps a PERMISSION_RESTRICTED error to its localized message', async () => {
    const user = userEvent.setup();
    // Force the PUT to reject with the restricted code regardless of payload.
    vi.spyOn(httpClient, 'put').mockRejectedValue(
      new ApiError(422, { code: 'PERMISSION_RESTRICTED', message: 'restricted' }),
    );
    renderAt('CLIENT_HR_DIRECTOR');
    await user.click(await screen.findByTestId('permission-checkbox-METHODOLOGY_EDIT'));
    await user.click(screen.getByTestId('role-save-button'));
    const banner = await screen.findByTestId('role-save-error');
    expect(banner.textContent).toMatch(/berib bo'lmaydi|выдать роли|granted to the role/i);
  });

  it('maps a PERMISSION_NOT_HELD_BY_CALLER error to its localized message', async () => {
    const user = userEvent.setup();
    vi.spyOn(httpClient, 'put').mockRejectedValue(
      new ApiError(422, { code: 'PERMISSION_NOT_HELD_BY_CALLER', message: 'not held' }),
    );
    renderAt('CLIENT_HR_DIRECTOR');
    await user.click(await screen.findByTestId('permission-checkbox-METHODOLOGY_EDIT'));
    await user.click(screen.getByTestId('role-save-button'));
    const banner = await screen.findByTestId('role-save-error');
    expect(banner.textContent).toMatch(/yo'q ruxsatni|которого у вас нет|do not hold/i);
  });
});

describe('<RoleDetailPage /> read-only matrix (editable_by_caller=false)', () => {
  beforeEach(() => {
    signOut();
    // Caller HAS USER_ACCESS_MANAGE (so the page renders) but the backend marks
    // this particular role's matrix as non-editable for them.
    signIn('super-admin');
  });

  it('shows a read-only banner and hides Save/Cancel when not editable', async () => {
    vi.spyOn(httpClient, 'get').mockResolvedValue({
      data: {
        role_code: 'CLIENT_HR_DIRECTOR',
        scope: 'TENANT',
        is_system: true,
        editable_by_caller: false,
        items: [
          { code: 'PROJECT_READ', resource: 'PROJECT', action: 'READ', granted: true, restricted: false },
          { code: 'METHODOLOGY_EDIT', resource: 'METHODOLOGY', action: 'EDIT', granted: false, restricted: false },
        ],
      },
      status: 200,
      statusText: 'OK',
      headers: {},
      config: {} as never,
    });

    renderAt('CLIENT_HR_DIRECTOR');
    expect(await screen.findByTestId('role-readonly-banner')).toBeInTheDocument();
    expect(screen.queryByTestId('role-save-button')).not.toBeInTheDocument();
    // Matrix checkboxes are present but disabled.
    const cb = screen.getByTestId('permission-checkbox-PROJECT_READ') as HTMLInputElement;
    expect(cb.disabled).toBe(true);
  });
});
