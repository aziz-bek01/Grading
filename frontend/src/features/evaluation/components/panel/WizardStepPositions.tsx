import { Search } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { pickLocalized } from '@/shared/lib/localized';
import type { Methodology } from '@/features/methodology/types';
import type { Position } from '@/features/positions/types/positionTypes';
import { WizardPositionRow } from './WizardPositionRow';

interface WizardStepPositionsProps {
  departmentName: string;
  versionId: string;
  onVersionChange: (id: string) => void;
  activeMethodologies: Methodology[];
  posSearch: string;
  onPosSearchChange: (value: string) => void;
  allSelected: boolean;
  onToggleAll: () => void;
  selectablePositionsCount: number;
  includeSubtree: boolean;
  onToggleSubtree: (on: boolean) => void;
  positionsLoading: boolean;
  candidates: Position[];
  fullyPaneled: boolean;
  isPaneled: (p: Position) => boolean;
  panelIdFor: (p: Position) => string | undefined;
  selectedPositions: Set<string>;
  onTogglePosition: (id: string, on: boolean) => void;
  panelDetailHref: (panelId: string) => string;
}

/**
 * Step 2 — POSITIONS: server-filtered candidate list (multi-select,
 * select-all, search); already-paneled rows DISABLED + marked.
 */
export function WizardStepPositions({
  departmentName,
  versionId,
  onVersionChange,
  activeMethodologies,
  posSearch,
  onPosSearchChange,
  allSelected,
  onToggleAll,
  selectablePositionsCount,
  includeSubtree,
  onToggleSubtree,
  positionsLoading,
  candidates,
  fullyPaneled,
  isPaneled,
  panelIdFor,
  selectedPositions,
  onTogglePosition,
  panelDetailHref,
}: WizardStepPositionsProps) {
  const { t, i18n } = useTranslation();

  return (
    <div className="flex flex-col flex-1 min-h-0 space-y-3" data-testid="wizard-step-2">
      <div className="shrink-0">
        <p className="text-sm text-text-secondary">
          {t('panel.wizard.step_2_body', { department: departmentName })}
        </p>
      </div>
      <div className="shrink-0">
        <label htmlFor="open-panel-version" className="block text-sm font-medium mb-1">
          {t('panel.dialog.methodology')}
        </label>
        <select
          id="open-panel-version"
          value={versionId}
          onChange={(e) => onVersionChange(e.target.value)}
          data-testid="open-panel-version-select"
          className="w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface"
        >
          <option value="">—</option>
          {activeMethodologies.map((m) => (
            <option key={m.id} value={m.active_version_id!}>
              {pickLocalized(m.name_i18n, i18n.language)} v{m.active_version_number}
            </option>
          ))}
        </select>
      </div>

      <div className="shrink-0 flex items-center gap-2">
        <label className="relative flex-1">
          <span className="sr-only">{t('common.search')}</span>
          <Search
            size={14}
            aria-hidden
            className="absolute left-2 top-1/2 -translate-y-1/2 text-text-muted"
          />
          <input
            type="search"
            value={posSearch}
            onChange={(e) => onPosSearchChange(e.target.value)}
            placeholder={t('common.search')}
            data-testid="wizard-position-search"
            className="w-full h-9 pl-7 pr-3 border border-border-strong rounded-md text-sm bg-surface"
          />
        </label>
        <label className="inline-flex items-center gap-1.5 text-sm text-text-secondary whitespace-nowrap">
          <input
            type="checkbox"
            checked={allSelected}
            onChange={onToggleAll}
            disabled={selectablePositionsCount === 0}
            data-testid="wizard-select-all"
            className="h-4 w-4 accent-primary-500"
          />
          {t('evaluation.add_positions.select_all')}
        </label>
      </div>

      {/* T4 — subtree toggle: list the unit's descendants too (server
          expands via includeSubtree=true) so a parent department is not
          limited to its direct positions. */}
      <label className="shrink-0 inline-flex items-center gap-1.5 text-sm text-text-secondary">
        <input
          type="checkbox"
          checked={includeSubtree}
          onChange={(e) => onToggleSubtree(e.target.checked)}
          data-testid="wizard-include-subtree"
          className="h-4 w-4 accent-primary-500"
        />
        {t('panel.wizard.positions.include_subtree')}
      </label>

      <div
        className="flex-1 min-h-0 overflow-y-auto border border-border rounded-md divide-y divide-border"
        data-testid="wizard-position-list"
      >
        {positionsLoading ? (
          <p className="p-4 text-sm text-text-muted text-center">
            {t('common.loading')}
          </p>
        ) : candidates.length === 0 ? (
          <p
            className="p-4 text-sm text-text-muted text-center"
            data-testid="wizard-positions-empty"
          >
            {t('panel.wizard.positions.empty')}
          </p>
        ) : fullyPaneled ? (
          <div
            className="p-4 text-sm text-text-muted text-center space-y-2"
            data-testid="wizard-positions-fully-paneled"
          >
            {/* T3 — un-dead-end: keep the disabled rows for context but
                give each already-paneled position an "open existing panel"
                CTA that deep-links to its panel detail. panel_id resolved
                from the already-loaded panelsQuery (no new fetch). */}
            <p>{t('panel.wizard.positions.fully_paneled')}</p>
            {candidates.map((p) => {
              const existingPanelId = panelIdFor(p);
              return (
                <WizardPositionRow
                  key={p.id}
                  position={p}
                  checked={false}
                  disabled
                  onToggle={() => undefined}
                  locale={i18n.language}
                  paneledLabel={t('panel.wizard.positions.already_paneled')}
                  openPanelHref={
                    existingPanelId ? panelDetailHref(existingPanelId) : null
                  }
                  openPanelLabel={t('panel.wizard.positions.open_existing')}
                />
              );
            })}
          </div>
        ) : (
          candidates.map((p) => {
            const paneled = isPaneled(p);
            const existingPanelId = panelIdFor(p);
            return (
              <WizardPositionRow
                key={p.id}
                position={p}
                checked={selectedPositions.has(p.id)}
                disabled={paneled}
                onToggle={(on) => onTogglePosition(p.id, on)}
                locale={i18n.language}
                paneledLabel={
                  paneled ? t('panel.wizard.positions.already_paneled') : null
                }
                openPanelHref={
                  paneled && existingPanelId
                    ? panelDetailHref(existingPanelId)
                    : null
                }
                openPanelLabel={t('panel.wizard.positions.open_existing')}
              />
            );
          })
        )}
      </div>
    </div>
  );
}
