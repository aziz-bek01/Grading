import { useState, type ReactNode } from 'react';
import { ChevronDown, ChevronRight } from 'lucide-react';
import { cn } from '@/shared/lib/cn';

interface JobProfileSectionGroupProps {
  title: string;
  subtitle?: string;
  defaultOpen?: boolean;
  children: ReactNode;
}

/** Collapsible section grouping job-profile field editors. */
export function JobProfileSectionGroup({
  title,
  subtitle,
  defaultOpen = true,
  children,
}: JobProfileSectionGroupProps) {
  const [open, setOpen] = useState(defaultOpen);
  return (
    <section className="bg-surface border border-border rounded-lg shadow-sm">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        className="w-full flex items-center justify-between gap-3 p-4 text-left"
        aria-expanded={open}
      >
        <div>
          <h3 className="text-md text-text-primary font-medium">{title}</h3>
          {subtitle ? <p className="text-xs text-text-secondary mt-0.5">{subtitle}</p> : null}
        </div>
        <span className="text-text-secondary" aria-hidden>
          {open ? <ChevronDown size={18} /> : <ChevronRight size={18} />}
        </span>
      </button>
      <div className={cn('border-t border-divider p-4 space-y-5', !open && 'hidden')}>{children}</div>
    </section>
  );
}
