import type { ReactNode } from 'react';

/**
 * ThemeProvider — placeholder for MVP 1.
 * Tailwind tokens drive the theme; this is reserved for future dark-mode toggle.
 */
export function ThemeProvider({ children }: { children: ReactNode }) {
  return <div className="grading-theme-root">{children}</div>;
}
