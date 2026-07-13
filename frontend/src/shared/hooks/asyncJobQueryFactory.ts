/**
 * Generic TanStack Query scaffolding for "async job" domains — a paginated
 * list, a detail view that polls every 2s while the resource is in an
 * in-flight status, a "request" (create) mutation, and a cancel mutation.
 *
 * Extracted from `features/export/hooks/useExports` and
 * `features/report/hooks/useReports`, which implemented byte-for-byte the
 * same list / detail-polling / request / cancel scaffolding around two
 * different fetcher modules. Each feature keeps its OWN fetchers, wire
 * adapter, query-key builders (`exportKeys` / `reportKeys`), filter shape,
 * and in-flight status set — this factory only wires the shared TanStack
 * Query mechanics around them, so the exact query keys, the 2s polling
 * interval, and the invalidation behavior are preserved unchanged.
 *
 * Download hooks (`useDownloadExport` / `useDownloadReport`) are
 * intentionally NOT part of this factory — their argument shapes diverge
 * (`format` is export-only) — and stay next to each feature's own
 * `useXxx.ts` file.
 */
import { useMutation, useQuery, useQueryClient, type QueryKey } from '@tanstack/react-query';

export interface AsyncJobQueryKeys<TFilters> {
  /** Root key namespace, e.g. `['exports']` — used to invalidate everything in the domain. */
  all: QueryKey;
  /** Builds the list query key from the caller's filter object. */
  list: (filters: TFilters) => QueryKey;
  /** Builds the detail query key for a given id. */
  detail: (id: string) => QueryKey;
}

export interface AsyncJobFetchers<TItem, TFilters, TPage, TRequestPayload> {
  fetchList: (filters: TFilters) => Promise<TPage>;
  fetchDetail: (id: string) => Promise<TItem>;
  request: (payload: TRequestPayload) => Promise<TItem>;
  cancel: (id: string) => Promise<TItem>;
}

export interface AsyncJobQueryFactoryConfig<
  TItem,
  TStatus extends string,
  TFilters extends { projectId?: string },
  TPage,
  TRequestPayload,
> {
  keys: AsyncJobQueryKeys<TFilters>;
  fetchers: AsyncJobFetchers<TItem, TFilters, TPage, TRequestPayload>;
  /** Statuses that should keep the detail view polling every 2s. */
  inFlightStatuses: ReadonlySet<TStatus>;
}

/**
 * Pure decision function behind `useDetail`'s `refetchInterval`: poll every
 * 2s while `pollWhileInFlight` is on AND (no data yet, OR the latest known
 * status is still in-flight); otherwise stop polling. Exported (and kept
 * side-effect free) so it can be unit-tested directly without spinning up a
 * real TanStack Query timer.
 */
export function resolvePollInterval<TStatus extends string>(
  inFlightStatuses: ReadonlySet<TStatus>,
  pollWhileInFlight: boolean | undefined,
  data: { status?: TStatus } | undefined,
): number | false {
  if (!pollWhileInFlight) return false;
  if (!data) return 2000;
  return inFlightStatuses.has(data.status as TStatus) ? 2000 : false;
}

/**
 * Builds the 4 hooks (`useList`, `useDetail`, `useRequest`, `useCancel`) for
 * one async-job domain (exports, reports, ...).
 */
export function createAsyncJobQueries<
  TItem,
  TStatus extends string,
  TFilters extends { projectId?: string },
  TPage,
  TRequestPayload,
>(config: AsyncJobQueryFactoryConfig<TItem, TStatus, TFilters, TPage, TRequestPayload>) {
  const { keys, fetchers, inFlightStatuses } = config;
  // Mirrors the exact fallback key each feature used before extraction, e.g.
  // `['exports', 'detail', null]` — `keys.all` IS `['exports']`, so spreading
  // it reproduces that literal tuple.
  const detailKeyWhenMissing: QueryKey = [...keys.all, 'detail', null];

  function useList(filters: TFilters) {
    return useQuery({
      queryKey: keys.list(filters),
      queryFn: () => fetchers.fetchList(filters),
      enabled: !!filters.projectId,
    });
  }

  function useDetail(id: string | undefined, opts?: { pollWhileInFlight?: boolean }) {
    return useQuery({
      queryKey: id ? keys.detail(id) : detailKeyWhenMissing,
      queryFn: () => fetchers.fetchDetail(id!),
      enabled: !!id,
      refetchInterval: (query) =>
        resolvePollInterval(
          inFlightStatuses,
          opts?.pollWhileInFlight,
          query.state.data as { status?: TStatus } | undefined,
        ),
    });
  }

  function useRequest() {
    const qc = useQueryClient();
    return useMutation({
      mutationFn: (payload: TRequestPayload) => fetchers.request(payload),
      onSuccess: () => qc.invalidateQueries({ queryKey: keys.all }),
    });
  }

  function useCancel(id: string) {
    const qc = useQueryClient();
    return useMutation({
      mutationFn: () => fetchers.cancel(id),
      onSuccess: () => {
        qc.invalidateQueries({ queryKey: keys.all });
        qc.invalidateQueries({ queryKey: keys.detail(id) });
      },
    });
  }

  return { useList, useDetail, useRequest, useCancel };
}
