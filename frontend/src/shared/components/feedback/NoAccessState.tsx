import { useTranslation } from 'react-i18next';
import { ShieldOff } from 'lucide-react';
import { cn } from '@/shared/lib/cn';

interface NoAccessStateProps {
  title?: string;
  body?: string;
  className?: string;
}

/**
 * Friendly, non-enumerating "no access" state.
 * Per security blueprint, never reveal whether a resource exists for another tenant.
 */
export function NoAccessState({ title, body, className }: NoAccessStateProps) {
  const { t } = useTranslation();
  return (
    <div
      className={cn(
        'flex flex-col items-center justify-center text-center py-16 px-6',
        className,
      )}
    >
      <div className="text-locked mb-4">
        <ShieldOff size={32} aria-hidden />
      </div>
      <h3 className="text-lg text-text-primary">{title ?? t('states.no_access_title')}</h3>
      <p className="text-sm text-text-secondary mt-2 max-w-md">{body ?? t('states.no_access_body')}</p>
    </div>
  );
}
