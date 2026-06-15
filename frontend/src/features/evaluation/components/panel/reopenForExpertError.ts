/**
 * Feature 2 — localized error mapping for the reopen-for-expert flow.
 *
 * Kept in its OWN module (not the dialog component file) so both the dialog host
 * (PanelDetailPage) and the tests share ONE mapping without tripping
 * react-refresh's component-only-export rule. The BE is the source of truth —
 * this only renders what it returned, mapping the stable error codes
 * (esp. PANEL_NOT_APPROVED_FOR_REOPEN) to user-facing copy.
 */
import { ApiError } from '@/shared/api/apiError';

export function describeReopenForExpertError(
  err: unknown,
  t: (key: string, opts?: Record<string, unknown>) => string,
): string {
  if (!(err instanceof ApiError)) return t('panel.reopen_expert.error_generic');
  if (err.status === 0) return t('panel.detail.error_network');
  if (err.code === 'PANEL_NOT_APPROVED_FOR_REOPEN') {
    return t('panel.reopen_expert.error_not_approved');
  }
  if (err.isForbidden()) return t('panel.reopen_expert.error_forbidden');
  if (err.isNotFound()) return t('panel.reopen_expert.error_not_found');
  if (err.isValidation()) return t('panel.reopen_expert.error_validation');
  return t('panel.reopen_expert.error_generic');
}
