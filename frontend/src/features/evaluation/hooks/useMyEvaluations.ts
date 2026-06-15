/**
 * Evaluator self-inbox hook (Feature 1).
 *
 * Keyed by the active tenant so the inbox refetches on tenant switch (tenant is
 * JWT-derived on the backend, never sent on the wire). The active UI locale is
 * folded into the fetcher (NOT the key) so the title is re-resolved on a
 * language switch without an extra network round-trip — i18next re-renders the
 * consumer, which re-reads the already-cached row map via {@link useMemo} in the
 * page. We pass the locale to the fetcher so a fresh fetch resolves correctly;
 * the key stays tenant-scoped.
 */
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { useAuthStore } from '@/features/auth/authStore';
import { fetchMyEvaluations, myEvaluationKeys } from '../api/myEvaluationApi';

export function useMyEvaluations() {
  const { i18n } = useTranslation();
  const tenantScope = useAuthStore((s) => s.activeTenant?.id);
  return useQuery({
    queryKey: myEvaluationKeys.list(tenantScope),
    queryFn: () => fetchMyEvaluations(i18n.language),
  });
}
