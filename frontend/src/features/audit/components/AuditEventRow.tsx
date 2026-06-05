/**
 * Audit event icon renderer (D-1 FE).
 *
 * The action->kind mapping + its type now live in `auditActionIcon.ts` so that
 * this .tsx file only exports React components (react-refresh fast refresh).
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
import type { AuditIconKind } from './auditActionIcon';

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
