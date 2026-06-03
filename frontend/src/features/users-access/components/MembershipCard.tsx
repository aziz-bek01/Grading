/**
 * Per-tenant membership card on the user details page.
 *
 * Renders:
 *   - Tenant name + status badge
 *   - List of roles with per-role remove buttons (gated by USER_ACCESS_MANAGE)
 *   - "Add role" CTA
 *   - SalaryPermissionToggle (gated by USER_SALARY_PERMISSION_GRANT)
 *   - "Revoke membership" destructive CTA (gated by USER_ACCESS_MANAGE)
 */
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Plus, X } from 'lucide-react';
import { Button } from '@/shared/components/ui/Button';
import { Card } from '@/shared/components/ui/Card';
import { PermissionGate } from '@/shared/components/access/PermissionGate';
import { PERMISSIONS } from '@/shared/types/permissions';
import type { UserMembership } from '../types/userTypes';
import { UserStatusBadge } from './UserStatusBadge';
import { AssignRoleDialog } from './AssignRoleDialog';
import { RevokeMembershipConfirm } from './RevokeMembershipConfirm';
import { SalaryPermissionToggle } from './SalaryPermissionToggle';
import {
  useAddRole,
  useRemoveRole,
  useRevokeMembership,
  useUpdateSalaryPermission,
} from '../hooks/useUserMutations';

interface MembershipCardProps {
  userId: string;
  userName: string;
  membership: UserMembership;
}

export function MembershipCard({ userId, userName, membership }: MembershipCardProps) {
  const { t } = useTranslation();
  const [assignOpen, setAssignOpen] = useState(false);
  const [revokeOpen, setRevokeOpen] = useState(false);
  const isRevoked = membership.status === 'REVOKED';

  const addRole = useAddRole(userId, membership.tenant_id);
  const removeRole = useRemoveRole(userId, membership.tenant_id);
  const revokeMembership = useRevokeMembership(userId);
  const updateSalary = useUpdateSalaryPermission(userId, membership.tenant_id);

  return (
    <Card
      compact
      className="space-y-4"
      data-testid={`membership-card-${membership.tenant_id}`}
    >
      <header className="flex items-start justify-between gap-3">
        <div>
          <h3 className="text-base font-medium text-text-primary">
            {membership.tenant_brand_name}
          </h3>
          <div className="mt-1 flex items-center gap-2">
            <UserStatusBadge status={membership.status} />
            {membership.joined_at ? (
              <span className="text-xs text-text-muted">
                {t('users.membership.joinedAt', {
                  date: new Date(membership.joined_at).toLocaleDateString(),
                })}
              </span>
            ) : (
              <span className="text-xs text-text-muted">
                {t('users.membership.invitedAt', {
                  date: new Date(membership.invited_at).toLocaleDateString(),
                })}
              </span>
            )}
          </div>
        </div>
        {!isRevoked ? (
          <PermissionGate permission={PERMISSIONS.USER_ACCESS_MANAGE}>
            <Button
              variant="ghost"
              size="sm"
              onClick={() => setRevokeOpen(true)}
              data-testid={`revoke-membership-${membership.tenant_id}`}
            >
              {t('users.membership.revoke')}
            </Button>
          </PermissionGate>
        ) : null}
      </header>

      <section aria-labelledby={`roles-${membership.tenant_id}`}>
        <div className="flex items-center justify-between">
          <h4
            id={`roles-${membership.tenant_id}`}
            className="text-sm font-medium text-text-secondary"
          >
            {t('users.membership.rolesLabel', { count: membership.roles.length })}
          </h4>
          {!isRevoked ? (
            <PermissionGate permission={PERMISSIONS.USER_ACCESS_MANAGE}>
              <Button
                variant="ghost"
                size="sm"
                leadingIcon={<Plus size={14} />}
                onClick={() => setAssignOpen(true)}
                data-testid={`add-role-${membership.tenant_id}`}
              >
                {t('users.membership.addRole')}
              </Button>
            </PermissionGate>
          ) : null}
        </div>
        {membership.roles.length === 0 ? (
          <p className="mt-2 text-sm text-text-muted">{t('users.membership.noRoles')}</p>
        ) : (
          <ul className="mt-2 flex flex-wrap gap-2">
            {membership.roles.map((r) => (
              <li
                key={r.id}
                className="inline-flex items-center gap-1 rounded-full bg-primary-50 text-primary-700 text-xs font-medium pl-3 pr-1 py-1 border border-primary-500/20"
              >
                <span>{t(`users.role.${r.role_code}`, { defaultValue: r.role_code })}</span>
                {!isRevoked ? (
                  <PermissionGate permission={PERMISSIONS.USER_ACCESS_MANAGE}>
                    <button
                      type="button"
                      aria-label={t('users.membership.removeRoleAria', {
                        role: r.role_code,
                      })}
                      onClick={() => removeRole.mutate(r.id)}
                      className="ml-1 text-primary-700 hover:text-danger-700 rounded-full p-0.5 hover:bg-danger-50"
                      data-testid={`remove-role-${r.id}`}
                    >
                      <X size={12} />
                    </button>
                  </PermissionGate>
                ) : null}
              </li>
            ))}
          </ul>
        )}
      </section>

      {!isRevoked ? (
        <SalaryPermissionToggle
          enabled={membership.salary_data_permission}
          grantedAt={membership.salary_data_permission_granted_at}
          disabled={updateSalary.isPending}
          onSubmit={async (next, reason) => {
            await updateSalary.mutateAsync({ enabled: next, reason });
          }}
        />
      ) : null}

      <AssignRoleDialog
        open={assignOpen}
        tenantBrand={membership.tenant_brand_name}
        alreadyAssigned={membership.roles.map((r) => r.role_code)}
        onClose={() => setAssignOpen(false)}
        onSubmit={async (data) => {
          await addRole.mutateAsync(data);
        }}
      />
      <RevokeMembershipConfirm
        open={revokeOpen}
        tenantBrand={membership.tenant_brand_name}
        userName={userName}
        onCancel={() => setRevokeOpen(false)}
        onConfirm={async () => {
          await revokeMembership.mutateAsync(membership.tenant_id);
          setRevokeOpen(false);
        }}
      />
    </Card>
  );
}
