/**
 * Integration test for the Access-scope section (slice E4-S1).
 *
 * Drives the section against the in-process mock adapter:
 *   - loads Anna's current ACME scope (seeded: 2 departments + 1 project),
 *   - toggles one extra department + one extra project,
 *   - saves each, and asserts the REPLACE-set payloads sent on the wire are
 *     snake_case ({ tenant_id, department_ids } / { tenant_id, project_ids }).
 *
 * Plus the no-access state: when the caller lacks the membership-management
 * authority the whole section is not rendered on the membership card.
 */
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { AxiosAdapter, AxiosRequestConfig } from 'axios';
import { renderWithProviders, signIn, signOut } from '@/test/testUtils';
import { httpClient } from '@/shared/api/httpClient';
import { createMockAdapter } from '@/shared/api/mocks/handlers';
import { useAuthStore } from '@/features/auth/authStore';
import { PERMISSIONS, type PermissionCode } from '@/shared/types/permissions';
import { MembershipCard } from './MembershipCard';
import type { UserMembership } from '../types/userTypes';
import { AccessScopeSection } from './AccessScopeSection';

const ORIGINAL_ADAPTER = httpClient.defaults.adapter as AxiosAdapter | undefined;
const ACME = '11111111-1111-1111-1111-111111111111';

/** Records the body of every PUT so we can assert the wire payloads. */
const sentPuts: { url: string; data: unknown }[] = [];
let interceptorId: number;

function parseData(data: unknown): unknown {
  if (typeof data === 'string') {
    try {
      return JSON.parse(data);
    } catch {
      return data;
    }
  }
  return data;
}

beforeEach(() => {
  httpClient.defaults.adapter = createMockAdapter(ORIGINAL_ADAPTER);
  sentPuts.length = 0;
  interceptorId = httpClient.interceptors.request.use((config: AxiosRequestConfig) => {
    if ((config.method ?? '').toLowerCase() === 'put') {
      sentPuts.push({ url: config.url ?? '', data: parseData(config.data) });
    }
    return config as never;
  });
});

afterEach(() => {
  httpClient.defaults.adapter = ORIGINAL_ADAPTER;
  httpClient.interceptors.request.eject(interceptorId);
  signOut();
});

function dropPermissions(...codes: PermissionCode[]) {
  const state = useAuthStore.getState();
  useAuthStore.setState({
    ...state,
    user: {
      ...state.user!,
      // Also strip the HRLAB_SUPER_ADMIN role: it short-circuits every
      // permission gate by design (defense-in-depth), so a super admin can
      // never have these actions hidden. To assert permission-LEVEL gating we
      // must simulate a regular (non-super-admin) user.
      roles: state.user!.roles.filter((r) => r !== 'HRLAB_SUPER_ADMIN'),
      permissions: state.user!.permissions.filter((p) => !codes.includes(p)),
    },
  });
}

describe('<AccessScopeSection /> (E4-S1)', () => {
  it('prefills from current scopes and saves REPLACE-set snake_case payloads', async () => {
    signIn('super-admin');
    const user = userEvent.setup();
    render(renderWithProviders(<AccessScopeSection userId="u-acme-anna" tenantId={ACME} />));

    // Seeded department scope (dep-acme-fin + dep-acme-fin-treasury) prefills.
    const finCheckbox = await screen.findByTestId('scope-dept-checkbox-FIN');
    await waitFor(() => expect(finCheckbox).toBeChecked());

    // Seeded project scope (proj-acme-2026) prefills.
    const proj2026 = await screen.findByTestId('scope-project-checkbox-ACME-2026');
    expect(proj2026).toBeChecked();

    // Toggle ON an additional, previously-unselected department (IT) and project.
    const itCheckbox = screen.getByTestId('scope-dept-checkbox-IT');
    expect(itCheckbox).not.toBeChecked();
    await user.click(itCheckbox);

    const projPilot = screen.getByTestId('scope-project-checkbox-ACME-PILOT');
    expect(projPilot).not.toBeChecked();
    await user.click(projPilot);

    // Save departments → asserts snake_case replace-set body with the full set.
    await user.click(screen.getByTestId('save-department-scopes'));
    await waitFor(() => expect(screen.getByTestId('dept-scope-banner')).toBeInTheDocument());

    const deptPut = sentPuts.find((p) => p.url.includes('/department-scopes'));
    expect(deptPut).toBeTruthy();
    const deptBody = deptPut!.data as { tenant_id: string; department_ids: string[] };
    expect(deptBody.tenant_id).toBe(ACME);
    // Replace-set carries the FULL desired set: the 2 seeded + IT (+ its subtree).
    expect(deptBody.department_ids).toEqual(
      expect.arrayContaining(['dep-acme-fin', 'dep-acme-fin-treasury', 'dep-acme-it']),
    );

    // Save projects → snake_case replace-set body.
    await user.click(screen.getByTestId('save-project-assignments'));
    await waitFor(() => expect(screen.getByTestId('project-scope-banner')).toBeInTheDocument());

    const projPut = sentPuts.find((p) => p.url.includes('/project-assignments'));
    expect(projPut).toBeTruthy();
    const projBody = projPut!.data as { tenant_id: string; project_ids: string[] };
    expect(projBody.tenant_id).toBe(ACME);
    expect(projBody.project_ids).toEqual(
      expect.arrayContaining(['proj-acme-2026', 'proj-acme-pilot']),
    );

    // No `tenantId`/camelCase leaked into either body.
    expect(deptBody).not.toHaveProperty('tenantId');
    expect(projBody).not.toHaveProperty('projectIds');
  });
});

describe('<MembershipCard /> access-scope gating (E4-S1)', () => {
  const membership: UserMembership = {
    tenant_id: ACME,
    tenant_brand_name: 'ACME Holdings',
    status: 'ACTIVE',
    salary_data_permission: false,
    salary_data_permission_granted_at: null,
    salary_data_permission_granted_by: null,
    roles: [{ id: 'r-001', role_code: 'CLIENT_HR_DIRECTOR', granted_at: '', granted_by: null }],
    invited_at: '2026-01-10T09:00:00Z',
    joined_at: '2026-01-15T08:00:00Z',
    revoked_at: null,
  };

  it('renders the access-scope toggle for a membership manager', () => {
    signIn('super-admin'); // has USER_ACCESS_MANAGE / USER_MEMBERSHIP_MANAGE
    render(
      renderWithProviders(
        <MembershipCard userId="u-acme-anna" userName="Anna" membership={membership} />,
      ),
    );
    expect(screen.getByTestId(`toggle-scope-${ACME}`)).toBeInTheDocument();
  });

  it('hides the access-scope section without USER_MEMBERSHIP_MANAGE / USER_ACCESS_MANAGE', () => {
    signIn('super-admin');
    dropPermissions(PERMISSIONS.USER_MEMBERSHIP_MANAGE, PERMISSIONS.USER_ACCESS_MANAGE);
    render(
      renderWithProviders(
        <MembershipCard userId="u-acme-anna" userName="Anna" membership={membership} />,
      ),
    );
    expect(screen.queryByTestId(`toggle-scope-${ACME}`)).not.toBeInTheDocument();
  });
});
