import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import {
  renderWithProviders,
  signIn,
  signInWithPermissions,
  signOut,
} from '@/test/testUtils';
import { PanelProgressView } from './PanelProgressView';
import { createMockAdapter } from '@/shared/api/mocks/handlers';
import { httpClient } from '@/shared/api/httpClient';
import type { AxiosAdapter } from 'axios';

describe('<PanelProgressView /> (FE-4)', () => {
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

  it('renders the blind-safe roster with N/M progress and STATUS only (no scores)', async () => {
    signIn('super-admin');
    render(
      renderWithProviders(<PanelProgressView panelId="panel-cto-collecting" />),
    );

    await waitFor(() =>
      expect(screen.getByTestId('panel-progress-view')).toBeInTheDocument(),
    );

    // One roster row per active evaluator (3 assigned).
    expect(screen.getByTestId('panel-roster-row-user-hr-dir')).toBeInTheDocument();
    expect(screen.getByTestId('panel-roster-row-user-dept-dir')).toBeInTheDocument();
    expect(screen.getByTestId('panel-roster-row-user-ext-exp')).toBeInTheDocument();

    // Progress chip shows 1/3 completed.
    expect(screen.getByTestId('progress-chip')).toHaveTextContent('1/3');

    // Blocked line names the responsible role (the first non-completed seat).
    expect(screen.getByTestId('panel-progress-blocked')).toBeInTheDocument();

    // STATUS only — no raw scores ever rendered in the progress view.
    expect(screen.queryByText(/raw_factor_score/i)).not.toBeInTheDocument();
  });

  it('hides the view (no-access) without EVALUATION_READ', async () => {
    // A user with no permissions at all.
    signInWithPermissions([]);
    render(
      renderWithProviders(<PanelProgressView panelId="panel-cto-collecting" />),
    );
    // The progress card never renders; a no-access state is shown instead.
    expect(screen.queryByTestId('panel-progress-view')).not.toBeInTheDocument();
  });
});
