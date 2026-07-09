/**
 * FE-018 regression: ExportCenterPage previously had no error-state handling
 * — a 403/500/network failure fell through to the same "no items" branch as
 * a genuinely empty list, rendering the misleading "No exports yet" copy.
 * It now reuses the shared LoadingState / EmptyState / ErrorState components
 * (the same pattern PositionListPage / UsersListPage already use correctly):
 * loading -> LoadingState, error -> ErrorState with a retry, empty -> EmptyState.
 */
import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { Routes, Route } from 'react-router-dom';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import { httpClient } from '@/shared/api/httpClient';
import { ApiError } from '@/shared/api/apiError';
import { ExportCenterPage } from './ExportCenterPage';

const PROJECT_ID = '11111111-1111-1111-1111-111111111111';

function makeExportWire(overrides: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    id: 'exp-1',
    project_id: PROJECT_ID,
    export_type: 'POSITION_CATALOG',
    format: 'XLSX',
    status: 'GENERATED',
    requested_by: '00000000-0000-0000-0000-000000000001',
    requested_at: '2026-05-23T08:15:00Z',
    generated_at: '2026-05-23T08:15:12Z',
    expires_at: '2026-06-06T08:15:12Z',
    downloaded_at: null,
    file_size: 12345,
    contains_salary_data: false,
    contains_personal_data: false,
    ...overrides,
  };
}

function mountAt(path = `/app/projects/${PROJECT_ID}/exports`) {
  return render(
    renderWithProviders(
      <Routes>
        <Route path="/app/projects/:projectId/exports" element={<ExportCenterPage />} />
      </Routes>,
      [path],
    ),
  );
}

describe('ExportCenterPage', () => {
  beforeEach(() => {
    signIn('super-admin');
  });
  afterEach(() => {
    signOut();
    vi.restoreAllMocks();
  });

  it('renders rows on success (no ErrorState, no empty copy)', async () => {
    vi.spyOn(httpClient, 'get').mockResolvedValue({
      data: {
        items: [makeExportWire()],
        page: 0,
        size: 1,
        total_elements: 1,
        total_pages: 1,
      },
    } as Awaited<ReturnType<typeof httpClient.get>>);

    mountAt();
    await waitFor(() => {
      expect(screen.getAllByRole('row').length).toBeGreaterThan(1);
    });
    expect(screen.queryByRole('alert')).toBeNull();
    expect(screen.queryByText(/No exports yet|Экспортов ещё нет/i)).toBeNull();
  });

  it('renders EmptyState (not ErrorState) for a genuinely empty list', async () => {
    vi.spyOn(httpClient, 'get').mockResolvedValue({
      data: { items: [], page: 0, size: 0, total_elements: 0, total_pages: 0 },
    } as Awaited<ReturnType<typeof httpClient.get>>);

    mountAt();
    await waitFor(() => {
      expect(screen.getByTestId('export-center-page')).toBeInTheDocument();
    });
    expect(screen.queryByRole('alert')).toBeNull();
  });

  it('renders the retryable ErrorState (not the misleading empty copy) on a 403', async () => {
    vi.spyOn(httpClient, 'get').mockRejectedValue(
      new ApiError(403, { code: 'ACCESS_DENIED', message: 'forbidden' }),
    );

    mountAt();
    await waitFor(() => {
      expect(screen.getByRole('alert')).toBeInTheDocument();
    });
    expect(screen.queryByText(/No exports yet|Экспортов ещё нет/i)).toBeNull();
  });

  it('renders the retryable ErrorState (not the misleading empty copy) on a 500', async () => {
    vi.spyOn(httpClient, 'get').mockRejectedValue(
      new ApiError(500, { code: 'INTERNAL_ERROR', message: 'boom' }),
    );

    mountAt();
    await waitFor(() => {
      expect(screen.getByRole('alert')).toBeInTheDocument();
    });
    expect(screen.queryByText(/No exports yet|Экспортов ещё нет/i)).toBeNull();
  });
});
