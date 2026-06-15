import { httpClient } from '@/shared/api/httpClient';
import { endpoints } from '@/shared/api/endpoints';

/**
 * Portfolio summary — POST-ADAPTER DOMAIN shape (camelCase).
 *
 * The REAL backend wire is SNAKE_CASE (`spring.jackson.property-naming-strategy:
 * SNAKE_CASE`) with `@JsonInclude(NON_NULL)` — see
 * backend `analytics/api/PortfolioSummaryResponse.java`. The active tenant is
 * derived from the auth context server-side (X-Active-Tenant-Id header), never
 * a query/body parameter. `lastActivityAt` is OMITTED when the tenant has no
 * activity yet, so it is optional here.
 */
export interface PortfolioSummary {
  tenantId: string | null;
  clientCompanyCount: number;
  projectCount: number;
  methodologyCount: number;
  lastActivityAt: string | null;
}

export const analyticsKeys = {
  all: ['analytics'] as const,
  portfolioSummary: () => ['analytics', 'portfolio-summary'] as const,
};

type Raw = Record<string, unknown>;

function num(raw: Raw, snake: string, camel: string): number {
  const v = raw[snake] ?? raw[camel];
  return typeof v === 'number' ? v : Number(v ?? 0) || 0;
}

/**
 * Wire → domain adapter. Snake_case first with a camelCase fallback so both the
 * real backend and the MSW mock deserialise. Binds defensively (optional
 * chaining / numeric coercion) so a partial payload never throws.
 */
export function normalizePortfolioSummary(input: unknown): PortfolioSummary {
  const raw = (input ?? {}) as Raw;
  return {
    tenantId: (raw['tenant_id'] ?? raw['tenantId'] ?? null) as string | null,
    clientCompanyCount: num(raw, 'client_company_count', 'clientCompanyCount'),
    projectCount: num(raw, 'project_count', 'projectCount'),
    methodologyCount: num(raw, 'methodology_count', 'methodologyCount'),
    lastActivityAt: (raw['last_activity_at'] ?? raw['lastActivityAt'] ?? null) as string | null,
  };
}

/** Fetch the tenant-scoped dashboard portfolio counters (requires PROJECT_READ). */
export async function fetchPortfolioSummary(): Promise<PortfolioSummary> {
  const res = await httpClient.get<unknown>(endpoints.analytics.portfolioSummary);
  return normalizePortfolioSummary(res.data);
}
