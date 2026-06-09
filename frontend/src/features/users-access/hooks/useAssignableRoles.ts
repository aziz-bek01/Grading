import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { fetchAssignableRoles, roleKeys } from '../api/rolesApi';

/**
 * Loads the DATA-DRIVEN role catalog for the assignment pickers
 * (`GET /roles?assignableOnly=true`). Replaces the hardcoded
 * CLIENT_GRANTABLE_ROLES / SUPER_ADMIN_GRANTABLE_ROLES arrays so every
 * assignable role — including future custom roles — appears automatically.
 *
 * The active i18n locale is part of the cache key, so display names refresh
 * when the user switches language (the backend ships all `name_i18n` variants
 * in one payload; we re-localize client-side without a refetch when cached).
 */
export function useAssignableRoles() {
  const { i18n } = useTranslation();
  const locale = i18n.language;
  return useQuery({
    queryKey: roleKeys.assignable(locale),
    queryFn: () => fetchAssignableRoles(locale),
    // Catalog rarely changes within a session; cache for 5 min to avoid a
    // round-trip every time a dialog opens.
    staleTime: 5 * 60 * 1000,
  });
}
