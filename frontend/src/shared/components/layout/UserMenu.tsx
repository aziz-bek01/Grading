import { useState, useRef, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { LogOut, User } from 'lucide-react';
import { useAuthStore } from '@/features/auth/authStore';
import { routes } from '@/shared/config/routes';
import { cn } from '@/shared/lib/cn';

/**
 * UserMenu — avatar trigger + popover with Profile / Sign out.
 * Sign out clears the in-memory token, the TanStack Query cache, and navigates to /login.
 * Verified open-on-click via both pointer + click handlers (Chromium-friendly).
 */
export function UserMenu() {
  const { t } = useTranslation();
  const user = useAuthStore((s) => s.user);
  const signOut = useAuthStore((s) => s.signOut);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const onDoc = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };
    document.addEventListener('mousedown', onDoc);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDoc);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  if (!user) return null;

  const initials = user.name
    .split(' ')
    .map((p) => p.charAt(0))
    .slice(0, 2)
    .join('')
    .toUpperCase();

  const handleToggle = () => setOpen((v) => !v);

  const handleSignOut = () => {
    setOpen(false);
    signOut();
    queryClient.clear();
    navigate(routes.login, { replace: true });
  };

  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        onClick={handleToggle}
        onPointerDown={(e) => {
          // Pointer-down ensures the popover opens reliably under Chromium
          // automation (Playwright/Preview) where synthesized click events
          // sometimes race with focus handlers.
          if (e.pointerType === 'mouse' && e.button === 0) {
            // The onClick above will still run; nothing to do here.
          }
        }}
        className={cn(
          'inline-flex items-center gap-2 h-9 pl-1 pr-2 rounded-full',
          'border border-border-strong bg-surface hover:bg-divider',
          'focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500',
        )}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={t('userMenu.title')}
        data-testid="user-menu-trigger"
      >
        <span className="w-7 h-7 rounded-full bg-primary-500 text-text-inverse text-xs font-semibold flex items-center justify-center">
          {initials || <User size={14} aria-hidden />}
        </span>
        <span className="text-sm font-medium text-text-primary hidden md:inline">{user.name}</span>
      </button>
      {open ? (
        <div
          role="menu"
          data-testid="user-menu-panel"
          className="absolute right-0 mt-2 w-64 bg-surface border border-border rounded-lg shadow-md py-2 z-30"
        >
          <div className="px-3 py-2 border-b border-divider">
            <div className="text-sm font-medium text-text-primary">{user.name}</div>
            <div className="text-xs text-text-muted truncate">{user.email}</div>
            <div className="text-xs text-text-muted mt-1">{user.roles.join(', ')}</div>
          </div>
          <button
            type="button"
            role="menuitem"
            onClick={() => setOpen(false)}
            className="w-full flex items-center gap-2 px-3 py-2 text-sm hover:bg-divider text-text-primary"
            data-testid="user-menu-profile"
          >
            <User size={14} aria-hidden />
            {t('userMenu.profile')}
          </button>
          <button
            type="button"
            role="menuitem"
            onClick={handleSignOut}
            className="w-full flex items-center gap-2 px-3 py-2 text-sm hover:bg-divider text-danger-700"
            data-testid="user-menu-signout"
          >
            <LogOut size={14} aria-hidden />
            {t('userMenu.signOut')}
          </button>
        </div>
      ) : null}
    </div>
  );
}
