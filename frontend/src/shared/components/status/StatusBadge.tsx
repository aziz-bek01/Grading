import { type ReactNode } from 'react';
import {
  CheckCircle2,
  Clock,
  FilePenLine,
  Lock,
  Archive,
  AlertTriangle,
  Shield,
  Sparkles,
  OctagonAlert,
} from 'lucide-react';
import { cn } from '@/shared/lib/cn';

export type StatusTone =
  | 'draft'
  | 'in-review'
  | 'approved'
  | 'locked'
  | 'archived'
  | 'incomplete'
  | 'needs-attention'
  | 'salary-protected'
  | 'ai-suggestion';

interface ToneSpec {
  bg: string;
  fg: string;
  border: string;
  icon: ReactNode;
}

const toneMap: Record<StatusTone, ToneSpec> = {
  draft: {
    bg: 'bg-divider',
    fg: 'text-text-secondary',
    border: 'border-border-strong',
    icon: <FilePenLine size={12} />,
  },
  'in-review': {
    bg: 'bg-info-50',
    fg: 'text-info-600',
    border: 'border-info-500/30',
    icon: <Clock size={12} />,
  },
  approved: {
    bg: 'bg-success-50',
    fg: 'text-success-700',
    border: 'border-success-500/30',
    icon: <CheckCircle2 size={12} />,
  },
  locked: {
    bg: 'bg-locked-bg',
    fg: 'text-locked',
    border: 'border-locked/40',
    icon: <Lock size={12} />,
  },
  archived: {
    bg: 'bg-divider',
    fg: 'text-text-muted',
    border: 'border-border-strong',
    icon: <Archive size={12} />,
  },
  incomplete: {
    bg: 'bg-warning-50',
    fg: 'text-warning-700',
    border: 'border-warning-500/30',
    icon: <AlertTriangle size={12} />,
  },
  'needs-attention': {
    bg: 'bg-danger-50',
    fg: 'text-danger-700',
    border: 'border-danger-500/30',
    icon: <OctagonAlert size={12} />,
  },
  'salary-protected': {
    bg: 'bg-salary-sensitive-bg',
    fg: 'text-salary-sensitive',
    border: 'border-salary-sensitive/30',
    icon: <Shield size={12} />,
  },
  'ai-suggestion': {
    bg: 'bg-ai-suggestion-bg',
    fg: 'text-ai-suggestion',
    border: 'border-ai-suggestion/30',
    icon: <Sparkles size={12} />,
  },
};

interface StatusBadgeProps {
  tone: StatusTone;
  label: string;
  /** outline variant for use in dense tables */
  outline?: boolean;
  className?: string;
}

/**
 * Icon + label pill — never relies on color alone (WCAG a11y).
 * 9 tones per design-foundation §3.6 / §10.
 */
export function StatusBadge({ tone, label, outline, className }: StatusBadgeProps) {
  // Defensive: an unknown/undefined tone must never crash the whole page with
  // `toneMap[undefined].bg`. Fall back to the neutral `draft` spec.
  const t = toneMap[tone] ?? toneMap.draft;
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 text-xs font-medium px-2 py-0.5 rounded-full border',
        outline ? 'bg-transparent' : t.bg,
        t.fg,
        t.border,
        className,
      )}
      aria-label={label}
    >
      {t.icon}
      <span>{label}</span>
    </span>
  );
}
