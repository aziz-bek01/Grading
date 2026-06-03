/**
 * Audit event row helpers (D-1 FE).
 *
 * Exports the action/icon mapping + small badge renderers so they can be
 * reused by both the audit table (this feature) and the Recent Activity
 * list on the Project Workspace (D-2).
 */
import type { ReactNode } from 'react';
import {
  AlertTriangle,
  Archive,
  CheckCircle2,
  CircleDot,
  FileText,
  LogIn,
  LogOut,
  Lock,
  RefreshCw,
  Settings,
  ShieldAlert,
  Upload,
  UserPlus,
  Wallet,
} from 'lucide-react';

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

export function ActionIcon({ kind, size = 14 }: { kind: AuditIconKind; size?: number }): ReactNode {
  switch (kind) {
    case 'auth':
      return <LogIn size={size} aria-hidden />;
    case 'logout':
      return <LogOut size={size} aria-hidden />;
    case 'security':
      return <ShieldAlert size={size} aria-hidden className="text-danger-700" />;
    case 'approve':
      return <CheckCircle2 size={size} aria-hidden className="text-success-700" />;
    case 'lock':
      return <Lock size={size} aria-hidden />;
    case 'archive':
      return <Archive size={size} aria-hidden />;
    case 'create':
      return <CircleDot size={size} aria-hidden className="text-primary-500" />;
    case 'update':
      return <RefreshCw size={size} aria-hidden />;
    case 'salary':
      return <Wallet size={size} aria-hidden className="text-warning-700" />;
    case 'import':
      return <Upload size={size} aria-hidden />;
    case 'export':
      return <FileText size={size} aria-hidden />;
    case 'report':
      return <FileText size={size} aria-hidden />;
    case 'user':
      return <UserPlus size={size} aria-hidden />;
    case 'project':
      return <Settings size={size} aria-hidden />;
    default:
      return <AlertTriangle size={size} aria-hidden />;
  }
}
