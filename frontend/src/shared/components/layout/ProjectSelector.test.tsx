/**
 * FE-015 regression: <ProjectSelector /> used to render with a permanently
 * empty `projects` prop (no feed existed for it), so the switcher listed
 * nothing and could never update `activeProject`. It is now self-contained
 * and driven by the existing `useProjects()` query, mirroring how
 * <TenantSelector /> reads `user.tenants`.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { AxiosAdapter } from 'axios';
import { ProjectSelector } from './ProjectSelector';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import { httpClient } from '@/shared/api/httpClient';
import { createMockAdapter } from '@/shared/api/mocks/handlers';
import { useAuthStore } from '@/features/auth/authStore';

const ORIGINAL_ADAPTER = httpClient.defaults.adapter as AxiosAdapter | undefined;

beforeEach(() => {
  httpClient.defaults.adapter = createMockAdapter(ORIGINAL_ADAPTER);
});

afterEach(() => {
  httpClient.defaults.adapter = ORIGINAL_ADAPTER;
  vi.restoreAllMocks();
  signOut();
});

describe('<ProjectSelector />', () => {
  it('lists the active tenant projects fetched via useProjects() (2+ projects)', async () => {
    const user = userEvent.setup();
    signIn('super-admin');
    render(renderWithProviders(<ProjectSelector />));

    await user.click(screen.getByRole('button', { name: /Выбор активного проекта/i }));

    // ACME Holdings (the default mock tenant) has 2 seeded projects.
    expect(await screen.findByRole('option', { name: /Грейдинг ACME 2026/i })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: /Пилотный проект ACME/i })).toBeInTheDocument();
  });

  it('selecting a project updates activeProject without a full navigation round-trip', async () => {
    const user = userEvent.setup();
    signIn('super-admin');
    render(renderWithProviders(<ProjectSelector />));

    expect(useAuthStore.getState().activeProject).toBeNull();

    await user.click(screen.getByRole('button', { name: /Выбор активного проекта/i }));
    await user.click(await screen.findByRole('option', { name: /Грейдинг ACME 2026/i }));

    await waitFor(() =>
      expect(useAuthStore.getState().activeProject?.name).toBe('Грейдинг ACME 2026'),
    );
    // The trigger button now reflects the active project's name.
    expect(screen.getByRole('button', { name: /Выбор активного проекта/i })).toHaveTextContent(
      'Грейдинг ACME 2026',
    );
  });

  it('switching to a different project asks for confirmation first', async () => {
    const user = userEvent.setup();
    signIn('super-admin');
    render(renderWithProviders(<ProjectSelector />));

    await user.click(screen.getByRole('button', { name: /Выбор активного проекта/i }));
    await user.click(await screen.findByRole('option', { name: /Грейдинг ACME 2026/i }));
    await waitFor(() =>
      expect(useAuthStore.getState().activeProject?.name).toBe('Грейдинг ACME 2026'),
    );

    await user.click(screen.getByRole('button', { name: /Выбор активного проекта/i }));
    await user.click(await screen.findByRole('option', { name: /Пилотный проект ACME/i }));

    expect(screen.getByRole('dialog')).toBeInTheDocument();
    // Still the previous project until confirmed.
    expect(useAuthStore.getState().activeProject?.name).toBe('Грейдинг ACME 2026');
  });
});
