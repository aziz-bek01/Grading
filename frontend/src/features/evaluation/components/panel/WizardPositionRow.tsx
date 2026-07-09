import { Link } from 'react-router-dom';
import { ExternalLink } from 'lucide-react';
import { cn } from '@/shared/lib/cn';
import { pickLocalized } from '@/shared/lib/localized';
import type { Position } from '@/features/positions/types/positionTypes';

interface WizardPositionRowProps {
  position: Position;
  checked: boolean;
  disabled?: boolean;
  onToggle: (on: boolean) => void;
  locale: string;
  paneledLabel: string | null;
  /** T3 — deep-link to the existing panel (already-paneled rows only). */
  openPanelHref?: string | null;
  openPanelLabel?: string;
}

/**
 * One position row — reuses the AddPositionsDialog checkbox-row visuals.
 * Extracted from OpenPanelDialog (FE-041); used by both the fully-paneled
 * and normal Step-2 candidate lists.
 */
export function WizardPositionRow({
  position,
  checked,
  disabled,
  onToggle,
  locale,
  paneledLabel,
  openPanelHref,
  openPanelLabel,
}: WizardPositionRowProps) {
  return (
    <div
      data-testid={`wizard-position-row-${position.code}`}
      className={cn(
        'flex items-start gap-2.5 px-3 py-2.5 text-sm transition-colors',
        disabled
          ? 'opacity-60'
          : checked
            ? 'bg-primary-50/40'
            : 'hover:bg-divider/40',
      )}
    >
      <label
        className={cn(
          'flex items-start gap-2.5 min-w-0 flex-1',
          disabled ? 'cursor-not-allowed' : 'cursor-pointer',
        )}
      >
        <input
          type="checkbox"
          checked={checked}
          disabled={disabled}
          onChange={(e) => onToggle(e.target.checked)}
          data-testid={`wizard-position-check-${position.code}`}
          className="mt-0.5 h-4 w-4 accent-primary-500"
        />
        <span className="min-w-0 flex-1 text-left">
          <span className="block font-medium text-text-primary">
            {pickLocalized(position.title_i18n, locale)}
          </span>
          <span className="block text-xs text-text-muted">
            <span className="font-mono">{position.code}</span>
            {paneledLabel ? (
              <span
                className="ml-2 rounded-full bg-divider px-1.5 py-0.5 text-text-secondary"
                data-testid={`wizard-position-paneled-${position.code}`}
              >
                {paneledLabel}
              </span>
            ) : null}
          </span>
        </span>
      </label>
      {openPanelHref ? (
        <Link
          to={openPanelHref}
          data-testid={`wizard-open-existing-${position.code}`}
          className="shrink-0 inline-flex items-center gap-1 text-xs text-primary-600 hover:underline whitespace-nowrap mt-0.5"
        >
          <ExternalLink size={12} aria-hidden />
          {openPanelLabel}
        </Link>
      ) : null}
    </div>
  );
}
