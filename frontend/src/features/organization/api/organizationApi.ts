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
};

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
