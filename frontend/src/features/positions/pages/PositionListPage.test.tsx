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
import { mockDb, type MockPosition } from '@/shared/api/mocks/fixtures';
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

  // PAGE-2: the "Job profile" column reuses the Position Catalog (no
  // duplicate "Job Profiles" page) — the sidebar / workflow-stepper "Job
  // Profiles" destination and the "Positions" destination are the SAME page,
  // but this column is what makes that destination meaningful for job
  // profiles instead of a generic list with no job-profile signal.
  //
  // Runs BEFORE the rename test below — it targets CFO/CTO by their PRISTINE
  // seed titles, and the next test renames CTO (mockDb is a persistent
  // module-level singleton across this file's tests; see the header note).
  it('surfaces job profile status per position (fixture: CFO has an approved profile, CTO has none) via a SINGLE bulk request', async () => {
    signIn('super-admin');
    // Call-through spy (no mockImplementation) — asserts the WIRE behavior
    // without altering it: the former per-position fan-out issued one
    // `GET /positions/:id/job-profile` per row; the bulk endpoint issues
    // exactly one `GET /job-profiles/statuses` for the whole page.
    const getSpy = vi.spyOn(httpClient, 'get');
    render(renderPage());

    const cfoCell = await screen.findByText('Финансовый директор');
    const cfoRow = cfoCell.closest('tr')!;
    await waitFor(() =>
      expect(within(cfoRow).getByText(/Утверждено|Approved/i)).toBeInTheDocument(),
    );

    const ctoCell = screen.getByText('Технический директор');
    const ctoRow = ctoCell.closest('tr')!;
    await waitFor(() =>
      expect(within(ctoRow).getByText(/Отсутствует|Missing/i)).toBeInTheDocument(),
    );

    const statusesCalls = getSpy.mock.calls.filter(([url]) =>
      String(url).includes('/job-profiles/statuses'),
    );
    expect(statusesCalls).toHaveLength(1);
    const perPositionFanOutCalls = getSpy.mock.calls.filter(([url]) =>
      /\/positions\/[^/]+\/job-profile$/.test(String(url)),
    );
    expect(perPositionFanOutCalls).toHaveLength(0);
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

/**
 * EPIC-013 regression guard: the Position Catalog used to fetch a
 * HARD-CODED `page: 0, size: 50` that never changed — anything past the
 * first 50 positions was invisible while the table still read "Page 1 of 1".
 * `page` is now real state wired to `PaginationBar` via `DataTable`'s
 * `serverPagination` prop, so a second GET with `page=1` is what surfaces
 * the rest — this locks that in against a regression back to a fetch-once
 * page.
 */
describe('<PositionListPage /> real server pagination (EPIC-013)', () => {
  const injectedIds: string[] = [];

  afterEach(() => {
    // mockDb is a module-level singleton (see the header note above) — undo
    // the seed so later tests/files are unaffected.
    for (const id of injectedIds.splice(0)) {
      const idx = mockDb.positions.findIndex((p) => p.id === id);
      if (idx >= 0) mockDb.positions.splice(idx, 1);
    }
  });

  it('a row on the LAST server page is only reachable by paging through Next (page actually changes)', async () => {
    signIn('super-admin');
    // proj-acme-2026 already seeds a few dozen ACTIVE positions (well beyond
    // one page) via other fixture sets — push 20 MORE distinctly-titled rows
    // so the project definitely spans multiple pages. The target row is
    // pushed LAST, so it is guaranteed to land on the LAST server page
    // (array order is preserved end-to-end by filter/paginate), regardless
    // of exactly how many fixture rows precede it.
    const TARGET_TITLE = 'Позиция EPIC013 — последняя строка';
    for (let i = 0; i < 20; i++) {
      const id = `pos-epic013-${i}`;
      const seed: MockPosition = {
        id,
        project_id: 'proj-acme-2026',
        department_id: 'dep-acme-fin',
        code: `P013-${i}`,
        title_i18n: {
          'ru-RU': i === 19 ? TARGET_TITLE : `Позиция EPIC013 №${i}`,
        },
        status: 'ACTIVE',
        updated_at: '2026-05-01T00:00:00Z',
      };
      mockDb.positions.push(seed);
      injectedIds.push(id);
    }

    render(renderPage());

    // Wait for the FIRST real fetch to resolve (not just the shell to mount)
    // — a known, always-present row proves the table has actual server data,
    // not the pre-fetch "total_pages ?? 1" default.
    await screen.findByText('Финансовый директор');

    // The pager now honestly reports the real page count instead of the old
    // hard-coded "Page 1 of 1" lie — read it back to know how many times to
    // click Next (no assumption baked in about exactly how many fixture rows
    // exist elsewhere in the shared mock DB).
    const pagerMatch = () =>
      /Страница (\d+) из (\d+)/.exec(
        screen.getByText(/Страница \d+ из \d+/).textContent ?? '',
      );
    const initialTotalPages = Number(pagerMatch()?.[2] ?? '1');
    expect(initialTotalPages).toBeGreaterThan(1);

    // The last-pushed row must NOT be visible on page 1 — a fetch-once page
    // (the old bug) would never even issue the second request.
    expect(screen.queryByText(TARGET_TITLE)).not.toBeInTheDocument();

    const user = userEvent.setup();
    for (let i = 1; i < initialTotalPages; i++) {
      await user.click(screen.getByRole('button', { name: /Далее|Next/i }));
    }

    await waitFor(() =>
      expect(screen.getByText(TARGET_TITLE)).toBeInTheDocument(),
    );
    // Landed on the actual last page (not just "some later page").
    const finalMatch = pagerMatch();
    expect(finalMatch?.[1]).toBe(String(initialTotalPages));
  });
});
