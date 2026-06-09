/**
 * Access-scope management for ONE tenant membership (slice E4-S1).
 *
 * Lets an admin restrict a user (e.g. an evaluation expert / department manager)
 * to specific DEPARTMENTS and PROJECTS within the tenant. Two independent
 * REPLACE-set saves:
 *   - Department scope  → PUT /users/{id}/department-scopes
 *   - Project scope     → PUT /users/{id}/project-assignments
 *
 * Reuse:
 *   - department TREE multiselect reuses the org-structure data + tree
 *     (`useTenantDepartments` → org `fetchDepartmentTree`; rendering via
 *     `DepartmentScopeTree` which uses the org `buildDepartmentTree`);
 *   - project multiselect reuses the projects list query (`useProjects`).
 *
 * Gating: the whole section is mounted only when the caller holds
 * USER_MEMBERSHIP_MANAGE or USER_ACCESS_MANAGE (see PermissionGate wrapper at
 * the call site / the inline guard below). Backend remains the source of truth.
 */
import { useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Info, ListTree, FolderKanban, Save } from 'lucide-react';
import { Button } from '@/shared/components/ui/Button';
import { pickLocalized } from '@/shared/lib/localized';
import { ApiError } from '@/shared/api/apiError';
import { useProjects } from '@/features/projects/hooks/useProjects';
import { useTenantDepartments } from '../hooks/useTenantDepartments';
import {
  useSetDepartmentScopes,
  useSetProjectAssignments,
  useUserScopes,
} from '../hooks/useUserScopes';
import { DepartmentScopeTree } from './DepartmentScopeTree';

interface AccessScopeSectionProps {
  userId: string;
  tenantId: string;
  /** When the membership is REVOKED the section is read-only (view current set). */
  readOnly?: boolean;
}

type Banner = { kind: 'success' | 'error'; text: string } | null;

