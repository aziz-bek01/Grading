import type { MouseEvent, ReactNode } from 'react';
import { cn } from '@/shared/lib/cn';

export interface IconButtonProps {
  /** Accessible name — also used as the native `title` tooltip. */
  label: string;
  /** `data-testid` for the button. */
  testId?: string;
  /** Danger (destructive-action) color treatment, e.g. delete/archive. */
  danger?: boolean;
  onClick: (e: MouseEvent<HTMLButtonElement>) => void;
  children: ReactNode;
  disabled?: boolean;
  className?: string;
}

/**
 * Small square icon-only button (edit/archive/delete/close/download, etc.)
 * used inside table action columns and toolbars. Extracted from identical
 * inline implementations in `ProjectTable` / `PositionTable` (DUP sweep) —
 * every prop, class and the `title` + `aria-label` pairing is preserved
 * exactly so existing tests/testids keep passing unmodified.
 */
export function IconButton({
  label,
  testId,
  danger,
  onClick,
  children,
  disabled,
  className,
}: IconButtonProps) {
  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      data-testid={testId}
      onClick={onClick}
      disabled={disabled}
      className={cn(
        'inline-flex items-center justify-center h-8 w-8 rounded-md transition-colors',
        'focus:outline-none focus:ring-2 focus:ring-primary-500',
        'disabled:cursor-not-allowed disabled:opacity-50',
        danger
          ? 'text-danger-700 hover:bg-danger-50'
          : 'text-text-secondary hover:bg-divider hover:text-text-primary',
        className,
      )}
    >
      {children}
    </button>
  );
}
