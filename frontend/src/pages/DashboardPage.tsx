import { useTranslation } from 'react-i18next';
import { StatCard } from '@/shared/components/cards/StatCard';
import { Card } from '@/shared/components/ui/Card';
import { EmptyState } from '@/shared/components/feedback/EmptyState';
import { Building2, FolderKanban, Scale } from 'lucide-react';
import { AuditLogStatCard } from '@/features/dashboard/components/AuditLogStatCard';

export function DashboardPage() {
  const { t } = useTranslation();
  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-2xl text-text-primary">{t('pages.dashboard_title')}</h1>
        <p className="text-sm text-text-secondary mt-1">{t('pages.dashboard_subtitle')}</p>
      </header>
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
        <StatCard label={t('nav.clients')} value="—" icon={<Building2 size={18} aria-hidden />} />
        <StatCard label={t('nav.projects')} value="—" icon={<FolderKanban size={18} aria-hidden />} />
        <StatCard label={t('nav.methodology')} value="—" icon={<Scale size={18} aria-hidden />} />
        <AuditLogStatCard />
      </div>
      <Card title={t('nav.projects')}>
        <EmptyState className="py-10" />
      </Card>
    </div>
  );
}
