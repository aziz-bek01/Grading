import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Inbox } from 'lucide-react';
import { cn } from '@/shared/lib/cn';

interface EmptyStateProps {
  title?: string;
  body?: string;
  action?: ReactNode;
  icon?: ReactNode;
  className?: string;
}

export function EmptyState({ title, body, action, icon, className }: EmptyStateProps) {
  const { t } = useTranslation();
  return (
    <div
      className={cn(
        'flex flex-col items-center justify-center text-center px-6',
        // Default vertical padding applies only when the caller does not
        // override it (clsx has no tailwind-merge here, so a literal `py-16`
        // would race a caller's `py-10` in the generated CSS).
        className ? undefined : 'py-16',
        className,
      )}
    >
      <div className="text-text-muted mb-4">{icon ?? <Inbox size={32} aria-hidden />}</div>
      <h3 className="text-lg text-text-primary">{title ?? t('states.empty_title')}</h3>
      <p className="text-sm text-text-secondary mt-2 max-w-md">{body ?? t('states.empty_body')}</p>
      {action ? <div className="mt-4">{action}</div> : null}
    </div>
  );
}
