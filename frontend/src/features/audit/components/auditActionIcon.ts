/**
 * Audit action -> icon-kind mapping (D-1 FE).
 *
 * Split out of AuditEventRow.tsx so that the .tsx file only exports React
 * components (react-refresh/only-export-components). The mapping function and
 * its type are shared by the audit table, the audit details drawer and the
 * Recent Activity list on the Project Workspace.
 */
export type AuditIconKind =
  | 'auth'
  | 'security'
  | 'project'
  | 'approve'
  | 'lock'
  | 'archive'
  | 'create'
  | 'update'
  | 'salary'
  | 'import'
  | 'export'
  | 'report'
  | 'user'
  | 'logout';

/** Map a raw action code to a stable icon kind. */
export function actionIconKind(action: string): AuditIconKind {
  if (action.startsWith('LOGIN_')) return 'auth';
  if (action === 'LOGOUT') return 'logout';
  if (action.startsWith('CROSS_TENANT') || action === 'TENANT_MEMBERSHIP_MISMATCH') return 'security';
  if (action.startsWith('USER_SALARY_PERMISSION')) return 'salary';
  if (action.startsWith('USER_')) return 'user';
  if (action.endsWith('_APPROVED') || action.endsWith('_SUBMITTED')) return 'approve';
  if (action.endsWith('_LOCKED')) return 'lock';
  if (action.endsWith('_ARCHIVED')) return 'archive';
  if (action.endsWith('_CREATED')) return 'create';
  if (action.endsWith('_UPDATED') || action.endsWith('_REORDERED') || action.endsWith('_UPSERTED'))
    return 'update';
  if (action.startsWith('IMPORT_')) return 'import';
  if (action.startsWith('EXPORT_')) return 'export';
  if (action.startsWith('REPORT_')) return 'report';
  if (action.startsWith('PROJECT_') || action.startsWith('TENANT_')) return 'project';
  return 'update';
}
