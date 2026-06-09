/**
 * Domain types for the Access-scope slice (E4-S1).
 *
 * Mirrors the backend scope contract (snake_case JSON):
 *   - GET  /users/{id}/scopes?tenantId=...        → {@link UserScopes}
 *   - PUT  /users/{id}/department-scopes          → {@link SetDepartmentScopesPayload}
 *   - PUT  /users/{id}/project-assignments        → {@link SetProjectAssignmentsPayload}
 *
 * Tenant rule: unlike ordinary business bodies (where the backend derives the
 * tenant from the JWT), the scope endpoints are *cross-tenant admin* surfaces —
 * the caller explicitly names which tenant membership they are editing. So
 * `tenant_id` IS a first-class field here, both as a `?tenantId` query param on
 * the GET and inside the PUT bodies. The backend validates that every supplied
 * department / project belongs to that tenant and that the target user is a
 * member of it.
 *
 * Both PUT endpoints are REPLACE-set semantics: the body carries the FULL
 * desired set of ids; anything previously assigned but omitted is deactivated.
 */
export interface UserScopes {
  user_id: string;
  tenant_id: string;
  /** ACTIVE department-scope rows. Selecting a parent implies its whole subtree. */
  department_ids: string[];
  /** ACTIVE project-assignment rows. */
  project_ids: string[];
}

export interface SetDepartmentScopesPayload {
  tenant_id: string;
  /** FULL replace-set of department ids. */
  department_ids: string[];
}

export interface SetProjectAssignmentsPayload {
  tenant_id: string;
  /** FULL replace-set of project ids. */
  project_ids: string[];
}
