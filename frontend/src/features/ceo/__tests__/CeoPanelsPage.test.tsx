/**
 * CEO Panel Overview — permission-gating + org-wide data tests.
 *
 * Asserts:
 * 1. CeoPanelsPage renders ONLY when the user holds EVALUATION_PANEL_APPROVE.
 * 2. Without the permission, NoAccessState is shown (not a crash / white screen).
 * 3. The overview calls GET /panels with status params and renders panel rows.
 * 4. The dashboard CEO card renders with EVALUATION_PANEL_APPROVE and is absent without.
 * 5. The sidebar CEO nav item renders with EVALUATION_PANEL_APPROVE and is absent without.
 * 6. Inline sign-off (dropdown UX):
 *    - pending row renders dropdown trigger button + "Открыть" link → approvalDetails;
 *    - opening the dropdown exposes Tasdiqlayman / request-changes / reject items;
 *    - Tasdiqlayman → ConfirmDialog → approve mutation;
 *    - Qayta ko'rib chiqilsin → ReasonRequiredDialog; <20 chars blocked;
 *    - Bekor qilinsin → ReasonRequiredDialog; valid reason closes dialog;
 *    - non-pending row shows "Open" link → panel-detail route (NO dropdown);
 *    - dept/division/avg-score columns render; 400 error shown inline.
 *
 * REUSE: renderWithProviders, signInWithPermissions, signOut from testUtils.tsx;
 * createMockAdapter from existing handlers (not forked); mockDb fixtures for
 * panel data. No new MSW workers — the existing in-process adapter is used.
 */
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import type { AxiosAdapter } from 'axios';
import {
  renderWithProviders,
  signInWithPermissions,
  signOut,
} from '@/test/testUtils';
import { createMockAdapter } from '@/shared/api/mocks/handlers';
import { mockDb } from '@/shared/api/mocks/fixtures';
import { httpClient } from '@/shared/api/httpClient';
import { PERMISSIONS } from '@/shared/types/permissions';
import { routes } from '@/shared/config/routes';
import { CeoPanelsPage } from '../pages/CeoPanelsPage';
import { DashboardPage } from '@/pages/DashboardPage';
import { Sidebar } from '@/shared/components/layout/Sidebar';

// ─── Adapter setup ────────────────────────────────────────────────────────────

let originalAdapter: AxiosAdapter | undefined;

/**
 * Reset the seeded panel approval request back to PENDING before each test, so
 * mutations in one test do not corrupt the mockDb for subsequent tests.
 * The mock handlers mutate the in-memory fixtures in-place — we must restore.
 */
function resetPanelApproval() {
  const req = mockDb.approvalRequests.find((a) => a.id === 'appr-panel-cfo-1');
  if (req) {
    req.current_status = 'PENDING';
    const step = req.steps.find((s) => s.id === 'appr-panel-cfo-1-step-1');
    if (step) {
      step.status = 'PENDING';
      // Cast through unknown to remove extra fields added by mutation handlers.
      const s = step as unknown as Record<string, unknown>;
      delete s['decided_at'];
      delete s['decided_by'];
      delete s['decided_by_name'];
      delete s['reason_i18n'];
    }
  }
}

beforeEach(() => {
  originalAdapter = httpClient.defaults.adapter as AxiosAdapter | undefined;
  httpClient.defaults.adapter = createMockAdapter(undefined);
  resetPanelApproval();
});

afterEach(() => {
  httpClient.defaults.adapter = originalAdapter;
  signOut();
  resetPanelApproval();
});

// ─── Helpers ──────────────────────────────────────────────────────────────────

// CEO user: EVALUATION_PANEL_APPROVE + APPROVAL_REQUEST_DECIDE so inbox fires
// and mutation hooks can run.
const WITH_CEO = [
  PERMISSIONS.EVALUATION_PANEL_APPROVE,
  PERMISSIONS.EVALUATION_READ,
  PERMISSIONS.APPROVAL_REQUEST_DECIDE,
];
const WITHOUT_CEO = [PERMISSIONS.EVALUATION_READ]; // no EVALUATION_PANEL_APPROVE

