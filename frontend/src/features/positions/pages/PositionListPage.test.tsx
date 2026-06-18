/**
 * Positions CRUD page tests.
 *
 * Mirrors the pattern from ProjectListPage.test.tsx:
 * - edit/rename wired (update mutation called, code NOT sent)
 * - archive shows confirm dialog then calls archivePosition
 * - duplicate code conflict (POSITION_CODE_TAKEN) surfaces error banner in drawer
 *
 * NOTE: mockDb is a module-level singleton that persists across tests. Each
 * test targets a DIFFERENT position to avoid cross-test state contamination.
 * Tests are ordered so mutations do not affect later tests' initial assertions.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Routes, Route } from 'react-router-dom';
import type { AxiosAdapter } from 'axios';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import { httpClient } from '@/shared/api/httpClient';
import { createMockAdapter } from '@/shared/api/mocks/handlers';
import * as positionApi from '../api/positionApi';
import { PositionListPage } from './PositionListPage';

const ORIGINAL_ADAPTER = httpClient.defaults.adapter as AxiosAdapter | undefined;

beforeEach(() => {
  httpClient.defaults.adapter = createMockAdapter(ORIGINAL_ADAPTER);
});

afterEach(() => {
  httpClient.defaults.adapter = ORIGINAL_ADAPTER;
  vi.restoreAllMocks();
  signOut();
});

function renderPage(projectId = 'proj-acme-2026') {
  return renderWithProviders(
    <Routes>
      <Route path="/app/projects/:projectId/positions" element={<PositionListPage />} />
      <Route
        path="/app/projects/:projectId/positions/:positionId"
        element={<div>POSITION DETAIL</div>}
      />
    </Routes>,
    [`/app/projects/${projectId}/positions`],
  );
}

describe('<PositionListPage /> CRUD', () => {
  it('renders positions from the mock backend for the active project', async () => {
    signIn('super-admin');
    render(renderPage());
    // CFO is one of the seed positions for proj-acme-2026
    expect(await screen.findByText('Финансовый директор')).toBeInTheDocument();
    // CTO is another seed position
    expect(screen.getByText('Технический директор')).toBeInTheDocument();
  });

  it('edit opens the drawer prefilled and renames via updatePosition WITHOUT sending code', async () => {
    signIn('super-admin');
    const updateSpy = vi.spyOn(positionApi, 'updatePosition');
    const user = userEvent.setup();
    render(renderPage());

    // Use CTO to avoid conflict with the first test's CFO assertion
    const titleCell = await screen.findByText('Технический директор');
    const row = titleCell.closest('tr')!;
    await user.click(within(row).getByTestId('position-edit-pos-cto'));

    // Drawer opens in EDIT mode: code is prefilled AND disabled (immutable)
    const codeInput = await screen.findByTestId('pos-code');
    expect(codeInput).toHaveValue('CTO');
    expect(codeInput).toBeDisabled();

    // The ru-RU title is prefilled — change it
    const ruField = screen.getByDisplayValue('Технический директор');
    await user.clear(ruField);
    await user.type(ruField, 'Технический директор v2');

    await user.click(screen.getByRole('button', { name: /Сохранить|Save|Сақлаш|Saqlash/i }));

    await waitFor(() => expect(updateSpy).toHaveBeenCalledTimes(1));
    const [id, payload] = updateSpy.mock.calls[0];
    expect(id).toBe('pos-cto');
    // PATCH payload must NOT include code
    expect(payload).not.toHaveProperty('code');
    expect(payload.title_i18n?.['ru-RU']).toBe('Технический директор v2');
  });

  it('archive asks for confirmation then calls archivePosition', async () => {
    signIn('super-admin');
    const archiveSpy = vi.spyOn(positionApi, 'archivePosition');
    const user = userEvent.setup();
    render(renderPage());

    // Use TREAS-HEAD to avoid conflict with renamed CTO or CFO tests
    const titleCell = await screen.findByText('Руководитель казначейства');
    const row = titleCell.closest('tr')!;
    await user.click(within(row).getByTestId('position-archive-pos-treas-head'));

    // Confirmation dialog appears; archive only fires after Confirm
    const dialog = await screen.findByRole('dialog');
    expect(archiveSpy).not.toHaveBeenCalled();
    await user.click(
      within(dialog).getByRole('button', { name: /Архивировать|Archive|Архивлаш|Arxivlash/i }),
    );

    await waitFor(() => {
      expect(archiveSpy).toHaveBeenCalledTimes(1);
      // The mutation runtime passes extra context args — check only the first arg (the id).
      expect(archiveSpy.mock.calls[0][0]).toBe('pos-treas-head');
    });
  });

  it('surfaces POSITION_CODE_TAKEN when creating with a duplicate code', async () => {
    signIn('super-admin');
    const user = userEvent.setup();
    render(renderPage());

    // Wait for data to load
    await screen.findByText('Финансовый директор');
    await user.click(screen.getByTestId('new-position-button'));

    // Code field is editable in create mode
    const codeInput = await screen.findByTestId('pos-code');
    expect(codeInput).toBeEnabled();
    await user.type(codeInput, 'CFO'); // duplicate code → backend rejects

    // Select a department
    await user.selectOptions(screen.getByTestId('pos-department-select'), 'dep-acme-fin');

    // Fill required title
    const nameInput = screen.getByTestId('locale-input-ru-RU');
    await user.type(nameInput, 'Дубликат');

    await user.click(screen.getByRole('button', { name: /Сохранить|Save|Сақлаш|Saqlash/i }));

    // The error banner appears and the drawer stays open (code field still shown)
    const banner = await screen.findByTestId('position-form-error');
    expect(banner).toHaveTextContent(/уже используется|already in use|ишлатилган|ishlatilgan/i);
    expect(screen.getByTestId('pos-code')).toBeInTheDocument();
  });
});
