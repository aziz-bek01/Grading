/**
 * Searchable SINGLE-SELECT department tree (shared org component).
 *
 * REUSE, not a fork of {@link DepartmentScopeTree} (the multi-select checkbox
 * variant in users-access): both build the hierarchy with the org feature's
 * shared `buildDepartmentTree` and render identical node visuals (type icon,
 * code + localized name, expand/collapse, archived dimming). The behavioural
 * difference is the selection model — here exactly ONE department is the
 * selected node (radio semantics), which is what the dept-first panel wizard
 * Step 1 needs. The two trees share the tree util so there is no duplicated
 * hierarchy logic.
 *
 * Per-node coverage badges (position count + already-paneled count) are passed
 * in via `countsOf` so the host (which already loads positions + panels) owns
 * the data; the tree stays a pure presentation component. A department whose
 * every position is already paneled is marked "fully paneled".
 */
import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  ChevronRight,
  ChevronDown,
  Building2,
  Network as Sitemap,
  Layers,
  Users,
} from 'lucide-react';
import { cn } from '@/shared/lib/cn';
import { pickLocalized } from '@/shared/lib/localized';
import { buildDepartmentTree } from '../lib/tree';
import type {
  Department,
  DepartmentTreeNode,
  DepartmentType,
} from '../types/organizationTypes';

const typeIcon: Record<DepartmentType, React.ReactNode> = {
  BRANCH: <Building2 size={14} aria-hidden />,
  DEPARTMENT: <Sitemap size={14} aria-hidden />,
  DIVISION: <Layers size={14} aria-hidden />,
  UNIT: <Users size={14} aria-hidden />,
};

/** Coverage counts for one department row (host-supplied, BE-authoritative). */
export interface DepartmentCoverage {
  /** ACTIVE positions DIRECTLY in the department (server-authoritative — T4). */
  positionCount: number;
  /** Positions already covered by an active panel for the chosen version. */
  paneledCount: number;
  /**
   * T4 (PD-1) — self + all descendants. When it exceeds `positionCount` the row
   * renders a secondary "↳ N" figure so a parent that only holds descendants is
   * not mistaken for empty. Optional: omit to hide the subtree figure.
   */
  subtreeCount?: number;
}

interface DepartmentSingleSelectTreeProps {
  items: Department[];
  /** Currently-selected department id (controlled; null = none). */
  selectedId: string | null;
  onSelect: (id: string) => void;
  /** Returns coverage counts for a department; undefined hides the badges. */
  countsOf?: (departmentId: string) => DepartmentCoverage | undefined;
  /** Free-text query (host-controlled so the host can also clear it). */
  search?: string;
  disabled?: boolean;
}

/**
 * Does the node or any descendant match the query? Keeps a parent visible when a
 * child matches so the path to a deep match stays navigable.
 */
function matchesQuery(
  node: DepartmentTreeNode,
  q: string,
  locale: string,
): boolean {
  if (!q) return true;
  const name = pickLocalized(node.name_i18n, locale).toLowerCase();
  if (name.includes(q) || node.code.toLowerCase().includes(q)) return true;
  return node.children.some((c) => matchesQuery(c, q, locale));
}

export function DepartmentSingleSelectTree({
  items,
  selectedId,
  onSelect,
  countsOf,
  search = '',
  disabled,
}: DepartmentSingleSelectTreeProps) {
  const { t, i18n } = useTranslation();

  // Skip archived rows — an archived department cannot host a new commission.
  const active = useMemo(
    () => items.filter((d) => d.status !== 'ARCHIVED'),
    [items],
  );
  const tree = useMemo(() => buildDepartmentTree(active), [active]);
  const q = search.trim().toLowerCase();

  const visibleRoots = useMemo(
    () => tree.filter((n) => matchesQuery(n, q, i18n.language)),
    [tree, q, i18n.language],
  );

  if (tree.length === 0) {
    return (
      <p
        className="text-sm text-text-secondary px-3 py-6 text-center"
        data-testid="dept-tree-empty"
      >
        {t('panel.wizard.dept.empty')}
      </p>
    );
  }

  if (visibleRoots.length === 0) {
    return (
      <p
        className="text-sm text-text-secondary px-3 py-6 text-center"
        data-testid="dept-tree-no-match"
      >
        {t('panel.wizard.dept.no_match')}
      </p>
    );
  }

  return (
    <ul role="tree" className="text-sm" data-testid="dept-single-select-tree">
      {visibleRoots.map((node) => (
        <DepartmentNode
          key={node.id}
          node={node}
          depth={0}
          selectedId={selectedId}
          onSelect={onSelect}
          countsOf={countsOf}
          query={q}
          locale={i18n.language}
          disabled={disabled}
        />
      ))}
    </ul>
  );
}

