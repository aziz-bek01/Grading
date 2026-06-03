import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { Check, Circle, Zap } from 'lucide-react';
import { cn } from '@/shared/lib/cn';
import { pickLocalized } from '@/shared/lib/localized';
import type { Factor } from '@/features/methodology/types';

export type FactorCompletionState = 'full' | 'partial' | 'empty';

/** Map of factor.id -> completion state across the project. */
export type FactorCompletionMap = Record<string, FactorCompletionState>;

interface FactorTabsProps {
  factors: Factor[];
  activeFactorId: string | null;
  /**
   * Per-factor completion map. When omitted the dot indicator is hidden
   * (e.g. while the first request is still loading).
   */
  completion?: FactorCompletionMap;
  onSelect: (factorId: string) => void;
  className?: string;
}

/**
 * Horizontal factor selector — Excel sheet-tab metaphor.
 *
 * Renders one tab per factor (sorted by `sort_order`). The active tab is
 * underlined with the primary accent. Each tab carries a tiny completion
 * indicator (full / partial / empty) so the evaluator can see which
 * sheets still need work without clicking through.
 *
 * Keyboard: ←/→ rotation is handled by the parent via Tab; tabs are
 * plain buttons so AT users can navigate them via screen-reader rotor.
 */
export function FactorTabs({
  factors,
  activeFactorId,
  completion,
  onSelect,
  className,
}: FactorTabsProps) {
  const { i18n } = useTranslation();

  const sorted = useMemo(
    () => [...factors].sort((a, b) => a.sort_order - b.sort_order),
    [factors],
  );

  return (
    <div
      role="tablist"
      aria-label="Factor selector"
      className={cn(
        'flex gap-1 border-b border-border overflow-x-auto -mb-px',
        className,
      )}
      data-testid="factor-tabs"
    >
      {sorted.map((f) => {
        const active = activeFactorId === f.id;
        const state = completion?.[f.id];
        const name = pickLocalized(f.name_i18n, i18n.language);
        return (
          <button
            key={f.id}
            type="button"
            role="tab"
            aria-selected={active}
            onClick={() => onSelect(f.id)}
            data-testid={`factor-tab-${f.code}`}
            data-active={active || undefined}
            className={cn(
              'inline-flex items-center gap-1.5 px-3 py-2 text-sm whitespace-nowrap',
              'border-b-2 -mb-px transition-colors',
              active
                ? 'border-primary-500 text-primary-700 font-medium'
                : 'border-transparent text-text-secondary hover:text-text-primary hover:border-border-strong',
            )}
            title={name}
          >
            <span className="font-mono text-xs uppercase">{f.code}</span>
            {state === 'full' ? (
              <Check
                size={12}
                aria-hidden
                className="text-success-600"
                data-testid={`factor-tab-state-${f.code}`}
                data-state="full"
              />
            ) : state === 'partial' ? (
              <Zap
                size={12}
                aria-hidden
                className="text-warning-600"
                data-testid={`factor-tab-state-${f.code}`}
                data-state="partial"
              />
            ) : state === 'empty' ? (
              <Circle
                size={10}
                aria-hidden
                className="text-text-muted"
                data-testid={`factor-tab-state-${f.code}`}
                data-state="empty"
              />
            ) : null}
          </button>
        );
      })}
    </div>
  );
}
