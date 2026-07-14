import { useMemo, useState } from 'react';
import { useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Plus } from 'lucide-react';
import { Button } from '@/shared/components/ui/Button';
import { Breadcrumbs } from '@/shared/components/layout/Breadcrumbs';
import { FilterBar, type FilterDefinition } from '@/shared/components/data-table/FilterBar';
import { PermissionGate } from '@/shared/components/access/PermissionGate';
import { PERMISSIONS } from '@/shared/types/permissions';
import { ErrorState } from '@/shared/components/feedback/ErrorState';
import { ConfirmDialog } from '@/shared/components/confirm-dialog/ConfirmDialog';
import { ApiError } from '@/shared/api/apiError';
import { pickLocalized } from '@/shared/lib/localized';
import { PositionTable } from '../components/PositionTable';
import { PositionFormDrawer } from '../components/PositionFormDrawer';
import { usePositions } from '../hooks/usePositions';
import { useCreatePosition } from '../hooks/useCreatePosition';
import { useUpdatePosition } from '../hooks/useUpdatePosition';
import { useArchivePosition } from '../hooks/useArchivePosition';
import { useDepartmentTree } from '@/features/organization/hooks/useDepartmentTree';
import { useJobProfileStatusesByPositions } from '@/features/job-profiles/hooks/useJobProfile';
import { usePermission } from '@/features/auth/usePermission';
import type { Position, PositionStatus } from '../types/positionTypes';

/**
 * Real server page size for the browsable Position Catalog. The former
 * `page: 0, size: 50` was HARD-CODED and never changed — every page beyond
 * the first 50 positions was invisible with the table still reading "Page 1
 * of 1" (EPIC-013). `page` is now real component state wired to the shared
 * `PaginationBar` pager (via `DataTable`'s `serverPagination` prop), and
 * `size` matches the backend's own default page size — no more guessing.
 */
const PAGE_SIZE = 20;

