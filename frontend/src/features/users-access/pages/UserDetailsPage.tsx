/**
 * /app/users/:id — single user detail view.
 *
 * Header section (name, email, status, last login) + memberships section
 * (one card per tenant) + audit link.
 *
 * Audit view itself lives in /app/audit?userId= and requires AUDIT_READ;
 * here we just deep-link to it.
 */
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate, useParams } from 'react-router-dom';
import {
  ArrowLeft,
  Mail,
  Calendar,
  ExternalLink,
  Pencil,
  Ban,
  CheckCircle2,
  Building2,
  KeyRound,
} from 'lucide-react';
import { Button } from '@/shared/components/ui/Button';
import { Card } from '@/shared/components/ui/Card';
import { Breadcrumbs } from '@/shared/components/layout/Breadcrumbs';
import { ErrorState } from '@/shared/components/feedback/ErrorState';
import { LoadingState } from '@/shared/components/feedback/LoadingState';
import { NoAccessState } from '@/shared/components/feedback/NoAccessState';
import { PermissionGate } from '@/shared/components/access/PermissionGate';
import { PERMISSIONS } from '@/shared/types/permissions';
import { routes } from '@/shared/config/routes';
import { ApiError } from '@/shared/api/apiError';
import { useUser } from '../hooks/useUser';
import { useAddMembership, useResetLogin, useUpdateUser } from '../hooks/useUserMutations';
import { UserStatusBadge } from '../components/UserStatusBadge';
import { MembershipCard } from '../components/MembershipCard';
import { EditUserDialog } from '../components/EditUserDialog';
import { AddMembershipDialog } from '../components/AddMembershipDialog';
import { DisableUserConfirm } from '../components/DisableUserConfirm';
import { ResetLoginDialog } from '../components/ResetLoginDialog';

const USER_EDIT_GATE = [PERMISSIONS.USER_UPDATE, PERMISSIONS.USER_ACCESS_MANAGE];
const MEMBERSHIP_GATE = [PERMISSIONS.USER_MEMBERSHIP_MANAGE, PERMISSIONS.USER_ACCESS_MANAGE];

