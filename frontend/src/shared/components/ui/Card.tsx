import type { HTMLAttributes, ReactNode } from 'react';
import { cn } from '@/shared/lib/cn';

interface CardProps extends Omit<HTMLAttributes<HTMLDivElement>, 'title'> {
  title?: ReactNode;
  subtitle?: ReactNode;
  action?: ReactNode;
  compact?: boolean;
}

export function Card({ title, subtitle, action, compact, className, children, ...rest }: CardProps) {
  return (
    <section
      {...rest}
      className={cn(
        'bg-surface border border-border rounded-lg shadow-sm',
        compact ? 'p-4' : 'p-6',
        className,
      )}
    >
      {(title || action) && (
        <header className="flex items-start justify-between gap-4 mb-4">
          <div>
            {title ? <h3 className="text-lg text-text-primary">{title}</h3> : null}
            {subtitle ? <p className="text-sm text-text-secondary mt-1">{subtitle}</p> : null}
          </div>
          {action ? <div>{action}</div> : null}
        </header>
      )}
      {children}
    </section>
  );
}
