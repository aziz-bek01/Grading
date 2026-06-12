import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import {
  renderWithProviders,
  signInWithPermissions,
  signOut,
} from '@/test/testUtils';
import { PanelAveragedResult } from './PanelAveragedResult';
import { PERMISSIONS } from '@/shared/types/permissions';
import { createMockAdapter } from '@/shared/api/mocks/handlers';
import { httpClient } from '@/shared/api/httpClient';
import type { AxiosAdapter } from 'axios';

describe('<PanelAveragedResult /> visibility gate (FE-5)', () => {
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

  it('shows the LOCKED placeholder before AVERAGED — never a partial average', () => {
    signInWithPermissions([PERMISSIONS.CAMPAIGN_RESULTS_VIEW]);
    render(
      renderWithProviders(
        <PanelAveragedResult panelId="panel-cto-collecting" status="AWAITING_EVALUATIONS" />,
      ),
    );
    expect(screen.getByTestId('panel-result-locked')).toBeInTheDocument();
    // No averaged table, no total.
    expect(screen.queryByTestId('panel-averaged-result')).not.toBeInTheDocument();
    expect(screen.queryByTestId('panel-result-total')).not.toBeInTheDocument();
  });

  it('denies the result when AVERAGED but the viewer lacks CAMPAIGN_RESULTS_VIEW (AC-6)', () => {
    // Holds EVALUATION_READ but NOT CAMPAIGN_RESULTS_VIEW — the blind does not lift.
    signInWithPermissions([PERMISSIONS.EVALUATION_READ]);
    render(
      renderWithProviders(
        <PanelAveragedResult panelId="panel-cfo-averaged" status="AVERAGED" />,
      ),
    );
    // No averaged table is rendered; a no-access state is shown.
    expect(screen.queryByTestId('panel-averaged-result')).not.toBeInTheDocument();
  });

  it('renders the averaged total + per-factor averages with CAMPAIGN_RESULTS_VIEW', async () => {
    signInWithPermissions([
      PERMISSIONS.EVALUATION_READ,
      PERMISSIONS.CAMPAIGN_RESULTS_VIEW,
    ]);
    render(
      renderWithProviders(
        <PanelAveragedResult panelId="panel-cfo-averaged" status="AVERAGED" />,
      ),
    );
    await waitFor(() =>
      expect(screen.getByTestId('panel-averaged-result')).toBeInTheDocument(),
    );
    // The official averaged total is shown.
    expect(screen.getByTestId('panel-result-total')).toBeInTheDocument();
    // The "official / backend-stored" honesty note is present.
    expect(screen.getByTestId('panel-result-official-note')).toBeInTheDocument();
  });
});