export function UserDetailsPage() {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const { userId } = useParams<{ userId: string }>();
  const { data, isLoading, error, refetch } = useUser(userId);

  const [editOpen, setEditOpen] = useState(false);
  const [addMembershipOpen, setAddMembershipOpen] = useState(false);
  const [disableOpen, setDisableOpen] = useState(false);
  const [resetLoginOpen, setResetLoginOpen] = useState(false);
  const [resetLoginDone, setResetLoginDone] = useState(false);

  const updateUser = useUpdateUser(userId ?? '');
  const addMembership = useAddMembership(userId ?? '');
  const resetLogin = useResetLogin(userId ?? '');

  // Auto-dismiss the reset-login success banner like a transient toast.
  useEffect(() => {
    if (!resetLoginDone) return;
    const id = window.setTimeout(() => setResetLoginDone(false), 5000);
    return () => window.clearTimeout(id);
  }, [resetLoginDone]);

  if (isLoading) {
    return (
      <div className="space-y-6">
        <Breadcrumbs extra={[{ label: t('nav.users_access'), to: routes.usersAccess }]} />
        <LoadingState />
      </div>
    );
  }

  if (error) {
    const isForbidden = error instanceof ApiError && (error.status === 403 || error.status === 404);
    if (isForbidden) {
      return (
        <div className="space-y-6">
          <Breadcrumbs extra={[{ label: t('nav.users_access'), to: routes.usersAccess }]} />
          <NoAccessState />
        </div>
      );
    }
    return (
      <div className="space-y-6">
        <Breadcrumbs extra={[{ label: t('nav.users_access'), to: routes.usersAccess }]} />
        <ErrorState onRetry={() => refetch()} />
      </div>
    );
  }

  if (!data) return null;

  // DISABLED (soft-deleted) and LOCKED (IdP lockout) are both "inactive" — the
  // status flip offers Reactivate (PATCH status=ACTIVE, the only re-enable the
  // backend accepts). ACTIVE / INVITED users get the destructive Disable flow.
  const isInactive = data.status === 'DISABLED' || data.status === 'LOCKED';

  return (
    <div className="space-y-6">
      <Breadcrumbs
        extra={[
          { label: t('nav.users_access'), to: routes.usersAccess },
          { label: data.full_name },
        ]}
      />

      <div>
        <Button
          variant="ghost"
          size="sm"
          leadingIcon={<ArrowLeft size={14} />}
          onClick={() => navigate(routes.usersAccess)}
        >
          {t('common.back')}
        </Button>
      </div>

      {resetLoginDone ? (
        <div
          role="status"
          className="rounded-md border border-success-500/30 bg-success-50 px-4 py-3 text-sm text-success-700"
          data-testid="reset-login-success"
        >
          {t('users.resetLogin.success', { name: data.full_name })}
        </div>
      ) : null}

      <Card>
        <div className="flex items-start justify-between gap-4 flex-wrap">
          <div>
            <h1 className="text-2xl text-text-primary">{data.full_name}</h1>
            <div className="mt-2 flex flex-wrap items-center gap-3 text-sm text-text-secondary">
              <span className="inline-flex items-center gap-1">
                <Mail size={14} aria-hidden />
                {data.email}
              </span>
              <UserStatusBadge status={data.status} />
              <span className="inline-flex items-center gap-1">
                <Calendar size={14} aria-hidden />
                {data.last_login_at
                  ? t('users.lastLoginAt', {
                      date: new Date(data.last_login_at).toLocaleString(i18n.language),
                    })
                  : t('users.neverLoggedIn')}
              </span>
            </div>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <PermissionGate permission={USER_EDIT_GATE} mode="any">
              <Button
                variant="secondary"
                size="sm"
                leadingIcon={<Pencil size={14} />}
                onClick={() => setEditOpen(true)}
                data-testid="edit-user-button"
              >
                {t('users.edit.button')}
              </Button>
            </PermissionGate>

            <PermissionGate permission={USER_EDIT_GATE} mode="any">
              <Button
                variant="secondary"
                size="sm"
                leadingIcon={<KeyRound size={14} />}
                onClick={() => setResetLoginOpen(true)}
                data-testid="reset-login-button"
              >
                {t('users.resetLogin.button')}
              </Button>
            </PermissionGate>

            <PermissionGate permission={USER_EDIT_GATE} mode="any">
              {isInactive ? (
                <Button
                  variant="secondary"
                  size="sm"
                  leadingIcon={<CheckCircle2 size={14} />}
                  onClick={() => updateUser.mutate({ status: 'ACTIVE' })}
                  disabled={updateUser.isPending}
                  data-testid="reactivate-user-button"
                >
                  {t('users.reactivate.button')}
                </Button>
              ) : (
                <Button
                  variant="danger"
                  size="sm"
                  leadingIcon={<Ban size={14} />}
                  onClick={() => setDisableOpen(true)}
                  disabled={updateUser.isPending}
                  data-testid="disable-user-button"
                >
                  {t('users.disable.button')}
                </Button>
              )}
            </PermissionGate>

            <PermissionGate permission={MEMBERSHIP_GATE} mode="any">
              <Button
                variant="secondary"
                size="sm"
                leadingIcon={<Building2 size={14} />}
                onClick={() => setAddMembershipOpen(true)}
                data-testid="add-membership-button"
              >
                {t('users.addMembership.button')}
              </Button>
            </PermissionGate>

            <PermissionGate permission={PERMISSIONS.AUDIT_READ}>
              <Button
                variant="secondary"
                size="sm"
                trailingIcon={<ExternalLink size={14} />}
                onClick={() => navigate(`${routes.audit}?userId=${data.id}`)}
              >
                {t('users.viewAudit')}
              </Button>
            </PermissionGate>
          </div>
        </div>
      </Card>

      <section aria-labelledby="memberships-heading" className="space-y-3">
        <div className="flex items-center justify-between">
          <h2 id="memberships-heading" className="text-lg text-text-primary">
            {t('users.memberships.title')}
          </h2>
          <span className="text-xs text-text-muted">
            {t('users.memberships.count', { count: data.memberships.length })}
          </span>
        </div>
        {data.memberships.length === 0 ? (
          <Card>
            <p className="text-sm text-text-secondary">{t('users.memberships.empty')}</p>
          </Card>
        ) : (
          <div className="space-y-3">
            {data.memberships.map((m) => (
              <MembershipCard
                key={m.tenant_id}
                userId={data.id}
                userName={data.full_name}
                membership={m}
              />
            ))}
          </div>
        )}
      </section>

      <EditUserDialog
        open={editOpen}
        user={data}
        onClose={() => setEditOpen(false)}
        onSubmit={async (payload) => {
          await updateUser.mutateAsync(payload);
        }}
      />

      <AddMembershipDialog
        open={addMembershipOpen}
        existingTenantIds={data.memberships.map((m) => m.tenant_id)}
        onClose={() => setAddMembershipOpen(false)}
        onSubmit={async (payload) => {
          await addMembership.mutateAsync(payload);
        }}
      />

      <DisableUserConfirm
        open={disableOpen}
        userName={data.full_name}
        onCancel={() => setDisableOpen(false)}
        onConfirm={async () => {
          await updateUser.mutateAsync({ status: 'DISABLED' });
          setDisableOpen(false);
        }}
      />

      <ResetLoginDialog
        open={resetLoginOpen}
        onClose={() => setResetLoginOpen(false)}
        onSubmit={async (payload) => {
          await resetLogin.mutateAsync(payload);
          setResetLoginDone(true);
        }}
      />
    </div>
  );
}
