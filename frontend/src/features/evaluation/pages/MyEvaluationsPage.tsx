import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ChevronDown, ChevronRight } from 'lucide-react';
import { Card } from '@/shared/components/ui/Card';
import { Breadcrumbs } from '@/shared/components/layout/Breadcrumbs';
import { EmptyState } from '@/shared/components/feedback/EmptyState';
import { ErrorState } from '@/shared/components/feedback/ErrorState';
import { NoAccessState } from '@/shared/components/feedback/NoAccessState';
import { PERMISSIONS } from '@/shared/types/permissions';
import { usePermission } from '@/features/auth/usePermission';
import { useAuthStore } from '@/features/auth/authStore';
import { ApiError } from '@/shared/api/apiError';
import { routes } from '@/shared/config/routes';
import { pickLocalized } from '@/shared/lib/localized';
import { cn } from '@/shared/lib/cn';
import { useMyEvaluations } from '../hooks/useMyEvaluations';
import { EvaluationStatusBadge } from '../components/EvaluationStatusBadge';
import { ProgressChip } from '../components/byFactor/ProgressChip';
import type { MyEvaluationRow } from '../api/myEvaluationApi';
import type { EvaluationStatus } from '../types';

/**
 * Feature 1 — evaluator self-inbox ("My evaluations").
 *
 * PART 4 — rows are grouped by methodology. Each group is collapsible,
 * default expanded, with per-group collapse state persisted in localStorage
 * keyed by tenant + project + methodologyId. A filter bar (methodology,
 * status, position search) narrows the visible rows in-memory (AND logic).
 *
 * The inbox is project-agnostic on the wire, but the scoring route is
 * project-scoped — each row carries its OWN project id, so we deep-link via
 * {@link routes.projectEvaluation} (`?mode=by-factor`) using `row.projectId`.
 */
