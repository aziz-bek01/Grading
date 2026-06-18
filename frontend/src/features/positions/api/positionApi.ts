import { httpClient } from '@/shared/api/httpClient';
import { endpoints } from '@/shared/api/endpoints';
import type {
  Position,
  PositionCreatePayload,
  PositionList,
  PositionListParams,
  PositionUpdatePayload,
} from '../types/positionTypes';

export const positionKeys = {
  all: ['positions'] as const,
  list: (params: PositionListParams) => ['positions', 'list', params] as const,
  detail: (id: string) => ['positions', 'detail', id] as const,
};

export async function fetchPositions(params: PositionListParams): Promise<PositionList> {
  const res = await httpClient.get<PositionList>(endpoints.positions.list, {
    params: {
      projectId: params.projectId,
      departmentId: params.departmentId ?? undefined,
      // T4 — only send the flag when expanding a department's subtree; omit
      // otherwise so the default (direct positions) is unchanged.
      includeSubtree: params.includeSubtree ? true : undefined,
      status: params.status ?? undefined,
      jobFamily: params.jobFamily ?? undefined,
      page: params.page ?? 0,
      size: params.size ?? 20,
    },
  });
  return res.data;
}

export async function fetchPosition(id: string): Promise<Position> {
  const res = await httpClient.get<Position>(endpoints.positions.detail(id));
  return res.data;
}

export async function createPosition(payload: PositionCreatePayload): Promise<Position> {
  const res = await httpClient.post<Position>(endpoints.positions.list, payload);
  return res.data;
}

export async function updatePosition(id: string, payload: PositionUpdatePayload): Promise<Position> {
  const res = await httpClient.patch<Position>(endpoints.positions.detail(id), payload);
  return res.data;
}

export async function archivePosition(id: string): Promise<void> {
  // Backend returns 204 No Content — do not read res.data (it would be empty).
  await httpClient.post(endpoints.positions.archive(id), {});
}