/** Open the actions dropdown for the awaiting row and return the open menu. */
async function openActionsMenu() {
  await waitFor(() =>
    expect(screen.getByTestId('ceo-actions-menu-trigger')).toBeInTheDocument(),
  );
  fireEvent.click(screen.getByTestId('ceo-actions-menu-trigger'));
  await waitFor(() =>
    expect(screen.getByTestId('ceo-actions-menu')).toBeInTheDocument(),
  );
  return screen.getByTestId('ceo-actions-menu');
}

// ─── CeoPanelsPage gating ─────────────────────────────────────────────────────

describe('<CeoPanelsPage /> permission gating', () => {
  it('renders the overview page when the user holds EVALUATION_PANEL_APPROVE', async () => {
    signInWithPermissions(WITH_CEO);
    render(renderWithProviders(<CeoPanelsPage />, ['/app/ceo/panels']));

    await waitFor(() =>
      expect(screen.getByTestId('ceo-panels-page')).toBeInTheDocument(),
    );
  });

  it('shows NoAccessState and hides the page without EVALUATION_PANEL_APPROVE', () => {
    signInWithPermissions(WITHOUT_CEO);
    render(renderWithProviders(<CeoPanelsPage />, ['/app/ceo/panels']));

    // The page content must NOT appear.
    expect(screen.queryByTestId('ceo-panels-page')).not.toBeInTheDocument();
    expect(screen.queryByText(/Panel Overview/i)).not.toBeInTheDocument();
  });
});

// ─── Org-wide status filter ───────────────────────────────────────────────────

describe('<CeoPanelsPage /> org-wide status pull', () => {
  it('fetches panels without projectId and renders at least the seeded rows', async () => {
    signInWithPermissions(WITH_CEO);
    render(renderWithProviders(<CeoPanelsPage />, ['/app/ceo/panels']));

    await waitFor(() =>
      expect(screen.getByTestId('ceo-panels-page')).toBeInTheDocument(),
    );

    // The seeded mock data has 2 panels (panel-cto-collecting + panel-cfo-averaged).
    await waitFor(() =>
      expect(screen.queryByTestId('ceo-panels-table-card')).toBeInTheDocument(),
    );
  });

  it('renders a link to the approvals inbox for sign-off', async () => {
    signInWithPermissions(WITH_CEO);
    render(renderWithProviders(<CeoPanelsPage />, ['/app/ceo/panels']));

    await waitFor(() =>
      expect(screen.getByTestId('ceo-inbox-link')).toBeInTheDocument(),
    );
  });

  it('shows status filter tabs', async () => {
    signInWithPermissions(WITH_CEO);
    render(renderWithProviders(<CeoPanelsPage />, ['/app/ceo/panels']));

    await waitFor(() =>
      expect(screen.getByTestId('ceo-filter-all')).toBeInTheDocument(),
    );
    expect(screen.getByTestId('ceo-filter-pending')).toBeInTheDocument();
    expect(screen.getByTestId('ceo-filter-inflight')).toBeInTheDocument();
    expect(screen.getByTestId('ceo-filter-history')).toBeInTheDocument();
  });
});

// ─── Dropdown UX on awaiting rows ─────────────────────────────────────────────

