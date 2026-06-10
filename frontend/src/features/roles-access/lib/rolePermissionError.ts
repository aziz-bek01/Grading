/**
 * Maps a failed roles-admin request to a localized i18n key under `roles.error.*`.
 * Keeps the mapping in ONE place so every surface (the E2 permission matrix and
 * the E3 create/edit/delete flows) renders identical messages.
 *
 * Contract errors handled:
 *   E2 (permission matrix — PUT /roles/{code}/permissions):
 *   - 422 PERMISSION_RESTRICTED         → cannot grant a locked permission
 *   - 422 PERMISSION_NOT_HELD_BY_CALLER → cannot grant a permission you lack
 *   - 403 (system role)                 → only super admin may edit system roles
 *   E3 (custom-role lifecycle — POST /roles, PUT/DELETE /roles/{id}):
 *   - 409 ROLE_CODE_RESERVED            → code collides with a system role
 *   - 409 ROLE_CODE_EXISTS              → code already used in this tenant
 *   - 409 ROLE_IN_USE                   → role assigned to users (revoke first)
 * Everything else falls back to a generic save-failed message.
 */
import { ApiError } from '@/shared/api/apiError';

export interface MappedRoleError {
  /** i18n key under `roles.error.*`. */
  key: string;
  /** Correlation id for support, when the backend supplied one. */
  correlationId?: string;
}

export function mapRolePermissionError(error: unknown): MappedRoleError {
  if (error instanceof ApiError) {
    const c = error.correlationId;
    switch (error.code) {
      case 'PERMISSION_RESTRICTED':
        return { key: 'roles.error.permissionRestricted', correlationId: c };
      case 'PERMISSION_NOT_HELD_BY_CALLER':
        return { key: 'roles.error.permissionNotHeld', correlationId: c };
      case 'ROLE_CODE_RESERVED':
        return { key: 'roles.error.codeReserved', correlationId: c };
      case 'ROLE_CODE_EXISTS':
        return { key: 'roles.error.codeExists', correlationId: c };
      case 'ROLE_IN_USE':
        return { key: 'roles.error.roleInUse', correlationId: c };
      default:
        if (error.isForbidden()) {
          return { key: 'roles.error.systemRoleForbidden', correlationId: c };
        }
        return { key: 'roles.error.saveFailed', correlationId: c };
    }
  }
  return { key: 'roles.error.saveFailed' };
}
