import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Plus, Search } from 'lucide-react';
import { Card } from '@/shared/components/ui/Card';
import { Button } from '@/shared/components/ui/Button';
import { Breadcrumbs } from '@/shared/components/layout/Breadcrumbs';
import { LoadingState } from '@/shared/components/feedback/LoadingState';
import { ErrorState } from '@/shared/components/feedback/ErrorState';
import { EmptyState } from '@/shared/components/feedback/EmptyState';
import { PermissionGate } from '@/shared/components/access/PermissionGate';
import { PERMISSIONS } from '@/shared/types/permissions';
import { pickLocalized } from '@/shared/lib/localized';
import { DepartmentTree } from '../components/DepartmentTree';
import { DepartmentDrawer } from '../components/DepartmentDrawer';
import { useDepartmentTree } from '../hooks/useDepartmentTree';
import { useCreateDepartment } from '../hooks/useCreateDepartment';

export function OrganizationPage() {
  const { t, i18n } = useTranslation();
  const { projectId } = useParams<{ projectId: string }>();
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [showArchived, setShowArchived] = useState(false);
  const [query, setQuery] = useState('');
  const [drawerOpen, setDrawerOpen] = useState(false);

  const tree = useDepartmentTree(projectId);
  const createMutation = useCreateDepartment(projectId ?? '');

  const items = tree.data ?? [];
  const selected = items.find((d) => d.id === selectedId) ?? null;

  return (
    <div className="space-y-6">
      <Breadcrumbs />
      <header className="flex items-start justify-between gap-4 flex-wrap">
        <div>
          <h1 className="text-2xl text-text-primary">{t('organization.page_title')}</h1>
          <p className="text-sm text-text-secondary mt-1">{t('organization.page_subtitle')}</p>
        </div>
        <PermissionGate permission={PERMISSIONS.ORG_EDIT}>
          <Button
            variant="primary"
            size="sm"
            leadingIcon={<Plus size={14} />}
            onClick={() => setDrawerOpen(true)}
            data-testid="new-department-button"
          >
            {t('organization.new_department')}
          </Button>
        </PermissionGate>
      </header>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        <Card title={t('organization.page_title')} compact className="lg:col-span-2">
          {tree.isLoading ? (
            <LoadingState />
          ) : tree.error ? (
            <ErrorState onRetry={() => tree.refetch()} />
          ) : (
            <div className="space-y-3">
              <div className="flex items-center gap-3 flex-wrap">
                <label className="relative flex-1 min-w-[200px]">
                  <span className="sr-only">{t('common.search')}</span>
                  <Search size={14} aria-hidden className="absolute left-2 top-1/2 -translate-y-1/2 text-text-muted" />
                  <input
                    type="search"
                    value={query}
                    onChange={(e) => setQuery(e.target.value)}
                    placeholder={t('organization.search_placeholder')}
                    className="w-full h-9 pl-7 pr-3 border border-border-strong rounded-md text-sm bg-surface"
                    data-testid="dep-search"
                  />
                </label>
                <label className="inline-flex items-center gap-2 text-sm text-text-secondary">
                  <input
                    type="checkbox"
                    checked={showArchived}
                    onChange={(e) => setShowArchived(e.target.checked)}
                    data-testid="show-archived-toggle"
                  />
                  {t('organization.show_archived')}
                </label>
              </div>
              <DepartmentTree
                items={items}
                selectedId={selectedId}
                onSelect={setSelectedId}
                showArchived={showArchived}
                query={query}
              />
            </div>
          )}
        </Card>

        <Card title={selected ? selected.code : t('organization.detail_empty_title')} compact>
          {selected ? (
            <div className="space-y-2 text-sm">
              <div>
                <div className="text-xs uppercase text-text-muted">{t('common.name')}</div>
                <div className="text-text-primary">{pickLocalized(selected.name, i18n.language)}</div>
              </div>
              <div>
                <div className="text-xs uppercase text-text-muted">{t('common.type')}</div>
                <div className="text-text-primary">
                  {t(`organization.type_${selected.type.toLowerCase()}`)}
                </div>
              </div>
              <div>
                <div className="text-xs uppercase text-text-muted">{t('common.status')}</div>
                <div className="text-text-primary">{selected.status}</div>
              </div>
            </div>
          ) : (
            <EmptyState title={t('organization.detail_empty_title')} body={t('organization.detail_empty_body')} />
          )}
        </Card>
      </div>

      <DepartmentDrawer
        open={drawerOpen}
        projectId={projectId ?? ''}
        items={items}
        onClose={() => setDrawerOpen(false)}
        onSubmit={async (input) => {
          await createMutation.mutateAsync({
            project_id: input.project_id,
            parent_id: input.parent_id,
            code: input.code,
            name: input.name,
            type: input.type,
          });
        }}
      />
    </div>
  );
}
