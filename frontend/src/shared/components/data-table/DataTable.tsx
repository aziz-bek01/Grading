import { useMemo, useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { ChevronDown, ChevronUp, ChevronsUpDown, Search } from 'lucide-react';
import { Button } from '@/shared/components/ui/Button';
import { LoadingState } from '@/shared/components/feedback/LoadingState';
import { EmptyState } from '@/shared/components/feedback/EmptyState';
import { cn } from '@/shared/lib/cn';

export interface DataTableColumn<T> {
  key: string;
  header: ReactNode;
  render: (row: T) => ReactNode;
  width?: string;
  className?: string;
  sortable?: boolean;
  sortAccessor?: (row: T) => string | number | null | undefined;
  /** When false, column is hidden by default and visible via Columns toggle. */
  defaultVisible?: boolean;
}

export interface DataTableProps<T> {
  columns: DataTableColumn<T>[];
  rows: T[];
  rowKey: (row: T) => string;
  caption?: string;
  loading?: boolean;
  /** Optional global search predicate. When provided, a search input is rendered. */
  searchPredicate?: (row: T, query: string) => boolean;
  /** Slot for FilterBar / chips above the table. */
  filterBar?: ReactNode;
  /** Slot for header-right actions (e.g. "New project" button). */
  toolbarRight?: ReactNode;
  emptyTitle?: string;
  emptyBody?: string;
  pageSize?: number;
  onRowClick?: (row: T) => void;
  className?: string;
}

type SortDir = 'asc' | 'desc';

export function DataTable<T>({
  columns,
  rows,
  rowKey,
  caption,
  loading,
  searchPredicate,
  filterBar,
  toolbarRight,
  emptyTitle,
  emptyBody,
  pageSize = 20,
  onRowClick,
  className,
}: DataTableProps<T>) {
  const { t } = useTranslation();
  const [query, setQuery] = useState('');
  const [sortKey, setSortKey] = useState<string | null>(null);
  const [sortDir, setSortDir] = useState<SortDir>('asc');
  const [page, setPage] = useState(0);
  // Column visibility map — kept for the column toggle (UI surface to be exposed
  // in the toolbar in a follow-up); for now respects `defaultVisible: false`.
  const [visibleCols] = useState<Record<string, boolean>>(() => {
    const v: Record<string, boolean> = {};
    columns.forEach((c) => (v[c.key] = c.defaultVisible !== false));
    return v;
  });

  const filteredCols = useMemo(() => columns.filter((c) => visibleCols[c.key] !== false), [columns, visibleCols]);

  const filteredRows = useMemo(() => {
    if (!query.trim() || !searchPredicate) return rows;
    const q = query.trim().toLowerCase();
    return rows.filter((r) => searchPredicate(r, q));
  }, [rows, query, searchPredicate]);

  const sortedRows = useMemo(() => {
    if (!sortKey) return filteredRows;
    const col = columns.find((c) => c.key === sortKey);
    if (!col?.sortAccessor) return filteredRows;
    const copy = [...filteredRows];
    copy.sort((a, b) => {
      const va = col.sortAccessor!(a);
      const vb = col.sortAccessor!(b);
      if (va == null && vb == null) return 0;
      if (va == null) return 1;
      if (vb == null) return -1;
      if (va < vb) return sortDir === 'asc' ? -1 : 1;
      if (va > vb) return sortDir === 'asc' ? 1 : -1;
      return 0;
    });
    return copy;
  }, [filteredRows, columns, sortKey, sortDir]);

  const totalPages = Math.max(1, Math.ceil(sortedRows.length / pageSize));
  const safePage = Math.min(page, totalPages - 1);
  const pageRows = sortedRows.slice(safePage * pageSize, safePage * pageSize + pageSize);

  const onHeaderClick = (col: DataTableColumn<T>) => {
    if (!col.sortable) return;
    if (sortKey === col.key) {
      setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'));
    } else {
      setSortKey(col.key);
      setSortDir('asc');
    }
  };

  return (
    <div className={cn('space-y-3', className)}>
      <div className="flex items-center gap-3 flex-wrap">
        {searchPredicate ? (
          <label className="relative flex-1 min-w-[240px] max-w-md">
            <span className="sr-only">{t('common.search')}</span>
            <Search size={14} aria-hidden className="absolute left-2 top-1/2 -translate-y-1/2 text-text-muted" />
            <input
              type="search"
              value={query}
              onChange={(e) => {
                setQuery(e.target.value);
                setPage(0);
              }}
              placeholder={t('dataTable.search_placeholder')}
              className="w-full h-9 pl-7 pr-3 border border-border-strong rounded-md text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500"
            />
          </label>
        ) : null}
        {filterBar}
        <div className="ml-auto flex items-center gap-2">{toolbarRight}</div>
      </div>

      <div className="bg-surface border border-border rounded-lg overflow-hidden">
        <table className="w-full text-sm">
          {caption ? <caption className="sr-only">{caption}</caption> : null}
          <thead className="bg-divider text-text-secondary text-xs uppercase tracking-wide">
            <tr>
              {filteredCols.map((col) => {
                const isSorted = sortKey === col.key;
                return (
                  <th
                    key={col.key}
                    scope="col"
                    aria-sort={isSorted ? (sortDir === 'asc' ? 'ascending' : 'descending') : undefined}
                    style={col.width ? { width: col.width } : undefined}
                    className={cn(
                      'text-left px-4 py-3 font-medium',
                      col.className,
                      col.sortable && 'cursor-pointer select-none hover:text-text-primary',
                    )}
                    onClick={() => onHeaderClick(col)}
                  >
                    <span className="inline-flex items-center gap-1">
                      {col.header}
                      {col.sortable ? (
                        isSorted ? (
                          sortDir === 'asc' ? (
                            <ChevronUp size={12} />
                          ) : (
                            <ChevronDown size={12} />
                          )
                        ) : (
                          <ChevronsUpDown size={12} className="opacity-50" />
                        )
                      ) : null}
                    </span>
                  </th>
                );
              })}
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={filteredCols.length} className="p-0">
                  <LoadingState />
                </td>
              </tr>
            ) : pageRows.length === 0 ? (
              <tr>
                <td colSpan={filteredCols.length} className="p-0">
                  <EmptyState title={emptyTitle} body={emptyBody ?? t('dataTable.no_results')} />
                </td>
              </tr>
            ) : (
              pageRows.map((row) => (
                <tr
                  key={rowKey(row)}
                  className={cn(
                    'border-t border-border hover:bg-divider/40',
                    onRowClick && 'cursor-pointer',
                  )}
                  onClick={onRowClick ? () => onRowClick(row) : undefined}
                >
                  {filteredCols.map((col) => (
                    <td key={col.key} className={cn('px-4 py-3 text-text-primary', col.className)}>
                      {col.render(row)}
                    </td>
                  ))}
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <div className="flex items-center justify-between text-xs text-text-secondary">
        <span>
          {/* FE-5: current-page RANGE (from–to / total), not a start index.
              The old `safePage*pageSize+1` value was a START index shown as a
              COUNT, contradicting "Саҳифа 1 / 1". */}
          {t('dataTable.showing_range', {
            from: sortedRows.length === 0 ? 0 : safePage * pageSize + 1,
            to: Math.min(sortedRows.length, (safePage + 1) * pageSize),
            total: sortedRows.length,
          })}
        </span>
        <div className="flex items-center gap-2">
          <Button
            variant="ghost"
            size="sm"
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            disabled={safePage === 0}
          >
            {t('dataTable.previous')}
          </Button>
          <span aria-live="polite">
            {t('dataTable.page_of', { page: safePage + 1, total: totalPages })}
          </span>
          <Button
            variant="ghost"
            size="sm"
            onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
            disabled={safePage >= totalPages - 1}
          >
            {t('dataTable.next')}
          </Button>
        </div>
      </div>
    </div>
  );
}
