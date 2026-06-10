/**
 * DeleteRoleConfirm — confirm + execute deletion of a CUSTOM role (slice E3).
 *
 * Wraps the shared <ConfirmDialog /> (destructive variant). On a 409
 * ROLE_IN_USE the role is still assigned to users — the backend refuses the
 * delete; we keep the dialog open and show a clear localized message telling the
 * admin to revoke the assignments first. Other errors map through the shared
 * `mapRolePermissionError`. The dialog never deletes a system role (the list
 * only offers delete on custom rows, and the backend re-enforces with 403).
 */
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { AlertTriangle } from 'lucide-react';
import { Button } from '@/shared/components/ui/Button';
import { useDeleteRole } from '../hooks/useRolePermissions';
import { mapRolePermissionError } from '../lib/rolePermissionError';
import type { AssignableRole } from '@/features/users-access/api/rolesApi';

interface DeleteRoleConfirmProps {
  open: boolean;
  role: AssignableRole | null;
  onClose: () => void;
  /** Called after a successful delete (parent refetches / toasts). */
  onDeleted: () => void;
}

export function DeleteRoleConfirm({ open, role, onClose, onDeleted }: DeleteRoleConfirmProps) {
  const { t } = useTranslation();
  const del = useDeleteRole();
  const [confirmRef, setConfirmRef] = useState<HTMLButtonElement | null>(null);

  useEffect(() => {
    if (open) {
      del.reset();
      confirmRef?.focus();
    }
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    if (open) document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
    // del.reset is stable; intentionally exclude to run only on open/close.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, onClose, confirmRef]);

  if (!open || !role) return null;

  const mapped = del.error ? mapRolePermissionError(del.error) : null;

  const confirm = () => {
    if (!role.id) return;
    del.mutate(role.id, {
      onSuccess: () => {
        onClose();
        onDeleted();
      },
    });
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="role-delete-title"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
      data-testid="role-delete-dialog"
    >
      <div className="bg-surface rounded-xl shadow-lg border border-border w-full max-w-md p-6">
        <h2 id="role-delete-title" className="text-lg text-text-primary">
          {t('roles.delete.title')}
        </h2>
        <p className="text-sm text-text-secondary mt-2">
          {t('roles.delete.body', { role: role.name })}
        </p>
        {typeof role.assignmentCount === 'number' && role.assignmentCount > 0 ? (
          <p className="text-xs text-warning-700 mt-2" data-testid="role-delete-assignment-count">
            {t('roles.delete.assignmentCount', { count: role.assignmentCount })}
          </p>
        ) : null}

        {mapped ? (
          <div
            role="alert"
            className="mt-4 flex items-start gap-2 rounded-md border border-danger-500/30 bg-danger-50 px-3 py-2.5 text-sm text-danger-700"
            data-testid="role-delete-error"
          >
            <AlertTriangle size={16} className="mt-0.5 shrink-0" aria-hidden />
            <div>
              <p>{t(mapped.key)}</p>
              {mapped.correlationId ? (
                <p className="text-xs text-text-muted mt-1 font-mono">
                  {t('states.correlation_id')}: {mapped.correlationId}
                </p>
              ) : null}
            </div>
          </div>
        ) : null}

        <div className="flex justify-end gap-2 mt-6">
          <Button variant="secondary" onClick={onClose} disabled={del.isPending}>
            {t('common.cancel')}
          </Button>
          <Button
            ref={setConfirmRef}
            variant="danger"
            onClick={confirm}
            disabled={del.isPending}
            data-testid="role-delete-confirm"
          >
            {del.isPending ? t('roles.delete.deleting') : t('roles.delete.confirm')}
          </Button>
        </div>
      </div>
    </div>
  );
}
