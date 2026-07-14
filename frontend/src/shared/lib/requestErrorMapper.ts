/**
 * Shared "describe a failed async-job request" mapper.
 *
 * `POST /reports/request` and `POST /exports/request` (and any future
 * request-a-job endpoint) can 4xx/5xx for reasons the caller needs to *see* —
 * a dialog that swallows the rejection looks like "nothing happened". Both
 * dialogs need the same shape of feedback:
 *   - 403 -> a feature-specific "you don't have permission" message.
 *   - a known backend error code -> a feature-specific, actionable message.
 *   - anything else -> a generic fallback that still carries the backend
 *     error code + correlation/trace id so the failure is reportable.
 *
 * This module owns only the generic mapping (forbidden / known-code lookup /
 * generic fallback); the i18n keys and the known-code table stay with each
 * feature (`report.error.*`, `export.error.*`, ...) since the domains differ.
 */
import { ApiError } from '@/shared/api/apiError';

export type TranslateFn = (key: string, opts?: Record<string, unknown>) => string;

export interface DescribeRequestErrorOptions {
  /** i18n key rendered when the backend rejects with 403. */
  permissionDeniedKey: string;
  /** i18n key for the generic fallback; interpolated with `{{code}}` + `{{ref}}`. */
  genericFailedKey: string;
  /** Backend `ApiError.code` -> i18n key, for codes with a friendlier, actionable message. */
  knownCodes?: Record<string, string>;
}

/**
 * Maps a failed request-a-job mutation to a localized, user-facing message.
 * Feature dialogs pass their own i18n keys (and, optionally, a table of
 * known backend error codes) rather than duplicating this branching logic.
 */
export function describeRequestError(
  err: unknown,
  t: TranslateFn,
  { permissionDeniedKey, genericFailedKey, knownCodes }: DescribeRequestErrorOptions,
): string {
  if (err instanceof ApiError) {
    if (err.isForbidden()) return t(permissionDeniedKey);
    const mappedKey = knownCodes?.[err.code];
    if (mappedKey) return t(mappedKey);
    return t(genericFailedKey, {
      code: err.code,
      ref: err.correlationId ?? err.traceId ?? '—',
    });
  }
  return t(genericFailedKey, { code: 'NETWORK', ref: '—' });
}
