import {
  useEffect,
  useRef,
  useState,
  type KeyboardEvent as ReactKeyboardEvent,
  type ReactNode,
  type RefObject,
} from 'react';
import { cn } from '@/shared/lib/cn';

export type ModalSize = 'sm' | 'md' | 'lg' | 'xl';

const SIZE_CLASS: Record<ModalSize, string> = {
  sm: 'max-w-sm',
  md: 'max-w-md',
  lg: 'max-w-lg',
  xl: 'max-w-xl',
};

/**
 * Elements that can receive keyboard focus — drives both the Tab focus trap
 * and the (opt-in) initial-focus fallback. Mirrors the WAI-ARIA APG dialog
 * pattern's focusable set; `tabindex="-1"` elements are deliberately
 * excluded (they are only reachable programmatically, e.g. a warning banner
 * a caller focuses via `initialFocusRef`).
 */
const FOCUSABLE_SELECTOR =
  'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

export interface ModalProps {
  open: boolean;
  onClose: () => void;
  /**
   * id of the caller-rendered heading element that names the dialog
   * (wired to `aria-labelledby`). Every migrated dialog renders its own
   * `<h2 id="...">` inside `children` — pass that id here.
   */
  labelledBy?: string;
  /**
   * id of a caller-rendered element that further describes the dialog
   * (wired to `aria-describedby`) — e.g. a consequence/warning paragraph.
   */
  describedBy?: string;
  /**
   * Accessible name used instead of `labelledBy` when the dialog has no
   * visible heading of its own (e.g. an inline confirm popover). Ignored
   * when `labelledBy` is set.
   */
  ariaLabel?: string;
  /**
   * Panel max-width preset. Defaults to `md`. Callers add height / flex /
   * overflow / background / border / padding via `className` — `Modal` only
   * owns the overlay, centering, and max-width, never the panel's visual
   * skin (which differs per dialog).
   */
  size?: ModalSize;
  /**
   * Element to focus once the modal finishes opening. Omit to leave focus
   * untouched — this matches most of this app's dialogs, which do not
   * steal focus on open. When provided but `.current` is `null` at open
   * time, no element is focused (lets a caller conditionally opt out, e.g.
   * `ConfirmDialog`'s `requireReason` branch).
   */
  initialFocusRef?: RefObject<HTMLElement | null>;
  /**
   * When `false`, Escape and backdrop-click are both disabled — use while a
   * mutation is in flight so a pending action is never silently abandoned.
   * Defaults to `true`.
   */
  dismissible?: boolean;
  /** Extra classes merged onto the panel element. */
  className?: string;
  children: ReactNode;
  'data-testid'?: string;
}

/**
 * Shared centered-dialog chrome (FE-016 de-duplication).
 *
 * Encapsulates what ~15+ hand-rolled dialogs across the app repeated: the
 * fixed overlay/backdrop, a centered `role="dialog"` / `aria-modal="true"`
 * panel, Escape-to-close, click-outside-to-close (guarded so a click
 * starting *inside* the panel never bubbles into a close), an opt-in
 * initial-focus hook, a Tab focus trap, focus restore to the
 * previously-focused element on close, a body-scroll lock while open, and
 * `prefers-reduced-motion` respect for the entrance transition.
 *
 * Purely presentational — it owns no business logic, no copy, and no
 * dialog-specific markup. Every caller keeps its own header / body / footer
 * JSX, strings, and `data-testid`s; `Modal` only replaces the duplicated
 * `fixed inset-0 z-50 ...` wrapper + the interaction wiring around it.
 */
export function Modal({
  open,
  onClose,
  labelledBy,
  describedBy,
  ariaLabel,
  size = 'md',
  initialFocusRef,
  dismissible = true,
  className,
  children,
  'data-testid': testId,
}: ModalProps) {
  const panelRef = useRef<HTMLDivElement>(null);
  const previouslyFocused = useRef<HTMLElement | null>(null);
  // Drives the entrance fade — purely cosmetic, gated by
  // `motion-reduce:transition-none` below so a reduced-motion user just
  // sees the end state immediately instead of an animated fade.
  const [entered, setEntered] = useState(false);

  // Reset `entered` the moment `open` flips closed -> ... — done during
  // render via a previous-value tracked in STATE (the same
  // adjust-state-on-prop-change pattern used by ConfirmDialog) rather than a
  // setState-in-effect, so the next open always starts from a fresh fade-in.
  const [seenOpen, setSeenOpen] = useState(open);
  if (seenOpen !== open) {
    setSeenOpen(open);
    if (!open && entered) setEntered(false);
  }

  // Escape-to-close (guarded by `dismissible`).
  useEffect(() => {
    if (!open || !dismissible) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [open, dismissible, onClose]);

  // Body-scroll lock + opt-in initial focus + focus restore on close, all
  // scoped to the open lifetime of this instance.
  useEffect(() => {
    if (!open) return;
    previouslyFocused.current = document.activeElement as HTMLElement | null;

    const prevOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';

    const raf = requestAnimationFrame(() => {
      setEntered(true);
      initialFocusRef?.current?.focus();
    });

    return () => {
      cancelAnimationFrame(raf);
      document.body.style.overflow = prevOverflow;
      previouslyFocused.current?.focus?.();
    };
    // `initialFocusRef` is a ref identity, not a reactive dependency; only
    // `open` should re-run this lifecycle.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  // Tab focus trap: cycle focus within the panel. Also treats "focus is
  // currently on a programmatically-focused element outside the tabbable
  // set" (e.g. a `tabindex="-1"` warning banner used as `initialFocusRef`)
  // as an alias for "before the first tabbable element", so Shift+Tab from
  // there wraps to the last control instead of escaping the trap.
  const onKeyDownTrap = (e: ReactKeyboardEvent) => {
    if (e.key !== 'Tab') return;
    const root = panelRef.current;
    if (!root) return;
    const focusables = Array.from(root.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR));
    if (focusables.length === 0) return;
    const first = focusables[0];
    const last = focusables[focusables.length - 1];
    const active = document.activeElement as HTMLElement | null;
    const activeIndex = active ? focusables.indexOf(active) : -1;
    if (e.shiftKey && (activeIndex === 0 || activeIndex === -1)) {
      e.preventDefault();
      last.focus();
    } else if (!e.shiftKey && activeIndex === focusables.length - 1) {
      e.preventDefault();
      first.focus();
    }
  };

  if (!open) return null;

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby={labelledBy}
      aria-describedby={describedBy}
      aria-label={labelledBy ? undefined : ariaLabel}
      data-testid={testId}
      className={cn(
        'fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4',
        'transition-opacity duration-150 ease-out motion-reduce:transition-none',
        entered ? 'opacity-100' : 'opacity-0',
      )}
      onClick={(e) => {
        if (dismissible && e.target === e.currentTarget) onClose();
      }}
      onKeyDown={onKeyDownTrap}
    >
      <div ref={panelRef} className={cn('relative w-full', SIZE_CLASS[size], className)}>
        {children}
      </div>
    </div>
  );
}
