/**
 * Projects CRUD page tests.
 *
 * Focus (the product-owner ask): edit/rename + archive are wired and the
 * duplicate-code conflict is surfaced. We drive the page against the in-process
 * mock backend (createMockAdapter) so the full hook → api → httpClient → mock
 * round-trip is exercised, including snake_case wire + tenant isolation.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Routes, Route } from 'react-router-dom';
import type { AxiosAdapter } from 'axios';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import { httpClient } from '@/shared/api/httpClient';
import { createMockAdapter } from '@/shared/api/mocks/handlers';
import * as projectApi from '../api/projectApi';
import { ProjectListPage } from './ProjectListPage';

const ORIGINAL_ADAPTER = httpClient.defaults.adapter as AxiosAdapter | undefined;

beforeEach(() => {
  httpClient.defaults.adapter = createMockAdapter(ORIGINAL_ADAPTER);
});

afterEach(() => {
  httpClient.defaults.adapter = ORIGINAL_ADAPTER;
  vi.restoreAllMocks();
  signOut();
});

function renderPage(initial = '/app/projects') {
  return renderWithProviders(
    <Routes>
      <Route path="/app/projects" element={<ProjectListPage />} />
      {/* Row-click navigates here — a stub so the route resolves. */}
      <Route path="/app/projects/:projectId/workspace" element={<div>WORKSPACE</div>} />
    </Routes>,
    [initial],
  );
}

describe('<ProjectListPage /> CRUD', () => {
  it('renders the active tenant projects from the mock backend', async () => {
    signIn('super-admin');
    render(renderPage());
    // ACME-2026 belongs to the default mock tenant (ACME Holdings).
    expect(await screen.findByText('ACME-2026')).toBeInTheDocument();
    expect(screen.getByText('ACME-PILOT')).toBeInTheDocument();
  });

  it('edit opens the drawer prefilled and renames via updateProject WITHOUT sending code', async () => {
    signIn('super-admin');
    const updateSpy = vi.spyOn(projectApi, 'updateProject');
    const user = userEvent.setup();
    render(renderPage());

    const codeCell = await screen.findByText('ACME-2026');
    const row = codeCell.closest('tr')!;
    // Click the row's Edit (pencil) icon — not the row itself.
    await user.click(within(row).getByTestId('project-edit-proj-acme-2026'));

    // Drawer opens in EDIT mode: code is prefilled AND disabled (immutable).
    const codeInput = await screen.findByTestId('project-code');
    expect(codeInput).toHaveValue('ACME-2026');
    expect(codeInput).toBeDisabled();

    // The ru-RU name field is prefilled from name_i18n — rename it.
    const ruField = screen.getByDisplayValue('Грейдинг ACME 2026');
    await user.clear(ruField);
    await user.type(ruField, 'Грейдинг ACME 2026 v2');

    await user.click(screen.getByRole('button', { name: /Сохранить|Save|Сақлаш|Saqlash/i }));

    await waitFor(() => expect(updateSpy).toHaveBeenCalledTimes(1));
    const [, payload] = updateSpy.mock.calls[0];
    // Update command carries name_i18n but NEVER the immutable code.
    expect(payload).not.toHaveProperty('code');
    expect(payload.name_i18n?.['ru-RU']).toBe('Грейдинг ACME 2026 v2');

    // The renamed project is reflected in the list (cache invalidated → refetch).
    expect(await screen.findByText('Грейдинг ACME 2026 v2')).toBeInTheDocument();
  });

  it('archive asks for confirmation then calls archiveProject', async () => {
    signIn('super-admin');
    const archiveSpy = vi.spyOn(projectApi, 'archiveProject');
    const user = userEvent.setup();
    render(renderPage());

    const codeCell = await screen.findByText('ACME-2026');
    const row = codeCell.closest('tr')!;
    await user.click(within(row).getByTestId('project-archive-proj-acme-2026'));

    // Confirmation dialog appears; archive only fires after Confirm.
    const dialog = await screen.findByRole('dialog');
    expect(archiveSpy).not.toHaveBeenCalled();
    await user.click(within(dialog).getByRole('button', { name: /Архивировать|Archive|Архивлаш|Arxivlash/i }));

    await waitFor(() => expect(archiveSpy).toHaveBeenCalledWith('proj-acme-2026'));
  });

  it('surfaces PROJECT_CODE_TAKEN when creating with a duplicate code', async () => {
    signIn('super-admin');
    const user = userEvent.setup();
    render(renderPage());

    await screen.findByText('ACME-2026');
    await user.click(screen.getByTestId('new-project-button'));

    // Reuse an existing code → backend rejects with PROJECT_CODE_TAKEN.
    const codeInput = await screen.findByTestId('project-code');
    expect(codeInput).toBeEnabled(); // create mode → editable
    await user.type(codeInput, 'ACME-PILOT');

    // Fill the required primary name (ru-RU tab is active first).
    const nameInput = screen.getByTestId('locale-input-ru-RU');
    await user.type(nameInput, 'Дубликат');

    await user.click(screen.getByRole('button', { name: /Сохранить|Save|Сақлаш|Saqlash/i }));

    // The error banner appears and the drawer stays open (code field still shown).
    const banner = await screen.findByTestId('project-form-error');
    expect(banner).toHaveTextContent(/уже используется|already taken|ишлатилган|ishlatilgan/i);
    expect(screen.getByTestId('project-code')).toBeInTheDocument();
  });
});