export function PositionListPage() {
  const { t, i18n } = useTranslation();
  const { projectId } = useParams<{ projectId: string }>();

  // Filter state
  const [departmentId, setDepartmentId] = useState<string | null>(null);
  const [status, setStatus] = useState<PositionStatus | null>(null);
  const [jobFamily, setJobFamily] = useState<string | null>(null);
  const [page, setPage] = useState(0);

  // Drawer / CRUD state — null → create mode; a Position → edit mode
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<Position | null>(null);
  const [formError, setFormError] = useState<string | undefined>(undefined);

  // Archive confirm state
  const [archiveTarget, setArchiveTarget] = useState<Position | null>(null);
  const [archiveError, setArchiveError] = useState<string | null>(null);

  const tree = useDepartmentTree(projectId);
  const positions = usePositions(
    projectId
      ? { projectId, departmentId, status, jobFamily, page, size: PAGE_SIZE }
      : null,
  );
  const createMutation = useCreatePosition();
  // useUpdatePosition requires the id at hook-call time (Rules of Hooks).
  // Pass the editing id, or empty string when not editing — the mutation is only
  // called inside handleSubmit when `editing` is non-null.
  const updateMutation = useUpdatePosition(editing?.id ?? '');
  const archiveMutation = useArchivePosition();

  const departments = useMemo(() => tree.data ?? [], [tree.data]);
  const rows = useMemo(() => positions.data?.items ?? [], [positions.data]);

  // PAGE-2: surface job-profile context directly in the Position Catalog so
  // the sidebar "Job Profiles" entry (and the workflow stepper's JOB_PROFILES
  // stage) — which both route here — land somewhere meaningful instead of a
  // generic position list with no job-profile signal. Bounded to the CURRENT
  // server page's ids (a single bulk `GET /job-profiles/statuses` call — see
  // `useJobProfileStatusesByPositions`). Hidden entirely (UX-only gate;
  // backend remains source of truth) for users without JOB_PROFILE_READ.
  const { can } = usePermission();
  const canReadJobProfiles = can(PERMISSIONS.JOB_PROFILE_READ);
  const positionIds = useMemo(() => rows.map((p) => p.id), [rows]);
  const { statuses: jobProfileByPositionId, isError: jobProfileStatusError } =
    useJobProfileStatusesByPositions(canReadJobProfiles ? positionIds : []);

  const filters: FilterDefinition[] = useMemo(
    () => [
      {
        key: 'department',
        label: t('positions.filter_department'),
        value: departmentId,
        onChange: (v) => {
          setDepartmentId(v);
          setPage(0);
        },
        options: departments
          .filter((d) => d.status !== 'ARCHIVED')
          .map((d) => ({ value: d.id, label: `${d.code} · ${pickLocalized(d.name_i18n, i18n.language)}` })),
      },
      {
        key: 'jobFamily',
        label: t('positions.filter_job_family'),
        value: jobFamily,
        onChange: (v) => {
          setJobFamily(v);
          setPage(0);
        },
        options: Array.from(new Set(rows.map((p) => p.job_family).filter((x): x is string => !!x))).map(
          (jf) => ({ value: jf, label: jf }),
        ),
      },
      {
        key: 'status',
        label: t('positions.filter_status'),
        value: status,
        onChange: (v) => {
          setStatus((v as PositionStatus | null) ?? null);
          setPage(0);
        },
        options: [
          { value: 'ACTIVE', label: t('status.active') },
          { value: 'DRAFT', label: t('status.draft') },
          { value: 'ARCHIVED', label: t('status.archived') },
        ],
      },
    ],
    [departments, rows, departmentId, jobFamily, status, t, i18n.language],
  );

  const openCreate = () => {
    setEditing(null);
    setFormError(undefined);
    setDrawerOpen(true);
  };

  const openEdit = (position: Position) => {
    setEditing(position);
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
        // Update command: department_id, title_i18n, function, category,
        // job_family, job_level. The code is IMMUTABLE — never send it on PATCH.
        await updateMutation.mutateAsync({
          department_id: input.department_id,
          title_i18n: input.title_i18n,
          function: input.function || undefined,
          category: input.category || undefined,
          job_family: input.job_family || undefined,
          job_level: input.job_level || undefined,
        });
      } else {
        await createMutation.mutateAsync({
          project_id: input.project_id,
          department_id: input.department_id,
          code: input.code,
          title_i18n: input.title_i18n,
          function: input.function || undefined,
          category: input.category || undefined,
          job_family: input.job_family || undefined,
          job_level: input.job_level || undefined,
        });
      }
      closeDrawer();
    } catch (err) {
      // Surface the failure in the drawer instead of failing silently. Map the
      // backend error codes to friendly localized messages.
      if (err instanceof ApiError) {
        if (err.code === 'POSITION_CODE_TAKEN') {
          setFormError(t('positions.code_taken'));
        } else if (err.code === 'POSITION_DEPARTMENT_INVALID') {
          setFormError(t('positions.department_invalid'));
        } else if (err.code === 'POSITION_DEPARTMENT_ARCHIVED') {
          setFormError(t('positions.department_archived'));
        } else {
          setFormError(t('positions.save_error'));
        }
      } else {
        setFormError(t('positions.save_error'));
      }
      // Re-throw so the drawer keeps itself open (its submit awaits onSubmit).
      throw err;
    }
  };

  const confirmArchive = async () => {
    if (!archiveTarget) return;
    setArchiveError(null);
    try {
      await archiveMutation.mutateAsync(archiveTarget.id);
      setArchiveTarget(null);
      await positions.refetch();
    } catch {
      setArchiveError(t('positions.archive_error'));
    }
  };

  return (
    <div className="space-y-6">
      <Breadcrumbs />
      <header className="flex items-start justify-between gap-4 flex-wrap">
        <div>
          <h1 className="text-2xl text-text-primary">{t('positions.list_title')}</h1>
          <p className="text-sm text-text-secondary mt-1">{t('positions.list_subtitle')}</p>
        </div>
      </header>

      {positions.error ? (
        <ErrorState onRetry={() => positions.refetch()} />
      ) : (
        <PositionTable
          projectId={projectId ?? ''}
          rows={rows}
          departments={departments}
          loading={positions.isFetching}
          onEdit={openEdit}
          jobProfileByPositionId={canReadJobProfiles ? jobProfileByPositionId : undefined}
          jobProfileStatusError={canReadJobProfiles ? jobProfileStatusError : false}
          onArchive={(p) => {
            setArchiveError(null);
            setArchiveTarget(p);
          }}
          filterBar={
            <FilterBar
              filters={filters}
              onReset={() => {
                setDepartmentId(null);
                setStatus(null);
                setJobFamily(null);
                setPage(0);
              }}
            />
          }
          serverPagination={{
            page,
            totalPages: positions.data?.total_pages ?? 1,
            total: positions.data?.total_elements ?? 0,
            pageSize: PAGE_SIZE,
            onPageChange: setPage,
          }}
          toolbarRight={
            <PermissionGate permission={PERMISSIONS.POSITION_CREATE}>
              <Button
                variant="primary"
                size="sm"
                leadingIcon={<Plus size={14} />}
                onClick={openCreate}
                data-testid="new-position-button"
              >
                {t('positions.new_position')}
              </Button>
            </PermissionGate>
          }
        />
      )}

      <PositionFormDrawer
        open={drawerOpen}
        projectId={projectId ?? ''}
        departments={departments}
        initial={editing ?? undefined}
        error={formError}
        onClose={closeDrawer}
        onSubmit={handleSubmit}
      />

      <ConfirmDialog
        open={archiveTarget !== null}
        destructive
        title={t('positions.archive_confirm_title')}
        body={t('positions.archive_confirm_body', {
          name: archiveTarget ? pickLocalized(archiveTarget.title_i18n, i18n.language) : '',
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
