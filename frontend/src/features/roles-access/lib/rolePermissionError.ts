/**
 * Maps a failed `PUT /roles/{code}/permissions` (or matrix load) error to a
 * localized i18n key under `roles.error.*`. Keeps the mapping in one place so
 * the page and any future surface render identical messages.
 *
 * Contract errors handled:
 *   - 422 PERMISSION_RESTRICTED        → cannot grant a locked permission
 *   - 422 PERMISSION_NOT_HELD_BY_CALLER → cannot grant a permission you lack
 *   - 403 (system role)                 → only super admin may edit system roles
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
    if (error.code === 'PERMISSION_RESTRICTED') {
      return { key: 'roles.error.permissionRestricted', correlationId: error.correlationId };
    }
    if (error.code === 'PERMISSION_NOT_HELD_BY_CALLER') {
      return { key: 'roles.error.permissionNotHeld', correlationId: error.correlationId };
    }
    if (error.isForbidden()) {
      return { key: 'roles.error.systemRoleForbidden', correlationId: error.correlationId };
    }
    return { key: 'roles.error.saveFailed', correlationId: error.correlationId };
  }
  return { key: 'roles.error.saveFailed' };
}
