import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { AlertTriangle, CheckCircle2, Info, X } from 'lucide-react';
import { cn } from '@/shared/lib/cn';

export type InlineBannerVariant = 'info' | 'warning' | 'success';
type InlineBannerRole = 'status' | 'alert';

interface InlineBannerProps {
  variant: InlineBannerVariant;
  /** Icon slot. Defaults to a variant-appropriate lucide icon; pass `null` to omit. */
  icon?: ReactNode;
  children: ReactNode;
  /** Renders a dismiss (×) control when provided. */
  onDismiss?: () => void;
  /** Accessible name for the dismiss control. Defaults to `common.dismiss`. */
  dismissLabel?: string;
  /**
   * ARIA live-region role override. Defaults to `alert` for `warning`
   * (interrupting) and `status` for `info`/`success` (polite) — override for
   * a warning-toned notice that is a non-blocking FYI rather than an urgent
   * alert (e.g. "results truncated, refine your filter").
   */
  role?: InlineBannerRole;
  className?: string;
  'data-testid'?: string;
}

const VARIANT_CLASS: Record<InlineBannerVariant, string> = {
  info: 'border-info-500/30 bg-info-50 text-info-600',
  warning: 'border-warning-500/40 bg-warning-50 text-warning-700',
  success: 'border-success-500/30 bg-success-50 text-success-700',
};

const DEFAULT_ROLE: Record<InlineBannerVariant, InlineBannerRole> = {
  info: 'status',
  warning: 'alert',
  success: 'status',
};

function defaultIcon(variant: InlineBannerVariant) {
  switch (variant) {
    case 'info':
      return <Info size={16} className="mt-0.5 shrink-0" aria-hidden />;
    case 'warning':
      return <AlertTriangle size={16} className="mt-0.5 shrink-0" aria-hidden />;
    case 'success':
      return <CheckCircle2 size={16} className="mt-0.5 shrink-0" aria-hidden />;
  }
}

/**
 * Small inline notice — a lighter-weight sibling of `ErrorState`/`EmptyState`
 * for messages that live inline within a page/panel rather than replacing
 * its whole content (e.g. "results truncated", a deprecate outcome, a
 * transient success confirmation). Reuses design tokens only; no new deps.
 */
export function InlineBanner({
  variant,
  icon,
  children,
  onDismiss,
  dismissLabel,
  role,
  className,
  'data-testid': testId,
}: InlineBannerProps) {
  const { t } = useTranslation();
  return (
    <div
      role={role ?? DEFAULT_ROLE[variant]}
      data-testid={testId}
      className={cn(
        'flex items-start gap-2 rounded-md border px-3 py-2 text-sm',
        VARIANT_CLASS[variant],
        className,
      )}
    >
      {icon === null ? null : (icon ?? defaultIcon(variant))}
      <div className="flex-1 min-w-0">{children}</div>
      {onDismiss ? (
        <button
          type="button"
          onClick={onDismiss}
          aria-label={dismissLabel ?? t('common.dismiss')}
          className="shrink-0 opacity-70 hover:opacity-100"
        >
          <X size={14} aria-hidden />
        </button>
      ) : null}
    </div>
  );
}
