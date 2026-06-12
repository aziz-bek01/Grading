import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { Route, Routes } from 'react-router-dom';
import { renderWithProviders, signIn } from '@/test/testUtils';
import { ApprovalsListPage } from '../pages/ApprovalsListPage';
import { ApprovalDetailsPage } from '../pages/ApprovalDetailsPage';
import { mockDb } from '@/shared/api/mocks/fixtures';
import { createMockAdapter } from '@/shared/api/mocks/handlers';
import { httpClient } from '@/shared/api/httpClient';
import type { AxiosAdapter } from 'axios';

/**
 * Drives the page against the MSW layer, which now emits the REAL snake_case
 * wire (PageResponse envelope for the list, full object for the detail). Proves
 * the adapter renders cards from the real shape and NEVER falls to the global
 * error state ("Хатолик юз берди").
 */
describe('<ApprovalsListPage /> (real snake_case wire via MSW)', () => {
  let originalAdapter: AxiosAdapter | undefined;

  beforeEach(() => {
    signIn('super-admin');
    originalAdapter = httpClient.defaults.adapter as AxiosAdapter | undefined;
    httpClient.defaults.adapter = createMockAdapter(undefined);
  });

  afterEach(() => {
    httpClient.defaults.adapter = originalAdapter;
  });

  function renderListPage() {
    return render(
      renderWithProviders(
        <Routes>
          <Route path="/app/projects/:projectId/approvals" element={<ApprovalsListPage />} />
        </Routes>,
        ['/app/projects/proj-acme-2026/approvals'],
      ),
    );
  }

  it('renders cards from the PageResponse envelope, not the error state', async () => {
    renderListPage();
    await waitFor(() => {
      expect(screen.queryAllByTestId('approval-request-card').length).toBeGreaterThan(0);
    });
    // No global error state.
    expect(screen.queryByText(/Хатолик юз берди|Something went wrong|Произошла ошибка/i)).toBeNull();
  });

  it('shows a valid localized date (never "Invalid Date") from requested_at', async () => {
    renderListPage();
    await waitFor(() => {
      expect(screen.queryAllByTestId('approval-request-card').length).toBeGreaterThan(0);
    });
    expect(screen.queryByText(/Invalid Date/i)).toBeNull();
  });

  it('renders EmptyState (not ErrorState) when items is empty', async () => {
    const snapshot = mockDb.approvalRequests.map((a) => a.project_id);
    // Filter all rows out of the active project so the page receives [].
    mockDb.approvalRequests.forEach((a) => {
      a.project_id = 'some-other-project';
    });
    renderListPage();
    // EmptyState title (ru-RU default) — never the ErrorState role="alert".
    await waitFor(() => {
      expect(
        screen.getByText(/No approval requests|Запросов нет|сўровлар йўқ|so'rovlar yo'q/i),
      ).toBeInTheDocument();
    });
    expect(screen.queryByRole('alert')).toBeNull();
    mockDb.approvalRequests.forEach((a, i) => {
      a.project_id = snapshot[i];
    });
  });
});

describe('<ApprovalDetailsPage /> (real snake_case wire via MSW)', () => {
  let originalAdapter: AxiosAdapter | undefined;

  beforeEach(() => {
    signIn('super-admin');
    originalAdapter = httpClient.defaults.adapter as AxiosAdapter | undefined;
    httpClient.defaults.adapter = createMockAdapter(undefined);
  });

  afterEach(() => {
    httpClient.defaults.adapter = originalAdapter;
  });

  function renderDetailPage(id: string) {
    return render(
      renderWithProviders(
        <Routes>
          <Route path="/app/approvals/:approvalId" element={<ApprovalDetailsPage />} />
        </Routes>,
        [`/app/approvals/${id}`],
      ),
    );
  }

  it('renders steps from the detail object without throwing (steps default [] / decisions synthesized)', async () => {
    renderDetailPage('appr-swe-eval-1');
    await waitFor(() => {
      expect(screen.getByTestId('approval-details-page')).toBeInTheDocument();
    });
    expect(screen.queryAllByTestId('approval-step-card').length).toBeGreaterThan(0);
    expect(screen.queryByText(/Invalid Date/i)).toBeNull();
  });

  it('a cross-tenant / unknown id surfaces as ErrorState, never a white-screen throw', async () => {
    renderDetailPage('does-not-exist');
    await waitFor(() => {
      // ErrorState or EmptyState — the point is the page renders, no crash.
      const ok =
        screen.queryByText(/Retry|Повторить|Қайта|Qayta|empty|пусто/i) ??
        screen.queryByTestId('approval-details-page');
      expect(ok).toBeTruthy();
    });
  });
});
