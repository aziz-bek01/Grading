import { useTranslation } from 'react-i18next';
import { cn } from '@/shared/lib/cn';

interface LoadingStateProps {
  label?: string;
  className?: string;
}

export function LoadingState({ label, className }: LoadingStateProps) {
  const { t } = useTranslation();
  return (
    <div
      role="status"
      aria-live="polite"
      className={cn(
        'flex flex-col items-center justify-center text-text-secondary py-12',
        className,
      )}
    >
      <div
        className="h-6 w-6 rounded-full border-2 border-primary-200 border-t-primary-500 animate-spin"
        aria-hidden
      />
      <p className="mt-3 text-sm">{label ?? t('states.loading')}</p>
    </div>
  );
}
