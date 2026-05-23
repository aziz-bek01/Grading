import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TenantSelector } from './TenantSelector';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import { useAuthStore } from '@/features/auth/authStore';

describe('<TenantSelector />', () => {
  beforeEach(() => {
    signOut();
  });

  it('exposes no free-text tenant_id input field', () => {
    signIn('super-admin');
    render(renderWithProviders(<TenantSelector />));
    // Hard rule: NO textbox/input accepting tenant_id from the user
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
    expect(document.querySelector('input[name="tenant_id"]')).toBeNull();
  });

  it('opens a confirmation dialog when switching tenants', async () => {
    const user = userEvent.setup();
    signIn('super-admin');
    render(renderWithProviders(<TenantSelector />));

    await user.click(screen.getByRole('button', { name: /Выбор активной компании/i }));
    await user.click(screen.getByRole('option', { name: /Beta University/i }));

    // Confirmation dialog appears
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(screen.getByText(/Сменить активную компанию/i)).toBeInTheDocument();
  });

  it('only switches active tenant after explicit confirmation', async () => {
    const user = userEvent.setup();
    signIn('super-admin');
    render(renderWithProviders(<TenantSelector />));
    const initialId = useAuthStore.getState().activeTenant?.id;

    await user.click(screen.getByRole('button', { name: /Выбор активной компании/i }));
    await user.click(screen.getByRole('option', { name: /Beta University/i }));
    // Cancel first
    await user.click(screen.getByRole('button', { name: /Отмена/i }));
    expect(useAuthStore.getState().activeTenant?.id).toBe(initialId);

    // Open again and confirm
    await user.click(screen.getByRole('button', { name: /Выбор активной компании/i }));
    await user.click(screen.getByRole('option', { name: /Beta University/i }));
    await user.click(screen.getByRole('button', { name: /Переключить компанию/i }));

    expect(useAuthStore.getState().activeTenant?.brand_name).toBe('Beta University');
  });
});
