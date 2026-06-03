/**
 * /app/users/:id — single user detail view.
 *
 * Header section (name, email, status, last login) + memberships section
 * (one card per tenant) + audit link.
 *
 * Audit view itself lives in /app/audit?userId= and requires AUDIT_READ;
 * here we just deep-link to it.
 */
import { useTranslation } from 'react-i18next';
import { useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, Mail, Calendar, ExternalLink } from 'lucide-react';
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
import { UserStatusBadge } from '../components/UserStatusBadge';
import { MembershipCard } from '../components/MembershipCard';

export function UserDetailsPage() {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const { userId } = useParams<{ userId: string }>();
  const { data, isLoading, error, refetch } = useUser(userId);

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
    </div>
  );
}
