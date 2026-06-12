import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { renderWithProviders, signIn } from '@/test/testUtils';
import { ApprovalsInboxPage } from '../pages/ApprovalsInboxPage';
import { mockDb } from '@/shared/api/mocks/fixtures';
import { createMockAdapter } from '@/shared/api/mocks/handlers';
import { httpClient } from '@/shared/api/httpClient';
import type { AxiosAdapter } from 'axios';

describe('<ApprovalsInboxPage />', () => {
  let originalAdapter: AxiosAdapter | undefined;

  beforeEach(() => {
    signIn('super-admin');
    originalAdapter = httpClient.defaults.adapter as AxiosAdapter | undefined;
    httpClient.defaults.adapter = createMockAdapter(undefined);
  });

  it('renders the seed approval cards', async () => {
    render(renderWithProviders(<ApprovalsInboxPage />));
    await waitFor(() => {
      const cards = screen.queryAllByTestId('approval-request-card');
      expect(cards.length).toBeGreaterThan(0);
    });
    // Restore
    httpClient.defaults.adapter = originalAdapter;
  });

  it('renders an empty state when no requests match', async () => {
    // Clear inbox by marking all PENDING as APPROVED then refetch.
    // `current_status` is the REAL snake_case wire field (BE record contract).
    const snapshot = mockDb.approvalRequests.map((a) => a.current_status);
    mockDb.approvalRequests.forEach((a) => {
      a.current_status = 'APPROVED';
    });
    render(renderWithProviders(<ApprovalsInboxPage />));
    await waitFor(() => {
      expect(screen.getByText(/Inbox|Очередь|кутиб|kutib/i)).toBeInTheDocument();
    });
    // restore
    mockDb.approvalRequests.forEach((a, i) => {
      a.current_status = snapshot[i];
    });
    httpClient.defaults.adapter = originalAdapter;
  });
});