export function AccessScopeSection({ userId, tenantId, readOnly }: AccessScopeSectionProps) {
  const { t, i18n } = useTranslation();

  const scopesQuery = useUserScopes(userId, tenantId);
  const projectsQuery = useProjects();
  const tenantDepts = useTenantDepartments();

  const setDeptScopes = useSetDepartmentScopes(userId);
  const setProjectAssignments = useSetProjectAssignments(userId);

  const [selectedDeptIds, setSelectedDeptIds] = useState<Set<string>>(new Set());
  const [selectedProjectIds, setSelectedProjectIds] = useState<Set<string>>(new Set());
  const [deptBanner, setDeptBanner] = useState<Banner>(null);
  const [projectBanner, setProjectBanner] = useState<Banner>(null);
  const prefilledRef = useRef(false);

  // Prefill ONCE, when the authoritative server set first arrives. We must NOT
  // re-prefill on every `scopesQuery.data` change: a successful save in one
  // section invalidates+refetches the query, and a blanket re-prefill would
  // wipe the user's unsaved edits in the OTHER section. After a save we instead
  // refresh only the saved section's selection from the mutation result.
  useEffect(() => {
    if (prefilledRef.current || !scopesQuery.data) return;
    prefilledRef.current = true;
    setSelectedDeptIds(new Set(scopesQuery.data.department_ids));
    setSelectedProjectIds(new Set(scopesQuery.data.project_ids));
  }, [scopesQuery.data]);

  const projectItems = projectsQuery.data?.items;

  const sortedProjects = useMemo(
    () =>
      [...(projectItems ?? [])].sort((a, b) =>
        pickLocalized(a.name_i18n, i18n.language).localeCompare(
          pickLocalized(b.name_i18n, i18n.language),
        ),
      ),
    [projectItems, i18n.language],
  );

  if (scopesQuery.isLoading) {
    return (
      <p className="text-sm text-text-secondary" data-testid="scope-loading">
        {t('users.scope.loading')}
      </p>
    );
  }

  if (scopesQuery.error) {
    const err = scopesQuery.error;
    const forbidden = err instanceof ApiError && (err.status === 403 || err.status === 404);
    return (
      <p className="text-sm text-danger-700" data-testid="scope-error">
        {forbidden ? t('users.scope.noAccess') : t('users.scope.loadError')}
      </p>
    );
  }

  const toggleProject = (id: string, checked: boolean) => {
    if (readOnly) return;
    setSelectedProjectIds((prev) => {
      const next = new Set(prev);
      if (checked) next.add(id);
      else next.delete(id);
      return next;
    });
  };

  const apiMessage = (e: unknown, fallbackKey: string): string =>
    e instanceof ApiError && e.message ? e.message : t(fallbackKey);

  const saveDepartments = async () => {
    setDeptBanner(null);
    try {
      const result = await setDeptScopes.mutateAsync({
        tenant_id: tenantId,
        department_ids: [...selectedDeptIds],
      });
      // Re-sync only this section from the authoritative server set.
      setSelectedDeptIds(new Set(result.department_ids));
      setDeptBanner({ kind: 'success', text: t('users.scope.dept.saved') });
    } catch (e) {
      setDeptBanner({ kind: 'error', text: apiMessage(e, 'users.scope.dept.saveError') });
    }
  };

  const saveProjects = async () => {
    setProjectBanner(null);
    try {
      const result = await setProjectAssignments.mutateAsync({
        tenant_id: tenantId,
        project_ids: [...selectedProjectIds],
      });
      // Re-sync only this section from the authoritative server set.
      setSelectedProjectIds(new Set(result.project_ids));
      setProjectBanner({ kind: 'success', text: t('users.scope.project.saved') });
    } catch (e) {
      setProjectBanner({ kind: 'error', text: apiMessage(e, 'users.scope.project.saveError') });
    }
  };

  return (
    <div className="space-y-6" data-testid="access-scope-section">
      <div className="flex items-start gap-2 rounded-md border border-primary-500/20 bg-primary-50 p-3">
        <Info size={16} className="mt-0.5 shrink-0 text-primary-700" aria-hidden />
        <p className="text-xs text-text-secondary">{t('users.scope.intro')}</p>
      </div>

      {/* ---------------- Department scope ---------------- */}
      <section aria-labelledby="dept-scope-heading" className="space-y-2">
        <div className="flex items-center justify-between gap-3">
          <h4
            id="dept-scope-heading"
            className="flex items-center gap-1.5 text-sm font-medium text-text-secondary"
          >
            <ListTree size={15} aria-hidden />
            {t('users.scope.dept.title')}
          </h4>
          {!readOnly ? (
            <Button
              variant="secondary"
              size="sm"
              leadingIcon={<Save size={14} />}
              onClick={saveDepartments}
              disabled={setDeptScopes.isPending}
              data-testid="save-department-scopes"
            >
              {t('users.scope.dept.save')}
            </Button>
          ) : null}
        </div>
        <p className="text-xs text-text-muted">{t('users.scope.dept.help')}</p>

        {deptBanner ? (
          <Banner banner={deptBanner} testId="dept-scope-banner" />
        ) : null}

        <div className="rounded-md border border-border bg-surface max-h-72 overflow-auto p-2">
          {tenantDepts.isLoading ? (
            <p className="px-3 py-6 text-center text-sm text-text-secondary">
              {t('users.scope.loading')}
            </p>
          ) : tenantDepts.isError ? (
            <p className="px-3 py-6 text-center text-sm text-danger-700">
              {t('users.scope.dept.loadError')}
            </p>
          ) : (
            <DepartmentScopeTree
              items={tenantDepts.departments}
              selectedIds={selectedDeptIds}
              onChange={setSelectedDeptIds}
              disabled={readOnly}
            />
          )}
        </div>
      </section>

      {/* ---------------- Project scope ---------------- */}
      <section aria-labelledby="project-scope-heading" className="space-y-2">
        <div className="flex items-center justify-between gap-3">
          <h4
            id="project-scope-heading"
            className="flex items-center gap-1.5 text-sm font-medium text-text-secondary"
          >
            <FolderKanban size={15} aria-hidden />
            {t('users.scope.project.title')}
          </h4>
          {!readOnly ? (
            <Button
              variant="secondary"
              size="sm"
              leadingIcon={<Save size={14} />}
              onClick={saveProjects}
              disabled={setProjectAssignments.isPending}
              data-testid="save-project-assignments"
            >
              {t('users.scope.project.save')}
            </Button>
          ) : null}
        </div>
        <p className="text-xs text-text-muted">{t('users.scope.project.help')}</p>

        {projectBanner ? (
          <Banner banner={projectBanner} testId="project-scope-banner" />
        ) : null}

        <div className="rounded-md border border-border bg-surface max-h-60 overflow-auto p-2">
          {projectsQuery.isLoading ? (
            <p className="px-3 py-6 text-center text-sm text-text-secondary">
              {t('users.scope.loading')}
            </p>
          ) : sortedProjects.length === 0 ? (
            <p className="px-3 py-6 text-center text-sm text-text-secondary">
              {t('users.scope.project.empty')}
            </p>
          ) : (
            <ul className="space-y-0.5">
              {sortedProjects.map((p) => {
                const inputId = `scope-project-${p.id}`;
                const checked = selectedProjectIds.has(p.id);
                return (
                  <li key={p.id}>
                    <div className="flex items-center gap-2 rounded-md px-2 py-1.5 hover:bg-divider/60">
                      <input
                        id={inputId}
                        type="checkbox"
                        className="h-4 w-4 rounded border-border-strong text-primary-500 focus:ring-primary-500 disabled:cursor-not-allowed"
                        checked={checked}
                        disabled={readOnly}
                        onChange={(e) => toggleProject(p.id, e.target.checked)}
                        data-testid={`scope-project-checkbox-${p.code}`}
                      />
                      <label
                        htmlFor={inputId}
                        className="flex min-w-0 cursor-pointer items-center gap-2"
                      >
                        <span className="text-xs font-medium text-text-muted">{p.code}</span>
                        <span className="truncate text-sm">
                          {pickLocalized(p.name_i18n, i18n.language)}
                        </span>
                      </label>
                    </div>
                  </li>
                );
              })}
            </ul>
          )}
        </div>
      </section>
    </div>
  );
}

function Banner({
  banner,
  testId,
}: {
  banner: { kind: 'success' | 'error'; text: string };
  testId: string;
}) {
  const isSuccess = banner.kind === 'success';
  return (
    <div
      role="status"
      data-testid={testId}
      className={
        isSuccess
          ? 'rounded-md border border-success-500/30 bg-success-50 px-3 py-2 text-sm text-success-700'
          : 'rounded-md border border-danger-500/30 bg-danger-50 px-3 py-2 text-sm text-danger-700'
      }
    >
      {banner.text}
    </div>
  );
}
