import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { Routes, Route } from 'react-router-dom';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import { useAuthStore } from '@/features/auth/authStore';
import { PERMISSIONS } from '@/shared/types/permissions';
import { httpClient } from '@/shared/api/httpClient';
import { ApiError } from '@/shared/api/apiError';
import { ReportsCenterPage } from '../pages/ReportsCenterPage';

const PROJECT_ID = '11111111-1111-1111-1111-111111111111';

/**
 * Real backend wire shape: global SNAKE_CASE with `@JsonInclude(NON_NULL)`.
 * Feeding the page snake_case here exercises `normalizeReport` against the
 * exact shape the backend serialises (FE-7 regression).
 */
function makeReportWire(overrides: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    id: 'rep-1',
    project_id: PROJECT_ID,
    report_type: 'GRADE_DISTRIBUTION',
    format: 'PDF',
    status: 'GENERATED',
    title: 'Grade distribution — ACME Grading 2026',
    locale: 'ru-RU',
    requested_by: '00000000-0000-0000-0000-000000000001',
    requested_at: '2026-05-23T08:15:00Z',
    generated_at: '2026-05-23T08:15:12Z',
    expires_at: '2026-06-06T08:15:12Z',
    downloaded_at: null,
    file_size: 12345,
    contains_salary_data: false,
    contains_personal_data: false,
    attempt_count: 1,
    failure_reason: null,
    trace_id: null,
    ...overrides,
  };
}

function mountAt(path = `/app/projects/${PROJECT_ID}/reports`) {
  return render(
    renderWithProviders(
      <Routes>
        <Route path="/app/projects/:projectId/reports" element={<ReportsCenterPage />} />
      </Routes>,
      [path],
    ),
  );
}

describe('ReportsCenterPage', () => {
  beforeEach(() => {
    signIn('super-admin');
  });
  afterEach(() => {
    signOut();
    vi.restoreAllMocks();
  });

  it('renders the page title, list rows, and the "+ Request report" CTA for users with REPORT_CREATE', async () => {
    vi.spyOn(httpClient, 'get').mockResolvedValue({
      data: {
        items: [
          makeReportWire({ id: 'rep-1', title: 'Grade distribution — ACME Grading 2026' }),
          makeReportWire({
            id: 'rep-2',
            title: 'Methodology spec — CFO Finance v1',
            report_type: 'METHODOLOGY_SPEC',
            status: 'GENERATING',
            generated_at: null,
            expires_at: null,
          }),
        ],
        page: 0,
        size: 2,
        total_elements: 2,
        total_pages: 1,
      },
    } as Awaited<ReturnType<typeof httpClient.get>>);

    mountAt();
    expect(screen.getByTestId('reports-center-page')).toBeInTheDocument();
    // CTA visible for the super-admin role (carries REPORT_CREATE).
    expect(screen.getByTestId('report-new-button')).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText('Grade distribution — ACME Grading 2026')).toBeInTheDocument();
    });
    expect(screen.getByText('Methodology spec — CFO Finance v1')).toBeInTheDocument();
    expect(screen.getAllByTestId('report-row')).toHaveLength(2);
    // FE-7 / AC10: the snake_case `requested_at` is normalized, so no row
    // shows the literal "Invalid Date" badge.
    expect(screen.queryByText(/Invalid Date/)).toBeNull();
  });

  it('hides the "+ Request report" CTA when REPORT_CREATE is missing', async () => {
    // Strip REPORT_CREATE from the active user permissions.
    const session = useAuthStore.getState();
    const u = session.user!;
    useAuthStore.getState().setSession(
      {
        ...u,
        permissions: u.permissions.filter((p) => p !== PERMISSIONS.REPORT_CREATE),
      },
      { value: 'test', expiresAt: Date.now() + 60_000 },
    );

    vi.spyOn(httpClient, 'get').mockResolvedValue({
      data: { items: [], page: 0, size: 0, totalElements: 0, totalPages: 0 },
    } as Awaited<ReturnType<typeof httpClient.get>>);

    mountAt();
    expect(screen.queryByTestId('report-new-button')).toBeNull();
  });

  it('passes the selected status filter to the API request', async () => {
    const getSpy = vi.spyOn(httpClient, 'get').mockResolvedValue({
      data: { items: [], page: 0, size: 0, totalElements: 0, totalPages: 0 },
    } as Awaited<ReturnType<typeof httpClient.get>>);

    mountAt();
    await waitFor(() => expect(getSpy).toHaveBeenCalled());

    fireEvent.change(screen.getByTestId('report-filter-status'), {
      target: { value: 'GENERATED' },
    });

    await waitFor(() => {
      const lastCall = getSpy.mock.calls[getSpy.mock.calls.length - 1];
      const params = (lastCall[1] as { params?: Record<string, unknown> } | undefined)?.params;
      expect(params).toMatchObject({ status: 'GENERATED', projectId: PROJECT_ID });
    });
  });

  // FE-018: a 403/500/network failure used to fall through to the same
  // "no items" branch as a genuinely empty list, rendering the misleading
  // "No reports yet" copy. The page now reuses the shared ErrorState (with a
  // retry) instead.
  it('renders the retryable ErrorState (not the misleading empty copy) on a 403', async () => {
    vi.spyOn(httpClient, 'get').mockRejectedValue(
      new ApiError(403, { code: 'ACCESS_DENIED', message: 'forbidden' }),
    );

    mountAt();
    await waitFor(() => {
      expect(screen.getByRole('alert')).toBeInTheDocument();
    });
    expect(screen.queryByText(/No reports yet|Пока нет отчётов/i)).toBeNull();
  });

  it('renders the retryable ErrorState (not the misleading empty copy) on a 500', async () => {
    vi.spyOn(httpClient, 'get').mockRejectedValue(
      new ApiError(500, { code: 'INTERNAL_ERROR', message: 'boom' }),
    );

    mountAt();
    await waitFor(() => {
      expect(screen.getByRole('alert')).toBeInTheDocument();
    });
    expect(screen.queryByText(/No reports yet|Пока нет отчётов/i)).toBeNull();
  });
});
