import type { ReactNode } from 'react';
import { cn } from '@/shared/lib/cn';

interface StatCardProps {
  label: string;
  value: ReactNode;
  delta?: string;
  tone?: 'default' | 'warning' | 'danger' | 'success' | 'info';
  icon?: ReactNode;
  className?: string;
  'data-testid'?: string;
}

const toneClass = {
  default: 'text-text-primary',
  warning: 'text-warning-700',
  danger: 'text-danger-700',
  success: 'text-success-700',
  info: 'text-info-600',
} as const;

export function StatCard({
  label,
  value,
  delta,
  tone = 'default',
  icon,
  className,
  'data-testid': testId,
}: StatCardProps) {
  return (
    <div
      className={cn(
        'bg-surface border border-border rounded-lg shadow-sm p-4',
        className,
      )}
      aria-label={label}
      data-testid={testId}
    >
      <div className="flex items-center justify-between gap-3">
        <span className="text-xs uppercase tracking-wide text-text-muted">{label}</span>
        {icon ? <span className="text-text-muted">{icon}</span> : null}
      </div>
      <div className={cn('mt-2 text-3xl tabular-nums', toneClass[tone])}>{value}</div>
      {delta ? <div className="text-xs text-text-secondary mt-1">{delta}</div> : null}
    </div>
  );
}
