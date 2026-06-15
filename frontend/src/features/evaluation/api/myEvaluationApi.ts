/**
 * Evaluator self-inbox — "My evaluations" fetcher (Feature 1).
 *
 * Backend contract:
 *   GET /api/v1/evaluations/my  (gated EVALUATION_READ)
 *   Returns a JSON ARRAY (NOT a PageResponse envelope) of the caller's OWN
 *   sheets only. tenant_id / evaluator_user_id are NEVER on the wire — the
 *   backend derives both from the JWT (security blueprint API-13).
 *
 * The wire is global SNAKE_CASE; this adapter maps each row to a camelCase
 * domain shape (mirrors the evaluation/panel API layer) and pre-resolves a
 * localized title via {@link pickLocalized} with a fallback to position_code so
 * a row is never rendered title-less.
 */
import { httpClient } from '@/shared/api/httpClient';
import { endpoints } from '@/shared/api/endpoints';
import { pickLocalized } from '@/shared/lib/localized';
import type { LocalizedString } from '@/shared/types/common';
import type { EvaluationStatus } from '../types';

export const myEvaluationKeys = {
  /**
   * Tenant-scoped key. The tenant scope is implicit (JWT-derived on the
   * backend) and is NOT sent on the wire, but it IS part of the cache key so
   * the inbox refetches when the user switches tenants (FE-TI-004).
   */
  list: (tenantId: string | undefined) =>
    ['evaluations', 'my', tenantId ?? null] as const,
};

/** Raw GET /evaluations/my row (snake_case, mirrors BE MyEvaluationRow). */
interface MyEvaluationRowWire {
  evaluation_id: string;
  panel_id: string | null;
  position_id: string;
  position_code: string;
  position_title: LocalizedString;
  status: EvaluationStatus;
  filled_factors_count: number;
  total_factors_count: number;
}

/** camelCase domain row consumed by the inbox page. */
export interface MyEvaluationRow {
  evaluationId: string;
  panelId: string | null;
  positionId: string;
  positionCode: string;
  positionTitle: LocalizedString;
  /** Pre-resolved localized title (falls back to positionCode). */
  title: string;
  status: EvaluationStatus;
  filledFactorsCount: number;
  totalFactorsCount: number;
}

function adaptRow(row: MyEvaluationRowWire, locale: string): MyEvaluationRow {
  return {
    evaluationId: row.evaluation_id,
    panelId: row.panel_id,
    positionId: row.position_id,
    positionCode: row.position_code,
    positionTitle: row.position_title,
    title: pickLocalized(row.position_title, locale, row.position_code),
    status: row.status,
    filledFactorsCount: row.filled_factors_count,
    totalFactorsCount: row.total_factors_count,
  };
}

/**
 * Fetch the caller's own evaluation sheets. `locale` resolves the localized
 * title at adapt-time so the page reads a flat `row.title` (the BE returns the
 * full 4-locale map; the active UI locale picks one with a code fallback).
 */
export async function fetchMyEvaluations(
  locale: string,
): Promise<MyEvaluationRow[]> {
  const res = await httpClient.get<MyEvaluationRowWire[]>(endpoints.evaluations.my);
  const rows = Array.isArray(res.data) ? res.data : [];
  return rows.map((r) => adaptRow(r, locale));
}
