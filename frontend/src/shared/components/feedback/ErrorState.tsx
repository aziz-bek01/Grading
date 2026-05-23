import { useTranslation } from 'react-i18next';
import { OctagonAlert } from 'lucide-react';
import { Button } from '@/shared/components/ui/Button';
import { cn } from '@/shared/lib/cn';

interface ErrorStateProps {
  title?: string;
  body?: string;
  correlationId?: string;
  onRetry?: () => void;
  className?: string;
}

export function ErrorState({ title, body, correlationId, onRetry, className }: ErrorStateProps) {
  const { t } = useTranslation();
  return (
    <div
      role="alert"
      className={cn(
        'rounded-lg border border-danger-500/30 bg-danger-50 p-6 text-danger-700',
        className,
      )}
    >
      <div className="flex items-start gap-3">
        <OctagonAlert size={22} aria-hidden className="mt-0.5" />
        <div className="flex-1">
          <h3 className="font-semibold text-text-primary">{title ?? t('states.error_title')}</h3>
          <p className="text-sm text-text-secondary mt-1">{body ?? t('states.error_body')}</p>
          {correlationId ? (
            <p className="text-xs text-text-muted mt-2 font-mono">
              {t('states.correlation_id')}: {correlationId}
            </p>
          ) : null}
          {onRetry ? (
            <Button variant="secondary" size="sm" onClick={onRetry} className="mt-3">
              {t('common.retry')}
            </Button>
          ) : null}
        </div>
      </div>
    </div>
  );
}
