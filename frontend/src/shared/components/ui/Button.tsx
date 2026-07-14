import { forwardRef, type ButtonHTMLAttributes, type ReactNode } from 'react';
import { cn } from '@/shared/lib/cn';

export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger';
export type ButtonSize = 'compact' | 'sm' | 'md' | 'lg';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  leadingIcon?: ReactNode;
  trailingIcon?: ReactNode;
  fullWidth?: boolean;
}

const variantClass: Record<ButtonVariant, string> = {
  primary:
    'bg-primary-500 text-text-inverse hover:bg-primary-600 active:bg-primary-700 disabled:bg-primary-200 disabled:text-text-inverse',
  secondary:
    'bg-surface text-text-primary border border-border-strong hover:bg-divider disabled:text-text-disabled',
  ghost:
    'bg-transparent text-text-primary hover:bg-divider disabled:text-text-disabled',
  danger:
    'bg-danger-500 text-text-inverse hover:bg-danger-600 active:bg-danger-700 disabled:bg-danger-50 disabled:text-danger-700',
};

const sizeClass: Record<ButtonSize, string> = {
  // Dense-UI footprint (no fixed height — padding + line-height define it, like
  // form-adjacent toolbar buttons in wizards/tables). Matches the ad-hoc
  // `px-3 py-2 text-sm` convention previously hand-rolled across the app so
  // migrating callers to `size="compact"` doesn't reflow tight layouts.
  compact: 'px-3 py-2 text-sm rounded-md',
  sm: 'h-8 px-3 text-sm rounded-md',
  md: 'h-10 px-4 text-sm rounded-md',
  lg: 'h-12 px-6 text-md rounded-md',
};

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { variant = 'primary', size = 'md', leadingIcon, trailingIcon, fullWidth, className, children, ...rest },
  ref,
) {
  return (
    <button
      ref={ref}
      type={rest.type ?? 'button'}
      className={cn(
        'inline-flex items-center justify-center gap-2 font-medium transition-colors',
        'disabled:cursor-not-allowed',
        variantClass[variant],
        sizeClass[size],
        fullWidth && 'w-full',
        className,
      )}
      {...rest}
    >
      {leadingIcon ? <span className="inline-flex items-center">{leadingIcon}</span> : null}
      <span>{children}</span>
      {trailingIcon ? <span className="inline-flex items-center">{trailingIcon}</span> : null}
    </button>
  );
});
