/**
 * MSW fixtures for the Users & Access feature.
 *
 * 6 sample users across two tenants (ACME = 11111…, Beta = 22222…) covering
 * ACTIVE / INVITED / REVOKED / SUSPENDED statuses, a mix of role codes, and
 * exactly ONE membership with salary_data_permission=true (Anna at ACME) so
 * the salary-visibility UI is exercised in the dev preview.
 *
 * Tenant ids match `devAuth.ts` tenant catalog so the active-tenant filter
 * works against the same scope the auth store sees.
 */
import type { RoleCode } from '@/shared/auth/authTypes';
import type {
  User,
  UserAuditEvent,
  UserDetails,
  UserMembership,
} from '../types/userTypes';

const TENANT_ACME = '11111111-1111-1111-1111-111111111111';
const TENANT_BETA = '22222222-2222-2222-2222-222222222222';

function membership(
  tenantId: string,
  brand: string,
  status: UserMembership['status'],
  roles: { id: string; role_code: RoleCode }[],
  opts: Partial<UserMembership> = {},
): UserMembership {
  return {
    tenant_id: tenantId,
    tenant_brand_name: brand,
    status,
    salary_data_permission: opts.salary_data_permission ?? false,
    salary_data_permission_granted_at: opts.salary_data_permission_granted_at ?? null,
    salary_data_permission_granted_by: opts.salary_data_permission_granted_by ?? null,
    roles: roles.map((r) => ({
      id: r.id,
      role_code: r.role_code,
      granted_at: opts.joined_at ?? '2026-01-15T08:00:00Z',
      granted_by: 'u-hrlab-001',
    })),
    invited_at: opts.invited_at ?? '2026-01-10T09:00:00Z',
    joined_at: opts.joined_at ?? '2026-01-15T08:00:00Z',
    revoked_at: opts.revoked_at ?? null,
  };
}

const userBase: Omit<UserDetails, 'memberships' | 'role_codes' | 'role_count' | 'tenant_count'>[] = [
  {
    id: 'u-acme-anna',
    email: 'anna.karimova@acme.test',
    full_name: 'Anna Karimova',
    locale: 'ru-RU',
    status: 'ACTIVE',
    last_login_at: '2026-05-23T11:42:00Z',
    created_at: '2025-12-01T10:00:00Z',
    updated_at: '2026-05-24T08:30:00Z',
  },
  {
    id: 'u-acme-bekzod',
    email: 'b.tursunov@acme.test',
    full_name: 'Bekzod Tursunov',
    locale: 'uz-Latn-UZ',
    status: 'ACTIVE',
    last_login_at: '2026-05-20T07:15:00Z',
    created_at: '2025-11-12T10:00:00Z',
    updated_at: '2026-05-20T07:15:00Z',
  },
  {
    id: 'u-acme-dilshod',
    email: 'd.rashidov@acme.test',
    full_name: 'Dilshod Rashidov',
    locale: 'uz-Cyrl-UZ',
    status: 'INVITED',
    last_login_at: null,
    created_at: '2026-05-22T13:20:00Z',
    updated_at: '2026-05-22T13:20:00Z',
  },
  {
    id: 'u-acme-yulia',
    email: 'y.petrova@acme.test',
    full_name: 'Yulia Petrova',
    locale: 'ru-RU',
    status: 'REVOKED',
    last_login_at: '2026-02-14T16:00:00Z',
    created_at: '2025-09-01T10:00:00Z',
    updated_at: '2026-03-10T12:00:00Z',
  },
  {
    id: 'u-beta-john',
    email: 'john.smith@beta-university.test',
    full_name: 'John Smith',
    locale: 'en-US',
    status: 'ACTIVE',
    last_login_at: '2026-05-24T05:55:00Z',
    created_at: '2026-02-01T09:00:00Z',
    updated_at: '2026-05-24T05:55:00Z',
  },
  {
    id: 'u-beta-malika',
    email: 'm.salimova@beta-university.test',
    full_name: 'Malika Salimova',
    locale: 'uz-Latn-UZ',
    status: 'ACTIVE',
    last_login_at: '2026-05-19T14:00:00Z',
    created_at: '2026-02-05T09:00:00Z',
    updated_at: '2026-05-19T14:00:00Z',
  },
];

