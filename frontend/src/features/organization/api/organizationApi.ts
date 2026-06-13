import { httpClient } from '@/shared/api/httpClient';
import { endpoints } from '@/shared/api/endpoints';
import type {
  Department,
  DepartmentCreatePayload,
  DepartmentUpdatePayload,
} from '../types/organizationTypes';

export const orgKeys = {
  all: ['organization'] as const,
  tree: (projectId: string) => ['organization', 'tree', projectId] as const,
  positionCounts: (projectId: string) =>
    ['organization', 'position-counts', projectId] as const,
};

/**
 * T4 (Defect 1) — normalized per-department position counts.
 *
 * `directCount`  — ACTIVE positions directly in the department.
 * `subtreeCount` — self + all descendants (a parent rolls up its children, and
 *                  the figure is never truncated by a position page cap).
 */
export interface DepartmentPositionCount {
  directCount: number;
  subtreeCount: number;
}

/** GET /departments/position-counts wire row (snake_case — global Jackson strategy). */
interface DepartmentPositionCountWire {
  department_id: string;
  direct_count: number;
  subtree_count: number;
}

/**
 * Server-authoritative per-department position counts (T4). Replaces the former
 * FE 200-row count scan: the BE rolls up the subtree (parents are no longer 0)
 * and is never page-capped. The snake_case wire is normalized at THIS boundary
 * (like {@link flattenDepartmentTree}) so consumers read a typed camelCase map.
 */
export async function fetchDepartmentPositionCounts(
  projectId: string,
): Promise<Map<string, DepartmentPositionCount>> {
  const res = await httpClient.get<DepartmentPositionCountWire[]>(
    endpoints.departments.positionCounts,
    { params: { projectId } },
  );
  const map = new Map<string, DepartmentPositionCount>();
  for (const row of res.data ?? []) {
    map.set(row.department_id, {
      directCount: row.direct_count ?? 0,
      subtreeCount: row.subtree_count ?? 0,
    });
  }
  return map;
}

interface DepartmentListResponse {
  items: Department[];
}

/** Real backend `GET /departments/tree` node: department + nested children. */
interface DepartmentTreeApiNode {
  department: Department;
  children?: DepartmentTreeApiNode[];
}

/**
 * Flatten the backend's nested `[{ department, children }]` tree into the flat
 * `Department[]` the UI pipeline expects (each row carries `parent_id`, so
 * {@link buildDepartmentTree} rebuilds the hierarchy downstream).
 */
function flattenDepartmentTree(nodes: DepartmentTreeApiNode[]): Department[] {
  const out: Department[] = [];
  const walk = (list: DepartmentTreeApiNode[]) => {
    for (const n of list) {
      if (n?.department) {
        out.push(n.department);
        if (n.children?.length) walk(n.children);
      }
    }
  };
  walk(nodes);
  return out;
}

/**
 * Tolerant of three response shapes:
 *  - real backend: bare array of `{ department, children }` (nested) → flattened;
 *  - a bare flat `Department[]`; and
 *  - legacy MSW `{ items: Department[] }`.
 * The downstream {@link buildDepartmentTree} only needs a flat list keyed by
 * `parent_id`, so every shape collapses to `Department[]`.
 */
export async function fetchDepartmentTree(projectId: string): Promise<Department[]> {
  const res = await httpClient.get<DepartmentTreeApiNode[] | DepartmentListResponse | Department[]>(
    endpoints.departments.tree,
    { params: { projectId } },
  );
  const data = res.data as unknown;
  if (Array.isArray(data)) {
    if (data.length === 0) return [];
    const first = data[0] as Record<string, unknown>;
    if (first && typeof first === 'object' && 'department' in first) {
      return flattenDepartmentTree(data as DepartmentTreeApiNode[]);
    }
    return data as Department[];
  }
  return (data as DepartmentListResponse)?.items ?? [];
}

export async function createDepartment(payload: DepartmentCreatePayload): Promise<Department> {
  const res = await httpClient.post<Department>(endpoints.departments.list, payload);
  return res.data;
}

export async function updateDepartment(id: string, payload: DepartmentUpdatePayload): Promise<Department> {
  const res = await httpClient.patch<Department>(endpoints.departments.detail(id), payload);
  return res.data;
}

export async function archiveDepartment(id: string): Promise<Department> {
  const res = await httpClient.post<Department>(endpoints.departments.archive(id), {});
  return res.data;
}
