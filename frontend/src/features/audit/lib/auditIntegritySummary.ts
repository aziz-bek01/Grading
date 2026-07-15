/**
 * MVP1-E10-1 — single source of truth for turning a raw
 * `AuditIntegrityResult` (the `GET /audit/integrity` response) into an
 * HONEST, render-ready summary.
 *
 * Both `AuditIntegrityPanel` (the full "Verify integrity" result card) and
 * the compact per-event hash-chain badge in `AuditDetailsDrawer` derive
 * their tone/label from `summarizeAuditIntegrity` — so the "what does this
 * result mean" judgement call is made in exactly ONE place, not
 * re-interpreted (and potentially re-worded inconsistently, or worse,
 * over-claimed) by every consumer.
 *
 * Honesty rules encoded here (do not weaken without re-reading
 * `AuditChainVerificationResult` on the backend):
 *   - `status === 'INTACT' && bounded === true` is NOT a full pass. It is
 *     classified as `'partial'`, distinct from the full `'intact'` kind, so
 *     no consumer can accidentally render a plain green "all intact" for a
 *     capped/truncated run.
 *   - No verification having been run yet (`result` is `null`/`undefined`)
 *     is its own `'not_verified'` kind — never conflated with `'empty'`
 *     (chain has zero rows) or any success state.
 */
import type { AuditIntegrityBreakType, AuditIntegrityResult } from '../types/auditTypes';

export type AuditIntegritySummaryKind = 'not_verified' | 'intact' | 'partial' | 'broken' | 'empty';

export type AuditIntegritySummary =
  | { kind: 'not_verified'; result: null }
  | { kind: 'intact' | 'partial' | 'broken' | 'empty'; result: AuditIntegrityResult };

/**
 * Classifies a verification result. Pass `undefined`/`null` (no run yet, or
 * the query cache slot is empty) to get the honest `'not_verified'` kind.
 */
export function summarizeAuditIntegrity(
  result: AuditIntegrityResult | null | undefined,
): AuditIntegritySummary {
  if (!result) return { kind: 'not_verified', result: null };
  if (result.status === 'BROKEN') return { kind: 'broken', result };
  if (result.status === 'EMPTY') return { kind: 'empty', result };
  // INTACT — split honestly on `bounded` (see module doc).
  if (result.bounded) return { kind: 'partial', result };
  return { kind: 'intact', result };
}

/**
 * `break_type` → i18n key under the `audit.integrity.break_type` namespace.
 * Unrecognised/future break types fall back to the raw code so the UI never
 * throws or silently hides a break — see `breakTypeLabelKey`.
 */
const BREAK_TYPE_KEYS: Record<string, string> = {
  HASH_MISMATCH: 'audit.integrity.break_type.HASH_MISMATCH',
  BROKEN_PREV_LINK: 'audit.integrity.break_type.BROKEN_PREV_LINK',
  GAP: 'audit.integrity.break_type.GAP',
  VERSION_REGRESSION: 'audit.integrity.break_type.VERSION_REGRESSION',
};

/**
 * Resolves the i18n key for a `break_type` code. Returns `null` for an
 * unmapped code so the caller can fall back to `t(key, { defaultValue: code })`
 * semantics used elsewhere in this feature (e.g. `audit.action.*`).
 */
export function breakTypeLabelKey(breakType: AuditIntegrityBreakType): string | null {
  return BREAK_TYPE_KEYS[breakType] ?? null;
}
