import type { CurrentUser } from '@/shared/auth/authTypes';
import { PERMISSIONS } from '@/shared/types/permissions';

/**
 * Local-only dev seed user.
 * Used when the backend dev-auth endpoint is not yet wired.
 * Backend will replace this via `POST /dev-auth/login` returning a real Token.
 */
export function buildDevUser(role: 'super-admin' | 'consultant' | 'viewer' = 'super-admin'): CurrentUser {
  const base: CurrentUser = {
    id: '00000000-0000-0000-0000-000000000001',
    email: 'dev@hrlab.local',
    name: 'Dev User',
    locale: 'ru-RU',
    roles: [],
    permissions: [],
    salary_data_permission: false,
    tenants: [
      {
        id: 'tenant-acme',
        slug: 'acme',
        brand_name: 'ACME Holdings',
        fingerprint_hue: 215,
      },
      {
        id: 'tenant-beta',
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
          PERMISSIONS.GRADE_READ,
          PERMISSIONS.AUDIT_READ,
        ],
      };
    case 'viewer':
    default:
      return {
        ...base,
        roles: ['CLIENT_VIEWER'],
        permissions: [
          PERMISSIONS.PROJECT_READ,
          PERMISSIONS.ORG_READ,
          PERMISSIONS.POSITION_READ,
          PERMISSIONS.METHODOLOGY_READ,
          PERMISSIONS.EVALUATION_READ,
          PERMISSIONS.GRADE_READ,
        ],
      };
  }
}
