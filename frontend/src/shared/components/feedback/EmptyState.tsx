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
        'flex flex-col items-center justify-center text-center py-16 px-6',
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
