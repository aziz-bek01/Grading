import { useTranslation } from 'react-i18next';
import { pickLocalized } from '@/shared/lib/localized';
import { ScoringModeBadge } from '@/features/methodology/components/ScoringModeBadge';
import type { Methodology, ScoringMode, Factor } from '@/features/methodology/types';
import { PanelRoleChip } from '../panel/PanelRoleChip';
import { PanelBlindBanner } from '../panel/PanelBlindBanner';
import type { EvaluatorRole } from '../../panelTypes';
import { FactorTabs, type FactorCompletionMap } from './FactorTabs';

interface ByFactorHeaderProps {
  selectableMethodologies: Methodology[];
  activeMethodology: Methodology;
  methodologyName: string;
  scoringMode: ScoringMode;
  onMethodologyChange: (methodologyId: string) => void;
  selfRole: EvaluatorRole | null;
  blind: boolean;
  factors: Factor[];
  activeFactorId: string;
  completionMap: FactorCompletionMap;
  onFactorChange: (factorId: string) => void;
}

/**
 * FE-7: active-methodology header strip — name + v{n} + scoring-mode badge
 * (or a selector when the project has >1 methodology with an active
 * version), the evaluator's own seat-role chip, the blind banner, and the
 * factor tabs. Extracted from `EvaluationByFactorView` (FE-041); unchanged
 * behaviour and testids.
 */
export function ByFactorHeader({
  selectableMethodologies,
  activeMethodology,
  methodologyName,
  scoringMode,
  onMethodologyChange,
  selfRole,
  blind,
  factors,
  activeFactorId,
  completionMap,
  onFactorChange,
}: ByFactorHeaderProps) {
  const { t, i18n } = useTranslation();

  return (
    <div className="pt-1" data-testid="byfactor-methodology-header">
      <div className="flex flex-wrap items-center gap-2 pb-1.5">
        <span className="text-xs uppercase tracking-wide text-text-muted">
          {t('evaluation.byFactor.active_methodology')}
        </span>
        {selectableMethodologies.length > 1 ? (
          <select
            aria-label={t('evaluation.byFactor.selector.aria')}
            value={activeMethodology.id}
            onChange={(e) => onMethodologyChange(e.target.value)}
            data-testid="byfactor-methodology-select"
            className="h-8 px-2 border border-border-strong rounded-md text-sm font-medium bg-surface text-text-primary max-w-[16rem]"
          >
            {selectableMethodologies.map((m) => (
              <option key={m.id} value={m.id}>
                {t('evaluation.byFactor.selector.option', {
                  name: pickLocalized(m.name_i18n, i18n.language) || m.code,
                  version: m.active_version_number ?? '?',
                })}
              </option>
            ))}
          </select>
        ) : (
          <>
            <span className="text-sm font-medium text-text-primary">
              {methodologyName}
            </span>
            {activeMethodology.active_version_number != null ? (
              <span className="text-sm text-text-secondary tabular-nums">
                v{activeMethodology.active_version_number}
              </span>
            ) : null}
          </>
        )}
        {/* Reuse the shared ScoringModeBadge; keep the legacy testid on a
            wrapper so existing header assertions stay green. */}
        <span data-testid="byfactor-scoring-mode-badge">
          <ScoringModeBadge mode={scoringMode} />
        </span>
        {/* FE-3: the evaluator's own seat role chip (panel mode only). */}
        {selfRole ? (
          <span data-testid="byfactor-self-role">
            <PanelRoleChip role={selfRole} self />
          </span>
        ) : null}
      </div>
      {/* FE-3: blind banner while the panel collects — UX only; the BE read
          guard is the real isolation control. Reuses the ScorePreviewBanner
          visual language via PanelBlindBanner. */}
      {blind ? <PanelBlindBanner className="mb-1.5" /> : null}
      <FactorTabs
        // Sticky offset + z-index come from the SHARED constant so the tabs
        // and the header never diverge. top-20 (80px) clears the 62px TopBar.
        factors={factors}
        activeFactorId={activeFactorId}
        completion={completionMap}
        onSelect={onFactorChange}
      />
    </div>
  );
}
