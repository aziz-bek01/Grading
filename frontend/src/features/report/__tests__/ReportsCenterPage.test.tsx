import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { Routes, Route } from 'react-router-dom';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import { useAuthStore } from '@/features/auth/authStore';
import { PERMISSIONS } from '@/shared/types/permissions';
import { httpClient } from '@/shared/api/httpClient';
import { ReportsCenterPage } from '../pages/ReportsCenterPage';
import type { Report } from '../types';

const PROJECT_ID = '11111111-1111-1111-1111-111111111111';

function makeReport(overrides: Partial<Report> = {}): Report {
  return {
    id: 'rep-1',
    projectId: PROJECT_ID,
    reportType: 'GRADE_DISTRIBUTION',
    format: 'PDF',
    status: 'GENERATED',
    title: 'Grade distribution — ACME Grading 2026',
    locale: 'ru-RU',
    requestedBy: '00000000-0000-0000-0000-000000000001',
    requestedAt: '2026-05-23T08:15:00Z',
    generatedAt: '2026-05-23T08:15:12Z',
    expiresAt: '2026-06-06T08:15:12Z',
    downloadedAt: null,
    fileSize: 12345,
    containsSalaryData: false,
    containsPersonalData: false,
    attemptCount: 1,
    failureReason: null,
    traceId: null,
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
          makeReport({ id: 'rep-1', title: 'Grade distribution — ACME Grading 2026' }),
          makeReport({
            id: 'rep-2',
            title: 'Methodology spec — CFO Finance v1',
            reportType: 'METHODOLOGY_SPEC',
            status: 'GENERATING',
            generatedAt: null,
            expiresAt: null,
          }),
        ],
        page: 0,
        size: 2,
        totalElements: 2,
        totalPages: 1,
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
});