function DepartmentNode({
  node,
  depth,
  selectedId,
  onSelect,
  countsOf,
  query,
  locale,
  disabled,
}: {
  node: DepartmentTreeNode;
  depth: number;
  selectedId: string | null;
  onSelect: (id: string) => void;
  countsOf?: (departmentId: string) => DepartmentCoverage | undefined;
  query: string;
  locale: string;
  disabled?: boolean;
}) {
  const { t } = useTranslation();
  const [expanded, setExpanded] = useState(depth < 2);
  const visibleChildren = node.children.filter((c) =>
    matchesQuery(c, query, locale),
  );
  const hasChildren = visibleChildren.length > 0;
  const isSelected = selectedId === node.id;
  const isArchived = node.status === 'ARCHIVED';
  const coverage = countsOf?.(node.id);
  const fullyPaneled =
    coverage != null &&
    coverage.positionCount > 0 &&
    coverage.paneledCount >= coverage.positionCount;

  return (
    <li
      role="treeitem"
      aria-expanded={hasChildren ? expanded : undefined}
      aria-selected={isSelected}
    >
      <div
        className={cn(
          'flex items-center gap-1.5 py-1.5 pr-2 rounded-md hover:bg-divider/60',
          isSelected && 'bg-primary-50',
          isArchived && 'opacity-60',
        )}
        style={{ paddingLeft: `${depth * 14 + 4}px` }}
      >
        <button
          type="button"
          className="p-0.5 text-text-secondary"
          onClick={() => hasChildren && setExpanded((v) => !v)}
          aria-label={expanded ? t('common.close') : t('common.continue')}
        >
          {hasChildren ? (
            expanded ? (
              <ChevronDown size={14} aria-hidden />
            ) : (
              <ChevronRight size={14} aria-hidden />
            )
          ) : (
            <span className="inline-block w-3.5" aria-hidden />
          )}
        </button>
        <button
          type="button"
          disabled={disabled}
          onClick={() => onSelect(node.id)}
          data-testid={`dept-select-${node.code}`}
          aria-pressed={isSelected}
          className={cn(
            'flex flex-1 items-center gap-1.5 min-w-0 text-left rounded-md px-1 py-0.5',
            'disabled:cursor-not-allowed',
            isSelected && 'font-medium',
          )}
        >
          <span aria-hidden className="text-text-secondary">
            {typeIcon[node.type]}
          </span>
          <span className="font-medium text-xs text-text-muted">{node.code}</span>
          <span className="truncate">{pickLocalized(node.name_i18n, locale)}</span>
          {coverage != null ? (
            <span
              className="ml-auto flex shrink-0 items-center gap-1.5 text-xs"
              data-testid={`dept-coverage-${node.code}`}
            >
              <span
                className="rounded-full bg-divider px-1.5 py-0.5 text-text-secondary tabular-nums"
                title={t('panel.wizard.dept.position_count', {
                  count: coverage.positionCount,
                })}
              >
                {t('panel.wizard.dept.position_count', {
                  count: coverage.positionCount,
                })}
              </span>
              {/* T4 (PD-1) — secondary subtree roll-up "↳ N" so a parent that
                  only holds descendants reads correctly. Show only when the
                  subtree adds positions beyond the direct count. */}
              {coverage.subtreeCount != null &&
              coverage.subtreeCount > coverage.positionCount ? (
                <span
                  className="text-text-muted tabular-nums"
                  data-testid={`dept-subtree-${node.code}`}
                  title={t('panel.wizard.dept.subtree_tooltip', {
                    count: coverage.subtreeCount,
                  })}
                >
                  {t('panel.wizard.dept.subtree_count', {
                    count: coverage.subtreeCount,
                  })}
                </span>
              ) : null}
              {coverage.paneledCount > 0 ? (
                <span
                  className={cn(
                    'rounded-full px-1.5 py-0.5 tabular-nums',
                    fullyPaneled
                      ? 'bg-success-50 text-success-700'
                      : 'bg-primary-50 text-primary-700',
                  )}
                  data-testid={`dept-paneled-${node.code}`}
                >
                  {fullyPaneled
                    ? t('panel.wizard.dept.fully_paneled')
                    : t('panel.wizard.dept.paneled_count', {
                        count: coverage.paneledCount,
                      })}
                </span>
              ) : null}
            </span>
          ) : null}
        </button>
      </div>
      {hasChildren && expanded ? (
        <ul role="group">
          {visibleChildren.map((child) => (
            <DepartmentNode
              key={child.id}
              node={child}
              depth={depth + 1}
              selectedId={selectedId}
              onSelect={onSelect}
              countsOf={countsOf}
              query={query}
              locale={locale}
              disabled={disabled}
            />
          ))}
        </ul>
      ) : null}
    </li>
  );
}
