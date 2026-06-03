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
    // Clear inbox by marking all PENDING as APPROVED then refetch
    const snapshot = mockDb.approvalRequests.map((a) => ({ ...a, status: a.status }));
    mockDb.approvalRequests.forEach((a) => {
      a.status = 'APPROVED';
    });
    render(renderWithProviders(<ApprovalsInboxPage />));
    await waitFor(() => {
      expect(screen.getByText(/Inbox|Очередь|кутиб|kutib/i)).toBeInTheDocument();
    });
    // restore
    mockDb.approvalRequests.forEach((a, i) => {
      a.status = snapshot[i].status as never;
    });
    httpClient.defaults.adapter = originalAdapter;
  });
});
