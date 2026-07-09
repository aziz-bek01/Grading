import { useTranslation } from 'react-i18next';
import { Loader2 } from 'lucide-react';
import { Card } from '@/shared/components/ui/Card';
import { Button } from '@/shared/components/ui/Button';
import { PaginationBar } from '@/shared/components/data-table/PaginationBar';
import { PermissionGate } from '@/shared/components/access/PermissionGate';
import { PERMISSIONS } from '@/shared/types/permissions';
import { cn } from '@/shared/lib/cn';
import { BY_FACTOR_PAGE_SIZE } from './useByFactorViewState';

interface ByFactorToolbarProps {
  selectedCount: number;
  totalElements: number;
  saving: boolean;
  onBulkScoreOpen: () => void;
  onBulkSubmitOpen: () => void;
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
}

/**
 * Bottom sticky toolbar: selected-count + autosave indicator + bulk-action
 * buttons + pagination. Extracted from `EvaluationByFactorView` (FE-041);
 * unchanged behaviour and testids.
 *
 * Sticky to the viewport bottom so bulk actions + pagination stay visible
 * while scrolling through a page of rows. z-10 keeps it below the global
 * TopBar (z-20) but above the table content; the border-t provides visual
 * separation from the table.
 */
export function ByFactorToolbar({
  selectedCount,
  totalElements,
  saving,
  onBulkScoreOpen,
  onBulkSubmitOpen,
  page,
  totalPages,
  onPageChange,
}: ByFactorToolbarProps) {
  const { t } = useTranslation();

  return (
    <div
      className="sticky bottom-0 z-10 bg-background border-t border-border"
      data-testid="byfactor-sticky-toolbar"
    >
      <Card compact>
        <div className="flex flex-wrap items-center gap-3 justify-between">
          <div className="flex items-center gap-3 text-sm text-text-secondary">
            {t('evaluation.byFactor.toolbar.selected', {
              selected: selectedCount,
              total: totalElements,
            })}
            {/* Global save-state indicator: "Saving…" while fetching/mutating,
                "All changes saved" otherwise. */}
            <span
              className={cn(
                'text-xs tabular-nums transition-opacity',
                saving
                  ? 'text-text-muted opacity-100'
                  : 'text-success-600 opacity-80',
              )}
              data-testid="byfactor-save-state"
              aria-live="polite"
            >
              {saving ? (
                <span className="inline-flex items-center gap-1">
                  <Loader2 size={12} className="animate-spin" aria-hidden />
                  {t('evaluation.byFactor.autosave.saving')}
                </span>
              ) : (
                t('evaluation.byFactor.autosave.saved')
              )}
            </span>
          </div>
          <div className="flex items-center gap-2">
            <PermissionGate permission={PERMISSIONS.EVALUATION_EDIT}>
              <Button
                variant="secondary"
                onClick={onBulkScoreOpen}
                disabled={selectedCount === 0}
                data-testid="bulk-score-open"
              >
                {t('evaluation.byFactor.bulk.set_all.cta', {
                  count: selectedCount,
                })}
              </Button>
            </PermissionGate>
            <PermissionGate permission={PERMISSIONS.EVALUATION_EDIT}>
              <Button
                onClick={onBulkSubmitOpen}
                disabled={selectedCount === 0}
                data-testid="bulk-submit-open"
              >
                {t('evaluation.byFactor.bulk.submit.cta', {
                  count: selectedCount,
                })}
              </Button>
            </PermissionGate>
          </div>
        </div>
        <PaginationBar
          page={page}
          totalPages={totalPages}
          total={totalElements}
          pageSize={BY_FACTOR_PAGE_SIZE}
          onPageChange={onPageChange}
        />
      </Card>
    </div>
  );
}