export const seedUserDetails: UserDetails[] = [
  {
    ...userBase[0],
    memberships: [
      membership(
        TENANT_ACME,
        'ACME Holdings',
        'ACTIVE',
        [
          { id: 'r-001', role_code: 'CLIENT_HR_DIRECTOR' },
          { id: 'r-002', role_code: 'EVALUATION_COMMITTEE_MEMBER' },
        ],
        {
          salary_data_permission: true,
          salary_data_permission_granted_at: '2026-03-01T11:00:00Z',
          salary_data_permission_granted_by: 'u-hrlab-001',
        },
      ),
    ],
    tenant_count: 1,
    role_count: 2,
    role_codes: ['CLIENT_HR_DIRECTOR', 'EVALUATION_COMMITTEE_MEMBER'],
  },
  {
    ...userBase[1],
    memberships: [
      membership(
        TENANT_ACME,
        'ACME Holdings',
        'ACTIVE',
        [{ id: 'r-003', role_code: 'CLIENT_COMPANY_ADMIN' }],
      ),
    ],
    tenant_count: 1,
    role_count: 1,
    role_codes: ['CLIENT_COMPANY_ADMIN'],
  },
  {
    ...userBase[2],
    memberships: [
      membership(
        TENANT_ACME,
        'ACME Holdings',
        'INVITED',
        [{ id: 'r-004', role_code: 'CLIENT_HR_SPECIALIST' }],
        { joined_at: null },
      ),
    ],
    tenant_count: 1,
    role_count: 1,
    role_codes: ['CLIENT_HR_SPECIALIST'],
  },
  {
    ...userBase[3],
    memberships: [
      membership(
        TENANT_ACME,
        'ACME Holdings',
        'REVOKED',
        [{ id: 'r-005', role_code: 'VIEWER' }],
        { revoked_at: '2026-03-10T12:00:00Z' },
      ),
    ],
    tenant_count: 0,
    role_count: 0,
    role_codes: [],
  },
  {
    ...userBase[4],
    memberships: [
      membership(
        TENANT_BETA,
        'Beta University',
        'ACTIVE',
        [
          { id: 'r-006', role_code: 'CLIENT_COMPANY_ADMIN' },
          { id: 'r-007', role_code: 'CLIENT_HR_DIRECTOR' },
        ],
      ),
    ],
    tenant_count: 1,
    role_count: 2,
    role_codes: ['CLIENT_COMPANY_ADMIN', 'CLIENT_HR_DIRECTOR'],
  },
  {
    ...userBase[5],
    memberships: [
      membership(
        TENANT_BETA,
        'Beta University',
        'ACTIVE',
        [{ id: 'r-008', role_code: 'EVALUATION_COMMITTEE_MEMBER' }],
      ),
    ],
    tenant_count: 1,
    role_count: 1,
    role_codes: ['EVALUATION_COMMITTEE_MEMBER'],
  },
];

/** Extracts the lightweight list-row shape (without memberships) from a UserDetails. */
export function toListRow(d: UserDetails): User {
  const { memberships, ...row } = d;
  return row;
}

export const seedUserAudit: Record<string, UserAuditEvent[]> = {
  'u-acme-anna': [
    {
      id: 'a-001',
      action: 'USER_INVITED',
      entity_type: 'USER',
      entity_id: 'u-acme-anna',
      actor_id: 'u-hrlab-001',
      actor_name: 'HRLab Admin',
      tenant_id: TENANT_ACME,
      occurred_at: '2025-12-01T10:00:00Z',
    },
    {
      id: 'a-002',
      action: 'USER_LOGGED_IN',
      entity_type: 'USER',
      entity_id: 'u-acme-anna',
      actor_id: 'u-acme-anna',
      actor_name: 'Anna Karimova',
      tenant_id: TENANT_ACME,
      occurred_at: '2025-12-02T08:01:00Z',
    },
    {
      id: 'a-003',
      action: 'USER_SALARY_PERMISSION_GRANTED',
      entity_type: 'USER_MEMBERSHIP',
      entity_id: 'u-acme-anna',
      actor_id: 'u-hrlab-001',
      actor_name: 'HRLab Admin',
      tenant_id: TENANT_ACME,
      occurred_at: '2026-03-01T11:00:00Z',
      metadata: {
        reason: 'HR Director needs full compensation visibility for grading project',
      },
    },
  ],
  'u-acme-bekzod': [
    {
      id: 'a-010',
      action: 'USER_INVITED',
      entity_type: 'USER',
      entity_id: 'u-acme-bekzod',
      actor_id: 'u-hrlab-001',
      actor_name: 'HRLab Admin',
      tenant_id: TENANT_ACME,
      occurred_at: '2025-11-12T10:00:00Z',
    },
  ],
};

/** Mutable in-memory database mirrored from seeds. */
export const userDb = {
  users: seedUserDetails.map((u) => ({ ...u, memberships: u.memberships.map((m) => ({ ...m, roles: [...m.roles] })) })),
  audit: { ...seedUserAudit },
};
