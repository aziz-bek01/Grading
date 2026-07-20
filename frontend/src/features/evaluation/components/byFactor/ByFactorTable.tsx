import { useTranslation } from 'react-i18next';
import { Card } from '@/shared/components/ui/Card';
import { LoadingState } from '@/shared/components/feedback/LoadingState';
import { EmptyState } from '@/shared/components/feedback/EmptyState';
import { ErrorState } from '@/shared/components/feedback/ErrorState';
import type { Factor, ScoringMode } from '@/features/methodology/types';
import type { EvaluationByFactorRow } from '../../types';
import { PositionScoreRow } from './PositionScoreRow';

interface ByFactorTableProps {
  isError: boolean;
  isLoading: boolean;
  onRetry: () => void;
  rows: EvaluationByFactorRow[];
  activeFactor: Factor;
  activeRow: EvaluationByFactorRow | null;
  bulkSet: Set<string>;
  canEdit: boolean;
  canSeePoints: boolean;
  /** Scoring mode of the version — mode-aware point badge in the drop. */
  scoringMode?: ScoringMode;
  allSelected: boolean;
  onToggleAll: () => void;
  onScoreChange: (row: EvaluationByFactorRow, factorLevelId: string) => Promise<void> | void;
  onCommentChange: (row: EvaluationByFactorRow, comment: string) => Promise<void> | void;
  onRowSelect: (rowId: string) => void;
  onBulkToggle: (rowId: string, on: boolean) => void;
}

/**
 * Full-width K-sheet table (Excel-cell metaphor) — row rendering delegates
 * to {@link PositionScoreRow}. Extracted from `EvaluationByFactorView`
 * (FE-041); unchanged behaviour and testids.
 *
 * FE-9: the table region GROWS (flex-1 / min-h-0) and scrolls internally;
 * the thead sticks to the top of THIS scroll context. The level picker is a
 * centered modal (LevelDropSelect), so the overflow container no longer
 * clips the picker for bottom rows.
 */
export function ByFactorTable({
  isError,
  isLoading,
  onRetry,
  rows,
  activeFactor,
  activeRow,
  bulkSet,
  canEdit,
  canSeePoints,
  scoringMode,
  allSelected,
  onToggleAll,
  onScoreChange,
  onCommentChange,
  onRowSelect,
  onBulkToggle,
}: ByFactorTableProps) {
  const { t } = useTranslation();

  return (
    <div className="w-full flex-1 min-h-0 flex flex-col">
      <Card compact className="overflow-hidden w-full flex-1 min-h-0 flex flex-col">
        {isError ? (
          <ErrorState onRetry={onRetry} />
        ) : isLoading ? (
          <LoadingState />
        ) : rows.length === 0 ? (
          <EmptyState
            title={t('evaluation.byFactor.empty_title')}
            body={t('evaluation.byFactor.empty_body')}
          />
        ) : (
          <div className="overflow-auto flex-1 min-h-0">
            <table
              className="w-full table-fixed text-sm border-collapse"
              data-testid="byfactor-table"
            >
              {/*
                colgroup pins the column widths so `table-fixed` keeps the
                DARAJA column dominant (~42%) regardless of cell content. The
                retired БЎЛИМ / СЕКТОР / ТЎЛДИРИЛГАН columns are folded into
                the merged ЛАВОЗИМ (line 2 sub-text) and the merged ҲОЛАТ
                (stacked progress + status) columns respectively.
              */}
              <colgroup>
                <col className="w-8" />
                <col className="w-[27%]" />
                <col className="w-[42%]" />
                <col className="w-[21%]" />
                <col className="w-[7%]" />
              </colgroup>
              <thead className="bg-divider text-text-secondary text-xs uppercase tracking-wide sticky top-0 z-[1]">
                <tr>
                  <th scope="col" className="px-2 py-3 w-8 text-left align-top">
                    <input
                      type="checkbox"
                      checked={allSelected}
                      onChange={onToggleAll}
                      aria-label={t('evaluation.byFactor.row.select_all_aria')}
                      data-testid="byfactor-select-all"
                      className="h-4 w-4 accent-primary-500"
                    />
                  </th>
                  <th scope="col" className="px-3 py-3 text-left font-medium align-top">
                    {t('evaluation.byFactor.table.column.position')}
                  </th>
                  <th scope="col" className="px-3 py-3 text-left font-medium align-top">
                    {t('evaluation.byFactor.table.column.level')}
                  </th>
                  <th scope="col" className="px-3 py-3 text-left font-medium align-top">
                    {t('evaluation.byFactor.table.column.comment')}
                  </th>
                  <th scope="col" className="px-3 py-3 text-right font-medium align-top hidden lg:table-cell">
                    {t('common.status')}
                  </th>
                </tr>
              </thead>
              <tbody>
                {rows.map((row) => (
                  <PositionScoreRow
                    key={row.evaluation_id}
                    row={row}
                    factor={activeFactor}
                    selected={activeRow?.evaluation_id === row.evaluation_id}
                    bulkSelected={bulkSet.has(row.evaluation_id)}
                    canEdit={canEdit}
                    canSeePoints={canSeePoints}
                    scoringMode={scoringMode}
                    onScoreChange={(lvlId) => onScoreChange(row, lvlId)}
                    onCommentChange={(c) => onCommentChange(row, c)}
                    onRowSelect={() => onRowSelect(row.evaluation_id)}
                    onBulkToggle={(on) => onBulkToggle(row.evaluation_id, on)}
                  />
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  );
}
