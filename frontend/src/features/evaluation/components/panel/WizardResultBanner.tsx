import { AlertTriangle } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Button } from '@/shared/components/ui/Button';
import { cn } from '@/shared/lib/cn';
import type { BulkCreatePanelsResult } from '../../panelTypes';

interface WizardResultBannerProps {
  result: BulkCreatePanelsResult | null;
  error: string | null;
  /** True once the bulk-create produced at least one panel. */
  commissionSucceeded: boolean;
  /** Whether the host wired a copy-roster callback (renders the CTA). */
  canCopyRoster: boolean;
  onCopyRoster: () => void;
}

/** Post-submit feedback: per-position bulk-create result + generic error. */
export function WizardResultBanner({
  result,
  error,
  commissionSucceeded,
  canCopyRoster,
  onCopyRoster,
}: WizardResultBannerProps) {
  const { t } = useTranslation();

  return (
    <>
      {result ? (
        <div
          role="status"
          className={cn(
            'shrink-0 rounded-md border p-3 text-sm',
            result.failed.length === 0
              ? 'bg-success-50 border-success-500/30 text-success-700'
              : 'bg-warning-50 border-warning-500/30 text-warning-700',
          )}
          data-testid="open-panel-result"
        >
          <div className="flex items-center gap-2">
            {result.failed.length > 0 ? <AlertTriangle size={14} aria-hidden /> : null}
            <span>
              {t('panel.wizard.result.summary', {
                created: result.created,
                failed: result.failed.length,
              })}
            </span>
          </div>
          {result.failed.length > 0 ? (
            <ul className="mt-2 text-xs list-disc pl-5 space-y-1 max-h-32 overflow-y-auto">
              {result.failed.slice(0, 12).map((f) => (
                <li key={f.position_id} data-testid={`open-panel-fail-${f.position_id}`}>
                  <span className="font-mono">{f.position_id.slice(0, 8)}</span>
                  {' — '}
                  {t(`panel.wizard.result.error.${f.error_code}`)}
                  {f.error_code === 'ROSTER_PARTIAL' && f.seat_failures?.length ? (
                    <ul className="mt-0.5 list-[circle] pl-4">
                      {f.seat_failures.map((sf, i) => (
                        <li key={i}>
                          {t(`panel.role.${sf.evaluator_role}`)} — {sf.reason}
                        </li>
                      ))}
                    </ul>
                  ) : null}
                </li>
              ))}
            </ul>
          ) : null}
          {commissionSucceeded && canCopyRoster ? (
            <Button
              variant="secondary"
              onClick={onCopyRoster}
              data-testid="open-panel-copy-roster"
              className="mt-3 h-8 px-2 text-xs"
            >
              {t('panel.wizard.copy_roster')}
            </Button>
          ) : null}
        </div>
      ) : null}

      {error ? (
        <div
          role="alert"
          className="shrink-0 rounded-md border border-danger-500/30 bg-danger-50 text-danger-700 text-sm p-3"
          data-testid="open-panel-error"
        >
          {error}
        </div>
      ) : null}
    </>
  );
}
