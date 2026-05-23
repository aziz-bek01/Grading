import { useEffect, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { X } from 'lucide-react';
import { Button } from '@/shared/components/ui/Button';
import { cn } from '@/shared/lib/cn';

interface DrawerFormProps {
  open: boolean;
  title: string;
  subtitle?: string;
  onClose: () => void;
  onSubmit?: () => void;
  submitLabel?: string;
  /** Hidden when the entity is locked/read-only. */
  readOnly?: boolean;
  children: ReactNode;
  /** When true, hides the default Save button (caller renders own actions). */
  customActions?: ReactNode;
}

/**
 * Side drawer used for create/edit forms across Phase 2.
 * Slide-in from the right; Esc closes; focuses first input.
 */
export function DrawerForm({
  open,
  title,
  subtitle,
  onClose,
  onSubmit,
  submitLabel,
  readOnly,
  children,
  customActions,
}: DrawerFormProps) {
  const { t } = useTranslation();

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="drawer-form-title"
      className="fixed inset-0 z-50 flex"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div className="absolute inset-0 bg-black/30" aria-hidden />
      <aside
        className={cn(
          'relative ml-auto h-full w-full max-w-md bg-surface shadow-lg flex flex-col',
          'border-l border-border',
        )}
      >
        <header className="flex items-start justify-between gap-3 p-5 border-b border-divider">
          <div>
            <h2 id="drawer-form-title" className="text-lg text-text-primary">
              {title}
            </h2>
            {subtitle ? <p className="text-sm text-text-secondary mt-1">{subtitle}</p> : null}
          </div>
          <button
            type="button"
            aria-label={t('common.close')}
            onClick={onClose}
            className="p-1 rounded-md text-text-secondary hover:bg-divider"
          >
            <X size={18} />
          </button>
        </header>
        <form
          onSubmit={(e) => {
            e.preventDefault();
            if (onSubmit && !readOnly) onSubmit();
          }}
          className="flex-1 flex flex-col overflow-hidden"
        >
          <div className="flex-1 overflow-y-auto p-5 space-y-4">{children}</div>
          <footer className="border-t border-divider p-4 flex items-center justify-end gap-2">
            {customActions ?? (
              <>
                <Button variant="secondary" onClick={onClose} type="button">
                  {t('common.cancel')}
                </Button>
                <Button variant="primary" type="submit" disabled={readOnly}>
                  {submitLabel ?? t('common.save')}
                </Button>
              </>
            )}
          </footer>
        </form>
      </aside>
    </div>
  );
}
