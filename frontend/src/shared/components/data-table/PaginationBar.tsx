import { useTranslation } from 'react-i18next';
import { Button } from '@/shared/components/ui/Button';

interface PaginationBarProps {
  page: number;
  totalPages: number;
  total: number;
  pageSize: number;
  onPageChange: (page: number) => void;
}

/** Server-driven pagination bar — used when the API returns PageEnvelope. */
export function PaginationBar({ page, totalPages, total, pageSize, onPageChange }: PaginationBarProps) {
  const { t } = useTranslation();
  const shown = total === 0 ? 0 : page * pageSize + 1;
  return (
    <div className="flex items-center justify-between text-xs text-text-secondary">
      <span>{t('dataTable.showing_x_of_y', { shown, total })}</span>
      <div className="flex items-center gap-2">
        <Button variant="ghost" size="sm" onClick={() => onPageChange(Math.max(0, page - 1))} disabled={page === 0}>
          {t('dataTable.previous')}
        </Button>
        <span aria-live="polite">{t('dataTable.page_of', { page: page + 1, total: Math.max(1, totalPages) })}</span>
        <Button
          variant="ghost"
          size="sm"
          onClick={() => onPageChange(Math.min(totalPages - 1, page + 1))}
          disabled={page >= totalPages - 1}
        >
          {t('dataTable.next')}
        </Button>
      </div>
    </div>
  );
}
