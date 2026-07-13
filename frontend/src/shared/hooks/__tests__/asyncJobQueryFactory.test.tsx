import { describe, expect, it, vi } from 'vitest';
import type { ReactNode } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react';
import { createAsyncJobQueries, resolvePollInterval } from '../asyncJobQueryFactory';

type DemoStatus = 'REQUESTED' | 'GENERATING' | 'GENERATED';

interface DemoItem {
  id: string;
  status: DemoStatus;
}

interface DemoFilters {
  projectId?: string;
  status?: DemoStatus;
}

interface DemoPage {
  items: DemoItem[];
}

interface DemoRequestPayload {
  projectId: string;
}

const IN_FLIGHT = new Set<DemoStatus>(['REQUESTED', 'GENERATING']);

const demoKeys = {
  all: ['demo'] as const,
  list: (filters: DemoFilters) => ['demo', 'list', filters.projectId ?? null, filters.status ?? null],
  detail: (id: string) => ['demo', 'detail', id],
};

function makeQueries(overrides?: {
  fetchList?: (filters: DemoFilters) => Promise<DemoPage>;
  fetchDetail?: (id: string) => Promise<DemoItem>;
  request?: (payload: DemoRequestPayload) => Promise<DemoItem>;
  cancel?: (id: string) => Promise<DemoItem>;
}) {
  const fetchList = overrides?.fetchList ?? vi.fn().mockResolvedValue({ items: [] });
  const fetchDetail =
    overrides?.fetchDetail ?? vi.fn().mockResolvedValue({ id: 'x', status: 'GENERATED' });
  const request = overrides?.request ?? vi.fn().mockResolvedValue({ id: 'x', status: 'REQUESTED' });
  const cancel = overrides?.cancel ?? vi.fn().mockResolvedValue({ id: 'x', status: 'GENERATED' });

  const queries = createAsyncJobQueries<DemoItem, DemoStatus, DemoFilters, DemoPage, DemoRequestPayload>({
    keys: demoKeys,
    fetchers: { fetchList, fetchDetail, request, cancel },
    inFlightStatuses: IN_FLIGHT,
  });
  return { queries, fetchList, fetchDetail, request, cancel };
}

function makeWrapper() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
  });
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  );
  return { client, wrapper };
}

describe('resolvePollInterval (pure decision fn behind useDetail refetchInterval)', () => {
  it('never polls when pollWhileInFlight is off', () => {
    expect(resolvePollInterval(IN_FLIGHT, false, { status: 'GENERATING' })).toBe(false);
    expect(resolvePollInterval(IN_FLIGHT, undefined, { status: 'GENERATING' })).toBe(false);
  });

  it('polls every 2s while no data has arrived yet', () => {
    expect(resolvePollInterval(IN_FLIGHT, true, undefined)).toBe(2000);
  });

  it('polls every 2s while the status is in the in-flight set', () => {
    expect(resolvePollInterval(IN_FLIGHT, true, { status: 'REQUESTED' })).toBe(2000);
    expect(resolvePollInterval(IN_FLIGHT, true, { status: 'GENERATING' })).toBe(2000);
  });

  it('stops polling once the status leaves the in-flight set', () => {
    expect(resolvePollInterval(IN_FLIGHT, true, { status: 'GENERATED' })).toBe(false);
  });
});

describe('createAsyncJobQueries', () => {
  it('useList: disabled without a projectId (fetchList never called)', () => {
    const { queries, fetchList } = makeQueries();
    const { wrapper } = makeWrapper();
    const { result } = renderHook(() => queries.useList({}), { wrapper });
    expect(result.current.fetchStatus).toBe('idle');
    expect(fetchList).not.toHaveBeenCalled();
  });

  it('useList: calls fetchList with the filters and resolves using the exact list key', async () => {
    const { queries, fetchList } = makeQueries();
    const { client, wrapper } = makeWrapper();
    const filters: DemoFilters = { projectId: 'p1', status: 'GENERATED' };
    const { result } = renderHook(() => queries.useList(filters), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(fetchList).toHaveBeenCalledWith(filters);
    expect(client.getQueryData(demoKeys.list(filters))).toEqual({ items: [] });
  });

  it('useDetail: falls back to `[...keys.all, "detail", null]` when id is undefined', () => {
    const { queries, fetchDetail } = makeQueries();
    const { client, wrapper } = makeWrapper();
    renderHook(() => queries.useDetail(undefined), { wrapper });
    expect(fetchDetail).not.toHaveBeenCalled();
    // The fallback key must be reachable via the documented shape so a
    // caller/tests can pre-seed or assert against it.
    expect(client.getQueryState(['demo', 'detail', null])).toBeDefined();
  });

  it('useDetail: fetches by id under the exact detail key', async () => {
    const { queries, fetchDetail } = makeQueries({
      fetchDetail: vi.fn().mockResolvedValue({ id: 'exp-1', status: 'GENERATED' }),
    });
    const { client, wrapper } = makeWrapper();
    const { result } = renderHook(() => queries.useDetail('exp-1'), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(fetchDetail).toHaveBeenCalledWith('exp-1');
    expect(client.getQueryData(demoKeys.detail('exp-1'))).toEqual({ id: 'exp-1', status: 'GENERATED' });
  });

  it('useRequest: invalidates the whole domain (keys.all) on success', async () => {
    const { queries, request } = makeQueries();
    const { client, wrapper } = makeWrapper();
    const invalidateSpy = vi.spyOn(client, 'invalidateQueries');
    const { result } = renderHook(() => queries.useRequest(), { wrapper });

    result.current.mutate({ projectId: 'p1' });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(request).toHaveBeenCalledWith({ projectId: 'p1' });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: demoKeys.all });
  });

  it('useCancel: invalidates BOTH keys.all and keys.detail(id) on success', async () => {
    const { queries, cancel } = makeQueries();
    const { client, wrapper } = makeWrapper();
    const invalidateSpy = vi.spyOn(client, 'invalidateQueries');
    const { result } = renderHook(() => queries.useCancel('exp-9'), { wrapper });

    result.current.mutate();
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(cancel).toHaveBeenCalledWith('exp-9');
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: demoKeys.all });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: demoKeys.detail('exp-9') });
  });
});