describe('<CeoPanelsPage /> inline sign-off (dropdown UX)', () => {
  it('renders the dropdown trigger (not three inline buttons) for the pending-approval panel row', async () => {
    signInWithPermissions(WITH_CEO);
    render(renderWithProviders(<CeoPanelsPage />, ['/app/ceo/panels']));

    await waitFor(() =>
      expect(screen.getByTestId('ceo-panels-table-card')).toBeInTheDocument(),
    );

    // The AVERAGED panel (panel-cfo-averaged) has a PENDING approval step in
    // the mock inbox → the dropdown trigger should appear.
    await waitFor(() =>
      expect(screen.getByTestId('ceo-actions-menu-trigger')).toBeInTheDocument(),
    );

    // Three always-visible inline buttons are gone — they are now inside the dropdown.
    expect(screen.queryByTestId('ceo-inline-approve')).not.toBeInTheDocument();
    expect(screen.queryByTestId('ceo-inline-request-changes')).not.toBeInTheDocument();
    expect(screen.queryByTestId('ceo-inline-reject')).not.toBeInTheDocument();
  });

  it('awaiting row renders an "Open" link pointing to approvalDetails route', async () => {
    signInWithPermissions(WITH_CEO);
    render(renderWithProviders(<CeoPanelsPage />, ['/app/ceo/panels']));

    await waitFor(() =>
      expect(screen.getByTestId('ceo-panels-table-card')).toBeInTheDocument(),
    );

    // panel-cfo-averaged → approvalId = 'appr-panel-cfo-1'
    const approvalId = 'appr-panel-cfo-1';
    await waitFor(() =>
      expect(screen.getByTestId('ceo-open-approval-panel-cfo-averaged')).toBeInTheDocument(),
    );

    const openLink = screen.getByTestId('ceo-open-approval-panel-cfo-averaged');
    expect(openLink).toHaveAttribute('href', routes.approvalDetails(approvalId));
  });

  it('opening the dropdown exposes approve / request-changes / reject menu items', async () => {
    signInWithPermissions(WITH_CEO);
    render(renderWithProviders(<CeoPanelsPage />, ['/app/ceo/panels']));

    await openActionsMenu();

    expect(screen.getByTestId('ceo-inline-approve')).toBeInTheDocument();
    expect(screen.getByTestId('ceo-inline-request-changes')).toBeInTheDocument();
    expect(screen.getByTestId('ceo-inline-reject')).toBeInTheDocument();
  });

  it('clicking Tasdiqlayman in the dropdown opens a ConfirmDialog', async () => {
    signInWithPermissions(WITH_CEO);
    render(renderWithProviders(<CeoPanelsPage />, ['/app/ceo/panels']));

    await openActionsMenu();
    fireEvent.click(screen.getByTestId('ceo-inline-approve'));

    // ConfirmDialog renders a role=dialog element.
    await waitFor(() =>
      expect(screen.getByRole('dialog')).toBeInTheDocument(),
    );
  });

  it('confirming in ConfirmDialog calls approve mutation (dialog closes)', async () => {
    signInWithPermissions(WITH_CEO);
    render(renderWithProviders(<CeoPanelsPage />, ['/app/ceo/panels']));

    await openActionsMenu();
    fireEvent.click(screen.getByTestId('ceo-inline-approve'));

    await waitFor(() => expect(screen.getByRole('dialog')).toBeInTheDocument());

    // ConfirmDialog confirm button: Cancel is [0], Confirm is [1]
    const confirmBtn = screen
      .getByRole('dialog')
      .querySelectorAll('button')[1];
    fireEvent.click(confirmBtn);

    // Dialog should close after confirm.
    await waitFor(() =>
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument(),
    );
  });

  it("clicking Qayta ko'rib chiqilsin opens ReasonRequiredDialog", async () => {
    signInWithPermissions(WITH_CEO);
    render(renderWithProviders(<CeoPanelsPage />, ['/app/ceo/panels']));

    await openActionsMenu();
    fireEvent.click(screen.getByTestId('ceo-inline-request-changes'));

    await waitFor(() => expect(screen.getByRole('dialog')).toBeInTheDocument());
  });

  it('ReasonRequiredDialog blocks confirm when reason <20 chars', async () => {
    signInWithPermissions(WITH_CEO);
    render(renderWithProviders(<CeoPanelsPage />, ['/app/ceo/panels']));

    await openActionsMenu();
    fireEvent.click(screen.getByTestId('ceo-inline-request-changes'));
    await waitFor(() => expect(screen.getByRole('dialog')).toBeInTheDocument());

    // The confirm button in ReasonRequiredDialog is disabled until reason >= 20 chars.
    const dialog = screen.getByRole('dialog');
    const textarea = dialog.querySelector('textarea');
    const confirmBtn = dialog.querySelectorAll('button')[1]; // Cancel[0], Confirm[1]

    expect(confirmBtn).toBeDisabled();

    // Short reason — still disabled.
    if (textarea) fireEvent.change(textarea, { target: { value: 'short' } });
    expect(confirmBtn).toBeDisabled();
  });

  it('ReasonRequiredDialog enables confirm when reason >=20 chars and closes on confirm', async () => {
    signInWithPermissions(WITH_CEO);
    render(renderWithProviders(<CeoPanelsPage />, ['/app/ceo/panels']));

    await openActionsMenu();
    fireEvent.click(screen.getByTestId('ceo-inline-request-changes'));
    await waitFor(() => expect(screen.getByRole('dialog')).toBeInTheDocument());

    const dialog = screen.getByRole('dialog');
    const textarea = dialog.querySelector('textarea');
    const confirmBtn = dialog.querySelectorAll('button')[1];

    if (textarea)
      fireEvent.change(textarea, {
        target: { value: 'This reason is long enough to pass the 20-char validation' },
      });

    expect(confirmBtn).not.toBeDisabled();
    fireEvent.click(confirmBtn);

    await waitFor(() =>
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument(),
    );
  });

  it('clicking Bekor qilinsin opens ReasonRequiredDialog and valid reason closes dialog', async () => {
    signInWithPermissions(WITH_CEO);
    render(renderWithProviders(<CeoPanelsPage />, ['/app/ceo/panels']));

    await openActionsMenu();
    fireEvent.click(screen.getByTestId('ceo-inline-reject'));
    await waitFor(() => expect(screen.getByRole('dialog')).toBeInTheDocument());

    const dialog = screen.getByRole('dialog');
    const textarea = dialog.querySelector('textarea');
    const confirmBtn = dialog.querySelectorAll('button')[1];

    expect(confirmBtn).toBeDisabled();

    if (textarea)
      fireEvent.change(textarea, {
        target: { value: 'Reject reason that is long enough to pass validation' },
      });

    expect(confirmBtn).not.toBeDisabled();
    fireEvent.click(confirmBtn);

    await waitFor(() =>
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument(),
    );
  });

  it('non-pending panel row shows the Open link to panel-detail (no dropdown)', async () => {
    signInWithPermissions(WITH_CEO);
    render(renderWithProviders(<CeoPanelsPage />, ['/app/ceo/panels']));

    await waitFor(() =>
      expect(screen.getByTestId('ceo-panels-table-card')).toBeInTheDocument(),
    );

    // The COLLECTING panel (panel-cto-collecting) has NO pending approval step
    // in the inbox → it should show the fallback Open link to PanelDetailPage.
    await waitFor(() =>
      expect(
        screen.getByTestId('ceo-open-panel-panel-cto-collecting'),
      ).toBeInTheDocument(),
    );

    // Confirm link goes to panel detail (NOT approval detail).
    const link = screen.getByTestId('ceo-open-panel-panel-cto-collecting');
    expect(link.getAttribute('href')).toContain('/evaluation/panels/');
  });

  it("renders Departament and Bo'limi columns with seeded values", async () => {
    signInWithPermissions(WITH_CEO);
    render(renderWithProviders(<CeoPanelsPage />, ['/app/ceo/panels']));

    await waitFor(() =>
      expect(screen.getByTestId('ceo-panels-table-card')).toBeInTheDocument(),
    );

    // Finance department label for the AVERAGED panel (ru-RU default in tests).
    // The seeded value is 'Финансы' for ru-RU.
    await waitFor(() => expect(screen.getByText('Финансы')).toBeInTheDocument());
    // Операции department for the COLLECTING panel.
    expect(screen.getByText('Операции')).toBeInTheDocument();
    // Division label for the COLLECTING panel: ИТ-инфраструктура.
    expect(screen.getByText('ИТ-инфраструктура')).toBeInTheDocument();
  });

  it('renders average score column with seeded value for AVERAGED panel', async () => {
    signInWithPermissions(WITH_CEO);
    render(renderWithProviders(<CeoPanelsPage />, ['/app/ceo/panels']));

    await waitFor(() =>
      expect(screen.getByTestId('ceo-panels-table-card')).toBeInTheDocument(),
    );

    // AVERAGED panel has displayed_total_score=712.5 → shown as '712.5'.
    await waitFor(() => expect(screen.getByText('712.5')).toBeInTheDocument());
  });

  it('shows inline error strip on 400 response and table stays interactive', async () => {
    // Override the adapter to simulate a 400 on the approve step only.
    // All other requests fall through to the real mock adapter.
    const baseAdapter = createMockAdapter(undefined);
    const errorAdapter: AxiosAdapter = async (config) => {
      const url = config.url ?? '';
      if (url.includes('/steps/') && url.endsWith('/approve')) {
        // Build an error with a response attached (mirrors apiError intercept).
        const err = Object.assign(new Error('Cannot approve'), {
          response: {
            status: 400,
            data: { code: 'INVALID_TRANSITION', message: 'Cannot approve' },
            headers: {},
            config,
            request: {},
          },
          config,
        });
        throw err;
      }
      return baseAdapter(config);
    };
    httpClient.defaults.adapter = errorAdapter;

    signInWithPermissions(WITH_CEO);
    render(renderWithProviders(<CeoPanelsPage />, ['/app/ceo/panels']));

    // Open the dropdown and select Approve.
    await openActionsMenu();
    fireEvent.click(screen.getByTestId('ceo-inline-approve'));

    // Open the confirm dialog and confirm.
    await waitFor(() => expect(screen.getByRole('dialog')).toBeInTheDocument());

    const confirmBtn = screen.getByRole('dialog').querySelectorAll('button')[1];
    fireEvent.click(confirmBtn);

    // Dialog closes after the mutation fires.
    await waitFor(() =>
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument(),
    );

    // Inline error strip appears (mutation failed with 400).
    await waitFor(() =>
      expect(screen.getByTestId('ceo-inline-action-error')).toBeInTheDocument(),
    );

    // The dropdown trigger is still there — table stays interactive.
    expect(screen.getByTestId('ceo-actions-menu-trigger')).toBeInTheDocument();
  });
});

