import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { Routes, Route } from 'react-router-dom';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import { ApprovalDetailsPage } from '../pages/ApprovalDetailsPage';
import { createMockAdapter } from '@/shared/api/mocks/handlers';
import { httpClient } from '@/shared/api/httpClient';
import { ApiError } from '@/shared/api/apiError';
import type { AxiosAdapter } from 'axios';

function mountAt(path: string) {
  return render(
    renderWithProviders(
      <Routes>
        <Route path="/app/approvals/:approvalId" element={<ApprovalDetailsPage />} />
      </Routes>,
      [path],
    ),
  );
}

describe('<ApprovalDetailsPage />', () => {
  let originalAdapter: AxiosAdapter | undefined;

  beforeEach(() => {
    signIn('super-admin');
    originalAdapter = httpClient.defaults.adapter as AxiosAdapter | undefined;
    httpClient.defaults.adapter = createMockAdapter(undefined);
  });

  afterEach(() => {
    httpClient.defaults.adapter = originalAdapter;
    signOut();
    vi.restoreAllMocks();
  });

  // AC1 / AC2 / AC4: enriched detail renders the localized human label +
  // approver name from the REAL-WIRE seed fixture (snake_case), not a UUID,
  // and the global "Хатолик юз берди" error is NOT shown. Tests run in ru-RU.
  it('renders the localized entity label and approver name (not a UUID)', async () => {
    mountAt('/app/approvals/appr-swe-eval-1');

    await waitFor(() => {
      expect(screen.getByTestId('approval-details-page')).toBeInTheDocument();
    });
    // Localized (ru-RU) EVALUATION label = "<position> · <methodology> v<n>"
    const heading = screen.getByRole('heading', { level: 1 });
    expect(heading).toHaveTextContent('Старший разработчик · CFO Finance v1');
    // The raw entity_id UUID is NOT the title.
    expect(heading).not.toHaveTextContent('eval-swe-1');
    // BE-resolved approver name on the step card (not a UUID).
    expect(screen.getByText('Dev User')).toBeInTheDocument();
  });

  // Cross-project inbox fix: the detail header shows WHICH project the
  // request belongs to via the BE-resolved project_label_i18n (mirrors
  // entity_label_i18n), rendered above the entity title.
  it('renders the localized project label above the entity title', async () => {
    mountAt('/app/approvals/appr-swe-eval-1');
    await waitFor(() => {
      expect(screen.getByTestId('approval-details-page')).toBeInTheDocument();
    });
    expect(screen.getByTestId('approval-detail-project-label')).toHaveTextContent(
      'Проект · Acme HRTech 2026',
    );
  });

  // ...and gracefully omits the line when the backend has not resolved a
  // project label for this request (NON_NULL omission, no crash).
  it('omits the project label line when project_label_i18n is not resolved', async () => {
    mountAt('/app/approvals/appr-cfo-jp-1');
    await waitFor(() => {
      expect(screen.getByTestId('approval-details-page')).toBeInTheDocument();
    });
    expect(screen.queryByTestId('approval-detail-project-label')).toBeNull();
  });

  // AC-2: a 404 (BE maps TenantAccessDeniedException → 404, e.g. a stale inbox
  // row opened after a tenant/context change) surfaces the SAME calm,
  // non-enumerating NoAccessState as a 403 — NOT the retryable "Что-то пошло не
  // так / ID запроса" crash card. Deliberate behavior change (consolidated
  // detail-404 handling; previously this asserted ErrorState).
  it('shows NoAccessState (not ErrorState) for an unknown id (404)', async () => {
    mountAt('/app/approvals/does-not-exist');
    await waitFor(() => {
      expect(screen.getByText('Нет доступа')).toBeInTheDocument();
    });
    // No generic retryable error card and no leaked correlation id.
    expect(screen.queryByRole('alert')).toBeNull();
    expect(screen.queryByText(/ID запроса/)).toBeNull();
  });

  // AC-3 boundary: a transport/network error (status 0) and a 5xx are GENUINELY
  // unexpected — they must still render the retryable ErrorState (role="alert"
  // + retry control). This pins the boundary so the NoAccess branch above can't
  // be over-broadened to swallow real failures later.
  it('shows the retryable ErrorState on a network error (status 0)', async () => {
    vi.spyOn(httpClient, 'get').mockRejectedValue(
      new ApiError(0, { code: 'NETWORK_ERROR', message: 'Network Error' }),
    );
    mountAt('/app/approvals/appr-swe-eval-1');
    await waitFor(() => {
      expect(screen.getByRole('alert')).toBeInTheDocument();
    });
    expect(screen.getByText('Повторить')).toBeInTheDocument();
    expect(screen.queryByText('Нет доступа')).toBeNull();
  });

  it('shows the retryable ErrorState on a 500', async () => {
    vi.spyOn(httpClient, 'get').mockRejectedValue(
      new ApiError(500, { code: 'INTERNAL_ERROR', message: 'boom' }),
    );
    mountAt('/app/approvals/appr-swe-eval-1');
    await waitFor(() => {
      expect(screen.getByRole('alert')).toBeInTheDocument();
    });
    expect(screen.queryByText('Нет доступа')).toBeNull();
  });

  // FE-5 / AC4: a genuine 403 surfaces a SPECIFIC NoAccessState, never the
  // generic error alert.
  it('shows NoAccessState (not ErrorState) on a 403', async () => {
    vi.spyOn(httpClient, 'get').mockRejectedValue(
      new ApiError(403, { code: 'ACCESS_DENIED', message: 'forbidden' }),
    );
    mountAt('/app/approvals/appr-swe-eval-1');
    // Tests run in ru-RU — NoAccessState renders the localized title.
    await waitFor(() => {
      expect(screen.getByText('Нет доступа')).toBeInTheDocument();
    });
    // The generic retryable error alert must NOT be present.
    expect(screen.queryByRole('alert')).toBeNull();
  });
});
