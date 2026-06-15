import { useQuery } from '@tanstack/react-query';
import { analyticsKeys, fetchPortfolioSummary } from '../api/analyticsApi';

/**
 * Tenant-scoped dashboard portfolio counters. Re-keyed per active tenant by
 * TanStack Query's default behaviour (the request carries X-Active-Tenant-Id);
 * the active tenant id is included in the cache key so a tenant switch refetches
 * rather than showing the previous tenant's numbers.
 */
export function usePortfolioSummary(activeTenantId: string | null | undefined) {
  return useQuery({
    queryKey: [...analyticsKeys.portfolioSummary(), activeTenantId ?? null],
    queryFn: fetchPortfolioSummary,
    staleTime: 60_000,
  });
}
