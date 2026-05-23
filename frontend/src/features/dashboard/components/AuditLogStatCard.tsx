import { useTranslation } from 'react-i18next';
import { ShieldAlert } from 'lucide-react';
import { StatCard } from '@/shared/components/cards/StatCard';
import { PermissionGate } from '@/shared/components/access/PermissionGate';
import { PERMISSIONS } from '@/shared/types/permissions';

interface AuditLogStatCardProps {
  value?: number | string;
}

/**
 * Dashboard StatCard for audit alerts.
 * Hidden completely from users without AUDIT_READ (verification finding fix).
 */
export function AuditLogStatCard({ value = '—' }: AuditLogStatCardProps) {
  const { t } = useTranslation();
  return (
    <PermissionGate permission={PERMISSIONS.AUDIT_READ}>
      <StatCard
        label={t('audit.stat_card_label')}
        value={value}
        icon={<ShieldAlert size={18} aria-hidden />}
        tone="danger"
        data-testid="audit-log-stat-card"
      />
    </PermissionGate>
  );
}
