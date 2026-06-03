import { useEffect, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { X } from 'lucide-react';
import { cn } from '@/shared/lib/cn';

interface DrawerProps {
  open: boolean;
  title: string;
  onClose: () => void;
  /** Slide-in edge. Defaults to the right (HRLab convention). */
  side?: 'right' | 'left';
  /** Tailwind max-width utility for the panel. Defaults to `max-w-md`. */
  widthClassName?: string;
  children: ReactNode;
  'data-testid'?: string;
}

/**
 * Generic, form-less slide-over drawer.
 *
 * Distinct from `DrawerForm` (which wraps its body in a <form> + Save/Cancel
 * footer): this host renders arbitrary interactive content with no implied
 * submit semantics. It exists so feature views can present an *existing*
 * panel component (e.g. the by-factor `<RubricPanel>`) on narrow viewports
 * without duplicating that panel's markup.
 *
 * Behaviour: overlay click + Esc close; body scroll lock while open.
 */
export function Drawer({
  open,
  title,
  onClose,
  side = 'right',
  widthClassName = 'max-w-md',
  children,
  'data-testid': testId,
}: DrawerProps) {
  const { t } = useTranslation();

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKey);
    const prevOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = prevOverflow;
    };
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label={title}
      className="fixed inset-0 z-50 flex"
      data-testid={testId}
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div className="absolute inset-0 bg-black/30" aria-hidden />
      <aside
        className={cn(
          'relative h-full w-full bg-surface shadow-lg flex flex-col',
          widthClassName,
          side === 'right' ? 'ml-auto border-l border-border' : 'mr-auto border-r border-border',
        )}
      >
        <header className="flex items-center justify-between gap-3 p-4 border-b border-divider">
          <h2 className="text-base font-semibold text-text-primary">{title}</h2>
          <button
            type="button"
            aria-label={t('common.close')}
            onClick={onClose}
            className="p-1 rounded-md text-text-secondary hover:bg-divider"
          >
            <X size={18} />
          </button>
        </header>
        <div className="flex-1 overflow-y-auto p-4">{children}</div>
      </aside>
    </div>
  );
}
