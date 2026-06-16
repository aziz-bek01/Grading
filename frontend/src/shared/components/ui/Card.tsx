import type { HTMLAttributes, ReactNode } from 'react';
import { cn } from '@/shared/lib/cn';

interface CardProps extends Omit<HTMLAttributes<HTMLDivElement>, 'title'> {
  title?: ReactNode;
  subtitle?: ReactNode;
  action?: ReactNode;
  compact?: boolean;
  /**
   * Heading element used for {@link title}. Defaults to `h3` (a card is
   * usually a section nested under a page `h1`/`h2`). Override to keep a
   * valid document heading order (WCAG heading-order) when the card title is
   * the PRIMARY heading on a screen — e.g. the login card uses `h1`.
   */
  titleAs?: 'h1' | 'h2' | 'h3' | 'h4';
}

export function Card({
  title,
  subtitle,
  action,
  compact,
  titleAs: TitleTag = 'h3',
  className,
  children,
  ...rest
}: CardProps) {
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
            {title ? <TitleTag className="text-lg text-text-primary">{title}</TitleTag> : null}
            {subtitle ? <p className="text-sm text-text-secondary mt-1">{subtitle}</p> : null}
          </div>
          {action ? <div>{action}</div> : null}
        </header>
      )}
      {children}
    </section>
  );
}
