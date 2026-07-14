import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Plus } from 'lucide-react';
import { Button } from '@/shared/components/ui/Button';
import { InlineBanner } from '@/shared/components/ui/InlineBanner';
import { PermissionGate } from '@/shared/components/access/PermissionGate';
import { PERMISSIONS } from '@/shared/types/permissions';
import { ErrorState } from '@/shared/components/feedback/ErrorState';
import { ConfirmDialog } from '@/shared/components/confirm-dialog/ConfirmDialog';
import { Breadcrumbs } from '@/shared/components/layout/Breadcrumbs';
import { ApiError } from '@/shared/api/apiError';
import { pickLocalized } from '@/shared/lib/localized';
import { useAuthStore } from '@/features/auth/authStore';
import { ProjectTable } from '../components/ProjectTable';
import { ProjectFormDrawer } from '../components/ProjectFormDrawer';
import { useProjects } from '../hooks/useProjects';
import { useCreateProject } from '../hooks/useCreateProject';
import { useUpdateProject } from '../hooks/useUpdateProject';
import { useArchiveProject } from '../hooks/useArchiveProject';
import type { Project } from '../types/projectTypes';

export function ProjectListPage() {
  const { t, i18n } = useTranslation();
  const [drawerOpen, setDrawerOpen] = useState(false);
  // null → create mode; a Project → edit mode (drawer prefilled from this row).
  const [editing, setEditing] = useState<Project | null>(null);
  const [formError, setFormError] = useState<string | undefined>(undefined);
  const [archiveTarget, setArchiveTarget] = useState<Project | null>(null);
  const [archiveError, setArchiveError] = useState<string | null>(null);

  const { data, isLoading, error, refetch } = useProjects();
  const createMutation = useCreateProject();
  const updateMutation = useUpdateProject();
  const archiveMutation = useArchiveProject();
  // FE-TI-004: make the active client-company explicit in the page header so
  // the user can immediately see which tenant scope the list reflects.
  const activeTenant = useAuthStore((s) => s.activeTenant);

  const subtitle = activeTenant
    ? t('projects.list_subtitle_for_tenant', { tenant: activeTenant.brand_name })
    : t('projects.list_subtitle');

  const openCreate = () => {
    setEditing(null);
    setFormError(undefined);
    setDrawerOpen(true);
  };

  const openEdit = (project: Project) => {
    setEditing(project);
    setFormError(undefined);
    setDrawerOpen(true);
  };

  const closeDrawer = () => {
    setDrawerOpen(false);
    setFormError(undefined);
  };

  const handleSubmit = async (input: Parameters<typeof createMutation.mutateAsync>[0]) => {
    setFormError(undefined);
    try {
      if (editing) {
        // Update command is { name_i18n, description, start_date, end_date } ONLY.
        // The code is IMMUTABLE — never send it (the server ignores it anyway).
        await updateMutation.mutateAsync({
          id: editing.id,
          payload: {
            name_i18n: input.name_i18n,
            description: input.description || undefined,
            start_date: input.start_date || undefined,
            end_date: input.end_date || undefined,
          },
        });
      } else {
        await createMutation.mutateAsync({
          code: input.code,
          name_i18n: input.name_i18n,
          description: input.description || undefined,
          start_date: input.start_date || undefined,
          end_date: input.end_date || undefined,
        });
      }
      closeDrawer();
    } catch (err) {
      // Surface the failure in the drawer instead of failing silently. Map the
      // backend duplicate-code conflict to a friendly localized message.
      if (err instanceof ApiError && err.code === 'PROJECT_CODE_TAKEN') {
        setFormError(t('projects.code_taken'));
      } else {
        setFormError(t('projects.save_error'));
      }
      // Re-throw so the drawer keeps itself open (its own submit awaits onSubmit).
      throw err;
    }
  };

  const confirmArchive = async () => {
    if (!archiveTarget) return;
    setArchiveError(null);
    try {
      await archiveMutation.mutateAsync(archiveTarget.id);
      setArchiveTarget(null);
      await refetch();
    } catch {
      setArchiveError(t('projects.archive_error'));
    }
  };

  return (
    <div className="space-y-6">
      <Breadcrumbs />
      <header className="flex items-start justify-between gap-4 flex-wrap">
        <div>
          <h1 className="text-2xl text-text-primary">{t('projects.list_title')}</h1>
          <p className="text-sm text-text-secondary mt-1" data-testid="projects-list-subtitle">
            {subtitle}
          </p>
        </div>
      </header>

      {error ? (
        <ErrorState onRetry={() => refetch()} />
      ) : (
        <>
          {/* Never silent: fetchProjects() pages to completion via the shared
              fetchAllPages helper; if its safety cap is ever hit say so rather
              than quietly presenting a partial catalog as the whole list. */}
          {data?.truncated ? (
            <InlineBanner variant="warning" role="status" data-testid="projects-list-truncated">
              {t('dataTable.results_truncated', {
                shown: data.items.length,
                total: data.total_elements,
              })}
            </InlineBanner>
          ) : null}
          <ProjectTable
            rows={data?.items ?? []}
            loading={isLoading}
            onEdit={openEdit}
            onArchive={(p) => {
              setArchiveError(null);
              setArchiveTarget(p);
            }}
            toolbarRight={
              <PermissionGate permission={PERMISSIONS.PROJECT_CREATE}>
                <Button
                  variant="primary"
                  size="sm"
                  leadingIcon={<Plus size={14} />}
                  onClick={openCreate}
                  data-testid="new-project-button"
                >
                  {t('projects.new_project')}
                </Button>
              </PermissionGate>
            }
          />
        </>
      )}

      <ProjectFormDrawer
        open={drawerOpen}
        initial={editing ?? undefined}
        error={formError}
        onClose={closeDrawer}
        onSubmit={handleSubmit}
      />

      <ConfirmDialog
        open={archiveTarget !== null}
        destructive
        title={t('projects.archive_confirm_title')}
        body={t('projects.archive_confirm_body', {
          name: archiveTarget ? pickLocalized(archiveTarget.name_i18n, i18n.language) : '',
        })}
        confirmLabel={t('common.archive')}
        busy={archiveMutation.isPending}
        error={archiveError}
        onConfirm={() => void confirmArchive()}
        onCancel={() => {
          setArchiveTarget(null);
          setArchiveError(null);
        }}
      />
    </div>
  );
}
