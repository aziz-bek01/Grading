import type { CurrentUser } from '@/shared/auth/authTypes';
import { PERMISSIONS } from '@/shared/types/permissions';

/**
 * Local-only dev seed user.
 * Used when the backend dev-auth endpoint is not yet wired.
 * Backend will replace this via `POST /dev-auth/login` returning a real Token.
 */
export function buildDevUser(role: 'super-admin' | 'consultant' | 'viewer' = 'super-admin'): CurrentUser {
  const base: CurrentUser = {
    // Real ACME admin user UUID seeded in the backend dev DB. When running
    // against the real backend (VITE_USE_MSW=false) httpClient forwards this
    // as `X-Dev-User` so DevAuthFilter resolves roles/permissions from the DB.
    id: 'aaaa1111-aaaa-1111-aaaa-1111aaaa1111',
    email: 'dev@hrlab.local',
    name: 'Dev User',
    locale: 'ru-RU',
    roles: [],
    permissions: [],
    salary_data_permission: false,
    tenants: [
      {
        id: '11111111-1111-1111-1111-111111111111',
        slug: 'acme',
        brand_name: 'ACME Holdings',
        fingerprint_hue: 215,
      },
      {
        id: '22222222-2222-2222-2222-222222222222',
        slug: 'beta',
        brand_name: 'Beta University',
        fingerprint_hue: 145,
      },
    ],
  };

  switch (role) {
    case 'super-admin':
      return {
        ...base,
        roles: ['HRLAB_SUPER_ADMIN'],
        // Super-admin has every permission except salary visibility (granted
        // explicitly per-membership). USER_INVITE / USER_SALARY_PERMISSION_TOGGLE
        // are part of the full set via Object.values(PERMISSIONS).
        permissions: Object.values(PERMISSIONS).filter(
          (p) => p !== PERMISSIONS.SALARY_VIEW && p !== PERMISSIONS.SALARY_EDIT && p !== PERMISSIONS.SALARY_EXPORT,
        ),
        salary_data_permission: false,
      };
    case 'consultant':
      return {
        ...base,
        roles: ['HRLAB_CONSULTANT'],
        permissions: [
          PERMISSIONS.PROJECT_READ,
          PERMISSIONS.ORG_READ,
          PERMISSIONS.ORG_EDIT,
          PERMISSIONS.POSITION_READ,
          PERMISSIONS.POSITION_CREATE,
          PERMISSIONS.POSITION_EDIT,
          PERMISSIONS.JOB_PROFILE_READ,
          PERMISSIONS.JOB_PROFILE_EDIT,
          PERMISSIONS.JOB_ANALYSIS_READ,
          PERMISSIONS.JOB_ANALYSIS_EDIT,
          PERMISSIONS.METHODOLOGY_READ,
          PERMISSIONS.METHODOLOGY_EDIT,
          PERMISSIONS.EVALUATION_READ,
          PERMISSIONS.EVALUATION_EDIT,
          PERMISSIONS.EVALUATION_APPROVE,
          PERMISSIONS.EVALUATION_LOCK,
          PERMISSIONS.CALIBRATION_EDIT,
          PERMISSIONS.GRADE_READ,
          PERMISSIONS.AUDIT_READ,
          PERMISSIONS.WORKFLOW_READ,
          PERMISSIONS.APPROVAL_REQUEST_CREATE,
          PERMISSIONS.APPROVAL_REQUEST_CANCEL,
          PERMISSIONS.APPROVAL_REQUEST_DECIDE,
          PERMISSIONS.APPROVAL_STEP_APPROVE,
          PERMISSIONS.APPROVAL_STEP_REJECT,
          PERMISSIONS.COMMENT_CREATE,
          PERMISSIONS.COMMENT_EDIT,
          PERMISSIONS.COMMENT_DELETE,
          PERMISSIONS.REPORT_READ,
          PERMISSIONS.REPORT_CREATE,
          PERMISSIONS.REPORT_EXPORT,
        ],
      };
    case 'viewer':
    default:
      return {
        ...base,
        roles: ['VIEWER'],
        permissions: [
          PERMISSIONS.PROJECT_READ,
          PERMISSIONS.ORG_READ,
          PERMISSIONS.POSITION_READ,
          PERMISSIONS.METHODOLOGY_READ,
          PERMISSIONS.EVALUATION_READ,
          PERMISSIONS.GRADE_READ,
          PERMISSIONS.WORKFLOW_READ,
          PERMISSIONS.REPORT_READ,
        ],
      };
  }
}
