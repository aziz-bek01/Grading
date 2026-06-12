import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { Routes, Route } from 'react-router-dom';
import {
  renderWithProviders,
  signInWithPermissions,
  signOut,
} from '@/test/testUtils';
import { ApprovalDetailsPage } from '@/features/approval/pages/ApprovalDetailsPage';
import { PERMISSIONS } from '@/shared/types/permissions';
import { createMockAdapter } from '@/shared/api/mocks/handlers';
import { httpClient } from '@/shared/api/httpClient';
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

describe('CEO approval — EVALUATION_PANEL detail (FE-6)', () => {
  let originalAdapter: AxiosAdapter | undefined;

  beforeEach(() => {
    originalAdapter = httpClient.defaults.adapter as AxiosAdapter | undefined;
    httpClient.defaults.adapter = createMockAdapter(undefined);
  });

  afterEach(() => {
    httpClient.defaults.adapter = originalAdapter;
    signOut();
    vi.restoreAllMocks();
  });

  it('renders the localized panel label (not a UUID) and the averaged summary block', async () => {
    signInWithPermissions([
      PERMISSIONS.APPROVAL_REQUEST_DECIDE,
      PERMISSIONS.EVALUATION_PANEL_APPROVE,
      PERMISSIONS.EVALUATION_READ,
      PERMISSIONS.CAMPAIGN_RESULTS_VIEW,
    ]);
    mountAt('/app/approvals/appr-panel-cfo-1');

    await waitFor(() =>
      expect(screen.getByTestId('approval-details-page')).toBeInTheDocument(),
    );

    // Resolved EVALUATION_PANEL label (ru-RU) — position + methodology + "среднее".
    const heading = screen.getByRole('heading', { level: 1 });
    expect(heading).toHaveTextContent('Финансовый директор · CFO Finance v1 · среднее');
    expect(heading).not.toHaveTextContent('panel-cfo-averaged');

    // The read-only campaign result summary block + the per-factor table.
    await waitFor(() =>
      expect(screen.getByTestId('panel-approval-summary')).toBeInTheDocument(),
    );
    await waitFor(() =>
      expect(screen.getByTestId('panel-averaged-result')).toBeInTheDocument(),
    );
    expect(screen.getByTestId('panel-result-total')).toBeInTheDocument();
  });

  it('shows the locked placeholder in the CEO summary when the viewer lacks CAMPAIGN_RESULTS_VIEW', async () => {
    // CEO can decide but cannot view per-evaluator results → the summary block
    // degrades to no-access for the result table, never a partial average.
    signInWithPermissions([
      PERMISSIONS.APPROVAL_REQUEST_DECIDE,
      PERMISSIONS.EVALUATION_PANEL_APPROVE,
      PERMISSIONS.EVALUATION_READ,
    ]);
    mountAt('/app/approvals/appr-panel-cfo-1');

    await waitFor(() =>
      expect(screen.getByTestId('panel-approval-summary')).toBeInTheDocument(),
    );
    // The averaged table is NOT rendered without the results permission.
    expect(screen.queryByTestId('panel-averaged-result')).not.toBeInTheDocument();
  });
});
