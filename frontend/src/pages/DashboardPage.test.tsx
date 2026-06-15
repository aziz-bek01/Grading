import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import { DashboardPage } from './DashboardPage';
import * as analyticsApi from '@/features/dashboard/api/analyticsApi';

describe('<DashboardPage />', () => {
  beforeEach(() => {
    signOut();
    vi.restoreAllMocks();
  });

  it('wires StatCards to the portfolio-summary endpoint (real counts, not "—")', async () => {
    // Signed-in super admin → active tenant = ACME (11111111…), which has 2
    // projects + 1 methodology in the MSW fixtures. The mock analytics handler
    // computes these tenant-scoped counts.
    signIn('super-admin');
    render(renderWithProviders(<DashboardPage />));

    const projectsCard = await screen.findByTestId('stat-projects');
    // Wait for the async summary to resolve and replace the skeleton with the
    // real count. The fixture tenant (ACME) has 2 projects + 1 methodology.
    await waitFor(() => {
      expect(within(projectsCard).queryByTestId('stat-skeleton')).toBeNull();
      expect(projectsCard).toHaveTextContent('2');
    });
    expect(screen.getByTestId('stat-methodology')).toHaveTextContent('1');
  });

  it('shows a loading skeleton while the summary is pending', () => {
    signIn('super-admin');
    // Keep the query pending forever so the skeleton is asserted deterministically.
    vi.spyOn(analyticsApi, 'fetchPortfolioSummary').mockReturnValue(
      new Promise(() => {}),
    );
    render(renderWithProviders(<DashboardPage />));
    expect(screen.getAllByTestId('stat-skeleton').length).toBeGreaterThan(0);
    expect(screen.getByTestId('projects-card-skeleton')).toBeInTheDocument();
  });

  it('renders the Projects empty state + CTA when the tenant has no projects', async () => {
    signIn('super-admin');
    vi.spyOn(analyticsApi, 'fetchPortfolioSummary').mockResolvedValue({
      tenantId: '11111111-1111-1111-1111-111111111111',
      clientCompanyCount: 0,
      projectCount: 0,
      methodologyCount: 0,
      lastActivityAt: null,
    });
    render(renderWithProviders(<DashboardPage />));

    // CTA appears once the (mocked) empty summary resolves.
    expect(await screen.findByTestId('dashboard-projects-cta')).toBeInTheDocument();
    // Project StatCard shows the real 0, never NaN / dash.
    expect(screen.getByTestId('stat-projects')).toHaveTextContent('0');
  });

  it('degrades gracefully to a dash and an error message on a failed summary', async () => {
    signIn('super-admin');
    vi.spyOn(analyticsApi, 'fetchPortfolioSummary').mockRejectedValue(
      new Error('boom'),
    );
    render(renderWithProviders(<DashboardPage />));

    expect(await screen.findByRole('alert')).toBeInTheDocument();
    // No NaN; the neutral dash stands in for the failed value.
    expect(screen.getByTestId('stat-projects')).toHaveTextContent('—');
  });
});
