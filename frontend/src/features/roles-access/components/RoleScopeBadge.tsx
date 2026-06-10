/**
 * Small pills describing a role row in the Roles admin list:
 *   - scope (PLATFORM / TENANT)
 *   - kind (system / custom)
 *
 * Color is never the only signal — each pill carries a text label (a11y).
 */
import { useTranslation } from 'react-i18next';
import { cn } from '@/shared/lib/cn';
import type { RoleScope } from '@/features/users-access/api/rolesApi';

export function RoleScopeBadge({ scope }: { scope: RoleScope }) {
  const { t } = useTranslation();
  const isPlatform = scope === 'PLATFORM';
  return (
    <span
      className={cn(
        'inline-flex items-center text-xs font-medium px-2 py-0.5 rounded-full border',
        isPlatform
          ? 'bg-primary-50 text-primary-700 border-primary-500/30'
          : 'bg-info-50 text-info-600 border-info-500/30',
      )}
      data-testid={`role-scope-${scope}`}
    >
      {isPlatform ? t('roles.scope.platform') : t('roles.scope.tenant')}
    </span>
  );
}

export function RoleKindBadge({ isSystem }: { isSystem: boolean }) {
  const { t } = useTranslation();
  return (
    <span
      className={cn(
        'inline-flex items-center text-xs font-medium px-2 py-0.5 rounded-full border',
        isSystem
          ? 'bg-locked-bg text-locked border-locked/40'
          : 'bg-success-50 text-success-700 border-success-500/30',
      )}
      data-testid={isSystem ? 'role-kind-system' : 'role-kind-custom'}
    >
      {isSystem ? t('roles.kind.system') : t('roles.kind.custom')}
    </span>
  );
}
