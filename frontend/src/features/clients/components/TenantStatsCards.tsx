import { useTranslation } from 'react-i18next';
import { FolderKanban, Users, Activity, Clock } from 'lucide-react';
import { Card } from '@/shared/components/ui/Card';
import type { TenantStats } from '../types/clientTypes';

interface TenantStatsCardsProps {
  stats: TenantStats | undefined;
  loading?: boolean;
}

/**
 * Four KPI cards rendered at the top of the client-detail page.
 * Numeric values fall back to "—" while loading; never shows salary data.
 */
export function TenantStatsCards({ stats, loading }: TenantStatsCardsProps) {
  const { t, i18n } = useTranslation();
  const dash = '—';
  const fmtDate = (iso: string | null) =>
    iso ? new Date(iso).toLocaleString(i18n.language) : t('clients.stats.never_active');

  const cards = [
    {
      key: 'projects',
      icon: <FolderKanban size={18} aria-hidden />,
      label: t('clients.stats.projects'),
      value: loading || !stats ? dash : stats.project_count,
    },
    {
      key: 'users',
      icon: <Users size={18} aria-hidden />,
      label: t('clients.stats.users'),
      value: loading || !stats ? dash : stats.user_count,
    },
    {
      key: 'active_now',
      icon: <Activity size={18} aria-hidden />,
      label: t('clients.stats.activity_30d'),
      // We don't have a 30-day window in the contract yet; fall back to the
      // boolean "has activity in the last 7 days" derived from last_activity_at.
      value:
        loading || !stats
          ? dash
          : isWithinDays(stats.last_activity_at, 7)
          ? t('clients.stats.activity_recent')
          : t('clients.stats.activity_quiet'),
    },
    {
      key: 'last_activity',
      icon: <Clock size={18} aria-hidden />,
      label: t('clients.stats.last_activity'),
      value: loading || !stats ? dash : fmtDate(stats.last_activity_at),
    },
  ];

  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
      {cards.map((c) => (
        <Card key={c.key} compact className="!p-4">
          <div className="flex items-start justify-between gap-2">
            <span className="text-xs text-text-secondary uppercase tracking-wide">{c.label}</span>
            <span className="text-primary-500">{c.icon}</span>
          </div>
          <div
            className="mt-2 text-2xl font-semibold text-text-primary truncate"
            data-testid={`tenant-stat-${c.key}`}
          >
            {c.value}
          </div>
        </Card>
      ))}
    </div>
  );
}

function isWithinDays(iso: string | null, days: number): boolean {
  if (!iso) return false;
  const ts = new Date(iso).getTime();
  if (Number.isNaN(ts)) return false;
  return Date.now() - ts <= days * 24 * 60 * 60 * 1000;
}
