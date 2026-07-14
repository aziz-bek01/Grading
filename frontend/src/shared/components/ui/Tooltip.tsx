import {
  cloneElement,
  isValidElement,
  useId,
  useState,
  type FocusEvent,
  type KeyboardEvent,
  type MouseEvent,
  type ReactElement,
  type ReactNode,
} from 'react';
import { cn } from '@/shared/lib/cn';

export type TooltipPlacement = 'top' | 'bottom';

interface TriggerProps {
  'aria-describedby'?: string;
  onMouseEnter?: (e: MouseEvent<HTMLElement>) => void;
  onMouseLeave?: (e: MouseEvent<HTMLElement>) => void;
  onFocus?: (e: FocusEvent<HTMLElement>) => void;
  onBlur?: (e: FocusEvent<HTMLElement>) => void;
  onKeyDown?: (e: KeyboardEvent<HTMLElement>) => void;
}

export interface TooltipProps {
  /** Short explanatory text/content rendered inside `role="tooltip"`. */
  content: ReactNode;
  /**
   * Single trigger element (e.g. a `Button`/`IconButton`). Its DOM node gets
   * `aria-describedby` wired to the tooltip id plus the hover/focus/blur/Escape
   * handlers merged onto any handlers it already defines.
   */
  children: ReactElement<TriggerProps>;
  /** Bubble position relative to the trigger. Defaults to `top`. */
  placement?: TooltipPlacement;
  className?: string;
}

const PLACEMENT_CLASS: Record<TooltipPlacement, string> = {
  top: 'bottom-full left-1/2 mb-1.5 -translate-x-1/2',
  bottom: 'top-full left-1/2 mt-1.5 -translate-x-1/2',
};

/**
 * Minimal accessible tooltip — no external dependency, pure React + Tailwind.
 *
 * Reveals `content` on hover AND keyboard focus of the trigger, dismisses on
 * Escape or blur/mouse-leave. The trigger keeps working (click, disabled
 * styling, etc.) exactly as before; the tooltip only adds the hover/focus
 * wiring + `aria-describedby` via `cloneElement` on the single child, so the
 * accessible description lives on the actual interactive element (not a
 * detached wrapper) per WAI-ARIA APG tooltip pattern.
 */
export function Tooltip({ content, children, placement = 'top', className }: TooltipProps) {
  const [open, setOpen] = useState(false);
  const tooltipId = useId();

  if (!isValidElement<TriggerProps>(children)) return children;

  const show = () => setOpen(true);
  const hide = () => setOpen(false);

  const trigger = cloneElement(children, {
    'aria-describedby': open ? tooltipId : undefined,
    onMouseEnter: (e: MouseEvent<HTMLElement>) => {
      children.props.onMouseEnter?.(e);
      show();
    },
    onMouseLeave: (e: MouseEvent<HTMLElement>) => {
      children.props.onMouseLeave?.(e);
      hide();
    },
    onFocus: (e: FocusEvent<HTMLElement>) => {
      children.props.onFocus?.(e);
      show();
    },
    onBlur: (e: FocusEvent<HTMLElement>) => {
      children.props.onBlur?.(e);
      hide();
    },
    onKeyDown: (e: KeyboardEvent<HTMLElement>) => {
      children.props.onKeyDown?.(e);
      if (e.key === 'Escape' && open) hide();
    },
  });

  return (
    <span className={cn('relative inline-flex', className)}>
      {trigger}
      {open ? (
        <span
          role="tooltip"
          id={tooltipId}
          className={cn(
            'pointer-events-none absolute z-50 w-max max-w-xs whitespace-normal rounded-md bg-text-primary px-2 py-1 text-xs text-text-inverse shadow-md',
            PLACEMENT_CLASS[placement],
          )}
        >
          {content}
        </span>
      ) : null}
    </span>
  );
}
