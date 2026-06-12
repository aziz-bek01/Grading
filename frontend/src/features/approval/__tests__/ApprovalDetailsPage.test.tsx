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

  // AC4: a genuine 404 / unknown id surfaces the retryable ErrorState (not a
  // white screen, not NoAccess).
  it('shows the ErrorState for an unknown id (404)', async () => {
    mountAt('/app/approvals/does-not-exist');
    await waitFor(() => {
      expect(screen.getByRole('alert')).toBeInTheDocument();
    });
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