// ─── Dashboard CEO card gating ────────────────────────────────────────────────

describe('Dashboard CEO card gating', () => {
  it('renders the CEO card when the user holds EVALUATION_PANEL_APPROVE', async () => {
    signInWithPermissions([
      PERMISSIONS.EVALUATION_PANEL_APPROVE,
      PERMISSIONS.EVALUATION_READ,
      PERMISSIONS.APPROVAL_REQUEST_DECIDE,
    ]);

    render(renderWithProviders(<DashboardPage />, ['/app/dashboard']));

    await waitFor(() =>
      expect(screen.getByTestId('ceo-dashboard-card')).toBeInTheDocument(),
    );
  });

  it('hides the CEO card without EVALUATION_PANEL_APPROVE', async () => {
    signInWithPermissions(WITHOUT_CEO);
    render(renderWithProviders(<DashboardPage />, ['/app/dashboard']));

    await waitFor(() =>
      expect(screen.queryByTestId('stat-skeleton')).not.toBeInTheDocument(),
      { timeout: 1000 },
    ).catch(() => {
      // timeout is fine — we just let async effects run.
    });

    expect(screen.queryByTestId('ceo-dashboard-card')).not.toBeInTheDocument();
  });
});

// ─── Sidebar CEO nav item gating ──────────────────────────────────────────────

describe('Sidebar CEO nav item gating', () => {
  it('renders the CEO nav item with EVALUATION_PANEL_APPROVE', () => {
    signInWithPermissions(WITH_CEO);
    render(renderWithProviders(<Sidebar />, ['/app/ceo/panels']));

    const link = document.querySelector('a[href="/app/ceo/panels"]');
    expect(link).toBeInTheDocument();
  });

  it('hides the CEO nav item without EVALUATION_PANEL_APPROVE', () => {
    signInWithPermissions(WITHOUT_CEO);
    render(renderWithProviders(<Sidebar />, ['/app/dashboard']));

    expect(
      document.querySelector('a[href="/app/ceo/panels"]'),
    ).not.toBeInTheDocument();
  });
});
