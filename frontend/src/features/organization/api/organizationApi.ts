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

export async function fetchDepartmentTree(projectId: string): Promise<Department[]> {
  const res = await httpClient.get<DepartmentListResponse>(endpoints.departments.tree, {
    params: { projectId },
  });
  return res.data.items;
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