export function MyEvaluationsPage() {
  const { t, i18n } = useTranslation();
  const { can } = usePermission();
  const canRead = can(PERMISSIONS.EVALUATION_READ);

  const activeTenant = useAuthStore((s) => s.activeTenant);
  const activeProject = useAuthStore((s) => s.activeProject);

  const query = useMyEvaluations();
  const rows = useMemo(() => query.data ?? [], [query.data]);

  // ----- Filter state -----
  const [filterMethodologyId, setFilterMethodologyId] = useState<string>('');
  const [filterStatus, setFilterStatus] = useState<string>('');
  const [filterSearch, setFilterSearch] = useState<string>('');

  // ----- Collapse state — persisted in localStorage -----
  const tenantId = activeTenant?.id ?? '';
  const projectId = activeProject?.id ?? '';

  const makeCollapseKey = (methodologyId: string) =>
    `my_evals_collapsed:${tenantId}:${projectId}:${methodologyId}`;

  const [collapsedGroups, setCollapsedGroups] = useState<Record<string, boolean>>({});

  const isGroupCollapsed = (methodologyId: string): boolean => {
    if (methodologyId in collapsedGroups) return collapsedGroups[methodologyId];
    try {
      return localStorage.getItem(makeCollapseKey(methodologyId)) === 'true';
    } catch {
      return false; // default: expanded
    }
  };

  const toggleGroup = (methodologyId: string) => {
    const next = !isGroupCollapsed(methodologyId);
    try {
      localStorage.setItem(makeCollapseKey(methodologyId), String(next));
    } catch {
      // localStorage may be blocked (private browsing); ignore
    }
    setCollapsedGroups((prev) => ({ ...prev, [methodologyId]: next }));
  };

  // ----- Group rows by methodologyId -----
  // Rows without a methodologyId are grouped under a sentinel "__none__" key
  // so they are still displayed without breaking the grouping logic.
  const NONE_KEY = '__none__';

  type MethodologyGroup = {
    methodologyId: string;
    methodologyName: string;
    versionLabel: string;
    rows: MyEvaluationRow[];
  };

  const allGroups = useMemo((): MethodologyGroup[] => {
    const map = new Map<string, MyEvaluationRow[]>();
    for (const row of rows) {
      const key = row.methodologyId ?? NONE_KEY;
      if (!map.has(key)) map.set(key, []);
      map.get(key)!.push(row);
    }
    return Array.from(map.entries()).map(([methodologyId, groupRows]) => {
      const sample = groupRows[0];
      const rawName = sample?.methodologyName ?? null;
      const methodologyName = rawName
        ? pickLocalized(rawName, i18n.language, methodologyId)
        : methodologyId;
      const vNum = sample?.methodologyActiveVersionNumber;
      const versionLabel =
        vNum != null
          ? `v${vNum}`
          : t('my_evaluations.group.version_draft');
      return { methodologyId, methodologyName, versionLabel, rows: groupRows };
    });
  }, [rows, i18n.language, t]);

  // ----- Build filter options from actual data -----
  const methodologyFilterOptions = useMemo(() => {
    const seen = new Set<string>();
    const opts: { id: string; label: string }[] = [];
    for (const g of allGroups) {
      if (!seen.has(g.methodologyId)) {
        seen.add(g.methodologyId);
        opts.push({ id: g.methodologyId, label: g.methodologyName });
      }
    }
    return opts;
  }, [allGroups]);

  const statusFilterOptions: EvaluationStatus[] = [
    'DRAFT', 'INCOMPLETE', 'COMPLETE', 'SUBMITTED', 'APPROVED', 'LOCKED', 'ARCHIVED',
  ];

  // ----- Apply in-memory filters -----
  const filteredGroups = useMemo(() => {
    const search = filterSearch.trim().toLowerCase();
    return allGroups
      .map((group) => {
        let groupRows = group.rows;

        if (filterMethodologyId && group.methodologyId !== filterMethodologyId) {
          groupRows = [];
        } else {
          if (filterStatus) {
            groupRows = groupRows.filter((r) => r.status === filterStatus);
          }
          if (search) {
            groupRows = groupRows.filter(
              (r) =>
                r.positionCode.toLowerCase().includes(search) ||
                r.title.toLowerCase().includes(search),
            );
          }
        }
        return { ...group, rows: groupRows };
      })
      .filter((g) => g.rows.length > 0);
  }, [allGroups, filterMethodologyId, filterStatus, filterSearch]);

  const hasActiveFilter = !!filterMethodologyId || !!filterStatus || !!filterSearch;
  const totalFilteredRows = filteredGroups.reduce((acc, g) => acc + g.rows.length, 0);

  // Statuses considered "completed/scored" for the group completion chip.
  const SCORED_STATUSES = new Set<EvaluationStatus>(['SUBMITTED', 'APPROVED', 'LOCKED']);

  // Early return AFTER all hooks — Rules of Hooks compliance.
  if (!canRead) return <NoAccessState />;

  return (
    <div className="space-y-6" data-testid="my-evaluations-page">
      <Breadcrumbs extra={[{ label: t('nav.my_evaluations') }]} />
      <header>
        <h1 className="text-2xl text-text-primary">{t('my_evaluations.title')}</h1>
        <p className="text-sm text-text-secondary mt-1">
          {t('my_evaluations.subtitle')}
        </p>
      </header>

      <Card>
        {query.isLoading ? (
          <MyEvaluationsSkeleton />
        ) : query.error ? (
          <ErrorState
            correlationId={
              query.error instanceof ApiError ? query.error.correlationId : undefined
            }
            onRetry={() => query.refetch()}
          />
        ) : rows.length === 0 ? (
          <EmptyState
            title={t('my_evaluations.empty_title')}
            body={t('my_evaluations.empty_body')}
          />
        ) : (
          <div className="space-y-4">
            {/* Filter bar — always visible while data is loaded */}
            <div
              className="flex flex-wrap items-center gap-2 px-1 pt-1"
              data-testid="my-evaluations-filter-bar"
            >
              {/* Methodology filter */}
              <select
                aria-label={t('evaluation.filter.methodology')}
                value={filterMethodologyId}
                onChange={(e) => setFilterMethodologyId(e.target.value)}
                data-testid="my-evaluations-filter-methodology"
                className="h-9 px-3 border border-border-strong rounded-md text-sm bg-surface"
              >
                <option value="">
                  {t('evaluation.filter.methodology')}: {t('common.all')}
                </option>
                {methodologyFilterOptions.map((opt) => (
                  <option key={opt.id} value={opt.id}>
                    {opt.label}
                  </option>
                ))}
              </select>

              {/* Status filter */}
              <select
                aria-label={t('evaluation.filter.status')}
                value={filterStatus}
                onChange={(e) => setFilterStatus(e.target.value)}
                data-testid="my-evaluations-filter-status"
                className="h-9 px-3 border border-border-strong rounded-md text-sm bg-surface"
              >
                <option value="">
                  {t('evaluation.filter.status')}: {t('common.all')}
                </option>
                {statusFilterOptions.map((s) => (
                  <option key={s} value={s}>
                    {t(`evaluation.status.${s.toLowerCase()}`)}
                  </option>
                ))}
              </select>

              {/* Position search */}
              <input
                type="search"
                value={filterSearch}
                onChange={(e) => setFilterSearch(e.target.value)}
                placeholder={t('evaluation.filter.position_search')}
                aria-label={t('evaluation.filter.position_search')}
                data-testid="my-evaluations-filter-search"
                className="h-9 px-3 border border-border-strong rounded-md text-sm bg-surface flex-1 min-w-[180px] max-w-xs"
              />

              {hasActiveFilter && (
                <button
                  type="button"
                  onClick={() => {
                    setFilterMethodologyId('');
                    setFilterStatus('');
                    setFilterSearch('');
                  }}
                  className="text-sm text-primary-600 hover:underline"
                  data-testid="my-evaluations-filter-clear"
                >
                  {t('common.reset_filters')}
                </button>
              )}
            </div>

            {/* No-match state: filter bar stays visible, groups go away */}
            {totalFilteredRows === 0 && hasActiveFilter ? (
              <EmptyState
                title={t('my_evaluations.no_match_title')}
                body={t('my_evaluations.no_match_body')}
                action={
                  <button
                    type="button"
                    onClick={() => {
                      setFilterMethodologyId('');
                      setFilterStatus('');
                      setFilterSearch('');
                    }}
                    className="text-sm text-primary-600 hover:underline"
                  >
                    {t('my_evaluations.no_match_clear')}
                  </button>
                }
              />
            ) : (
              <div
                className="divide-y divide-divider"
                data-testid="my-evaluations-list"
              >
                {filteredGroups.map((group) => {
                  const scored = group.rows.filter((r) =>
                    SCORED_STATUSES.has(r.status),
                  ).length;
                  const total = group.rows.length;
                  const collapsed = isGroupCollapsed(group.methodologyId);

                  return (
                    <div key={group.methodologyId}>
                      {/* Group header */}
                      <button
                        type="button"
                        aria-expanded={!collapsed}
                        aria-controls={`group-rows-${group.methodologyId}`}
                        onClick={() => toggleGroup(group.methodologyId)}
                        className="w-full flex items-center gap-2 px-1 py-2.5 text-left hover:bg-divider/40 rounded-md"
                        data-testid={`my-evaluations-group-${group.methodologyId}`}
                      >
                        <span className="text-text-muted">
                          {collapsed ? (
                            <ChevronRight size={16} aria-hidden />
                          ) : (
                            <ChevronDown size={16} aria-hidden />
                          )}
                        </span>
                        <span className="text-sm font-semibold text-text-primary flex-1 min-w-0 truncate">
                          {group.methodologyName}
                        </span>
                        <span className="text-xs text-text-secondary tabular-nums shrink-0">
                          {group.versionLabel}
                        </span>
                        {/* Completion chip: scored/total */}
                        <span
                          className={cn(
                            'inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium shrink-0',
                            scored === total && total > 0
                              ? 'bg-success-100 text-success-700'
                              : 'bg-divider text-text-secondary',
                          )}
                          aria-label={t('my_evaluations.group.completion_aria', {
                            scored,
                            total,
                          })}
                        >
                          {t('my_evaluations.group.completion', { scored, total })}
                        </span>
                      </button>

                      {/* Group rows */}
                      {!collapsed && (
                        <ul
                          id={`group-rows-${group.methodologyId}`}
                          className="divide-y divide-divider pl-6"
                        >
                          {group.rows.map((row) => {
                            const inner = (
                              <>
                                <div className="min-w-0 flex-1">
                                  <p className="text-sm font-medium text-text-primary truncate">
                                    {row.title}
                                  </p>
                                  <p className="text-xs text-text-muted">{row.positionCode}</p>
                                </div>
                                <div className="flex items-center gap-3 shrink-0">
                                  <ProgressChip
                                    filled={row.filledFactorsCount}
                                    total={row.totalFactorsCount}
                                  />
                                  <EvaluationStatusBadge status={row.status} />
                                </div>
                              </>
                            );

                            // ALL evaluators score in the by-factor K-sheet.
                            const target = `${routes.projectEvaluation(row.projectId)}?mode=by-factor`;

                            return (
                              <li key={row.evaluationId}>
                                {row.projectId ? (
                                  <Link
                                    to={target}
                                    data-testid={`open-my-evaluation-${row.evaluationId}`}
                                    className="flex items-center gap-4 px-1 py-3 hover:bg-divider/50 rounded-md"
                                  >
                                    {inner}
                                  </Link>
                                ) : (
                                  <div
                                    data-testid={`my-evaluation-row-${row.evaluationId}`}
                                    className="flex items-center gap-4 px-1 py-3 text-text-muted"
                                    aria-disabled
                                  >
                                    {inner}
                                  </div>
                                )}
                              </li>
                            );
                          })}
                        </ul>
                      )}
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        )}
      </Card>
    </div>
  );
}

/** Loading skeleton — two group placeholder headers with 2 rows each. */
function MyEvaluationsSkeleton() {
  return (
    <div className="divide-y divide-divider" data-testid="my-evaluations-skeleton">
      {[0, 1].map((gi) => (
        <div key={gi} className="py-2">
          {/* Group header skeleton */}
          <div className="flex items-center gap-2 px-1 py-2.5 animate-pulse">
            <div className="h-4 w-4 rounded bg-divider" />
            <div className="h-4 w-40 rounded bg-divider flex-1" />
            <div className="h-4 w-12 rounded bg-divider" />
            <div className="h-5 w-12 rounded-full bg-divider" />
          </div>
          {/* Row skeletons */}
          {[0, 1].map((ri) => (
            <div
              key={ri}
              className="flex items-center gap-4 px-1 py-3 pl-6 animate-pulse"
            >
              <div className="min-w-0 flex-1 space-y-2">
                <div className="h-4 w-48 rounded bg-divider" />
                <div className="h-3 w-24 rounded bg-divider" />
              </div>
              <div className="h-6 w-16 rounded-full bg-divider" />
              <div className="h-6 w-20 rounded-full bg-divider" />
            </div>
          ))}
        </div>
      ))}
    </div>
  );
}
