import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CreateTenantDialog } from './CreateTenantDialog';
import { ApiError } from '@/shared/api/apiError';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';

const submitButton = () =>
  screen.getByRole('button', {
    name: /Создать|Create|Яратиш|Yaratish/i,
  });

describe('<CreateTenantDialog />', () => {
  beforeEach(() => {
    signOut();
    signIn('super-admin');
  });

  it('blocks submission when slug fails the backend regex', async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    render(renderWithProviders(<CreateTenantDialog open onClose={() => {}} onSubmit={onSubmit} />));

    // Uppercase + too short → invalid per ^[a-z][a-z0-9_]{2,63}$.
    await user.type(screen.getByTestId('create-tenant-slug'), 'AB');
    await user.type(screen.getByTestId('create-tenant-display-name'), 'OzBank');
    await user.type(screen.getByTestId('create-tenant-legal-name'), 'OzBank JSCB');
    await user.click(submitButton());

    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('wires aria-invalid + aria-describedby onto a field once it errors', async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    render(renderWithProviders(<CreateTenantDialog open onClose={() => {}} onSubmit={onSubmit} />));

    const slugInput = screen.getByTestId('create-tenant-slug');
    // Clean before any submit attempt — aria-invalid must not appear at all
    // (not just "false") until the field actually has an error.
    expect(slugInput).not.toHaveAttribute('aria-invalid');
    expect(slugInput).not.toHaveAttribute('aria-describedby');

    // Uppercase + too short → invalid per ^[a-z][a-z0-9_]{2,63}$.
    await user.type(slugInput, 'AB');
    await user.type(screen.getByTestId('create-tenant-display-name'), 'OzBank');
    await user.type(screen.getByTestId('create-tenant-legal-name'), 'OzBank JSCB');
    await user.click(submitButton());

    await waitFor(() => expect(slugInput).toHaveAttribute('aria-invalid', 'true'));
    const describedBy = slugInput.getAttribute('aria-describedby');
    expect(describedBy).toBeTruthy();
    expect(document.getElementById(describedBy!)).toHaveAttribute('role', 'alert');
  });

  it('blocks submission when required fields are missing', async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    render(renderWithProviders(<CreateTenantDialog open onClose={() => {}} onSubmit={onSubmit} />));

    // Valid slug but no display name / legal name.
    await user.type(screen.getByTestId('create-tenant-slug'), 'ozbank');
    await user.click(submitButton());

    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('submits a contract-shaped payload (SCHEMA default, no tenant_id, empty optionals dropped)', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(renderWithProviders(<CreateTenantDialog open onClose={() => {}} onSubmit={onSubmit} />));

    await user.type(screen.getByTestId('create-tenant-slug'), 'ozbank');
    await user.type(screen.getByTestId('create-tenant-display-name'), 'OzBank');
    await user.type(screen.getByTestId('create-tenant-legal-name'), 'OzBank JSCB');
    await user.click(submitButton());

    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    const payload = onSubmit.mock.calls[0][0];
    expect(payload).toEqual({
      slug: 'ozbank',
      display_name: 'OzBank',
      default_locale: 'ru-RU',
      isolation_mode: 'SCHEMA',
      company_legal_name: 'OzBank JSCB',
    });
    // Never leak a manually entered tenant_id into the create body.
    expect('tenant_id' in payload).toBe(false);
    // Empty optionals must be omitted, not sent as empty strings.
    expect('company_brand_name' in payload).toBe(false);
    expect('company_industry' in payload).toBe(false);
  });

  it('maps a 409 TENANT_SLUG_TAKEN to a friendly field-level slug error', async () => {
    const onSubmit = vi
      .fn()
      .mockRejectedValue(new ApiError(409, { code: 'TENANT_SLUG_TAKEN', message: 'taken' }));
    const onClose = vi.fn();
    const user = userEvent.setup();
    render(renderWithProviders(<CreateTenantDialog open onClose={onClose} onSubmit={onSubmit} />));

    await user.type(screen.getByTestId('create-tenant-slug'), 'acme');
    await user.type(screen.getByTestId('create-tenant-display-name'), 'ACME Two');
    await user.type(screen.getByTestId('create-tenant-legal-name'), 'ACME Two LLC');
    await user.click(submitButton());

    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    // Friendly "slug already taken" message rendered as a field error.
    expect(
      await screen.findByText(
        /already taken|уже занят|аллақачон банд|allaqachon band/i,
      ),
    ).toBeInTheDocument();
    // Drawer stays open so the user can fix the slug.
    expect(onClose).not.toHaveBeenCalled();
  });
});
