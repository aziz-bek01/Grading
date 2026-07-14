/**
 * Checkbox department TREE for the Access-scope section (slice E4-S1).
 *
 * REUSE, not reimplementation:
 *   - data comes from the same org-structure source the app renders elsewhere
 *     (see `useTenantDepartments`, which fans out to the org feature's
 *     `fetchDepartmentTree`);
 *   - the hierarchy is built with the org feature's shared `buildDepartmentTree`
 *     + `collectDescendantIds` (org `lib/tree.ts`);
 *   - node visuals (type icon, code + localized name, expand/collapse, archived
 *     dimming) mirror the read-only `DepartmentTree` so the two look identical.
 *
 * Behaviour: selecting a node selects its whole subtree (mirrors the backend
 * "parent implies subtree" semantics). Deselecting a node deselects its subtree.
 */
import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { ChevronRight, ChevronDown } from 'lucide-react';
import { cn } from '@/shared/lib/cn';
import { pickLocalized } from '@/shared/lib/localized';
import { buildDepartmentTree, collectDescendantIds } from '@/features/organization/lib/tree';
import { departmentTypeIcon } from '@/features/organization/lib/departmentTypeIcon';
import type {
  Department,
  DepartmentTreeNode,
} from '@/features/organization/types/organizationTypes';

interface DepartmentScopeTreeProps {
  items: Department[];
  /** Currently-selected department ids (controlled). */
  selectedIds: Set<string>;
  onChange: (next: Set<string>) => void;
  disabled?: boolean;
}

export function DepartmentScopeTree({
  items,
  selectedIds,
  onChange,
  disabled,
}: DepartmentScopeTreeProps) {
  const { t, i18n } = useTranslation();

  // Skip archived rows — an archived department cannot meaningfully scope work.
  const active = useMemo(() => items.filter((d) => d.status !== 'ARCHIVED'), [items]);
  const tree = useMemo(() => buildDepartmentTree(active), [active]);

  const toggle = (node: DepartmentTreeNode, checked: boolean) => {
    if (disabled) return;
    const subtree = collectDescendantIds(active, node.id);
    const next = new Set(selectedIds);
    for (const id of subtree) {
      if (checked) next.add(id);
      else next.delete(id);
    }
    onChange(next);
  };

  if (tree.length === 0) {
    return (
      <p className="text-sm text-text-secondary px-3 py-6 text-center">
        {t('users.scope.dept.empty')}
      </p>
    );
  }

  return (
    <ul role="tree" className="text-sm">
      {tree.map((node) => (
        <DepartmentScopeNode
          key={node.id}
          node={node}
          depth={0}
          selectedIds={selectedIds}
          onToggle={toggle}
          locale={i18n.language}
          disabled={disabled}
        />
      ))}
    </ul>
  );
}

function DepartmentScopeNode({
  node,
  depth,
  selectedIds,
  onToggle,
  locale,
  disabled,
}: {
  node: DepartmentTreeNode;
  depth: number;
  selectedIds: Set<string>;
  onToggle: (node: DepartmentTreeNode, checked: boolean) => void;
  locale: string;
  disabled?: boolean;
}) {
  const { t } = useTranslation();
  const [expanded, setExpanded] = useState(depth < 2);
  const hasChildren = node.children.length > 0;
  const isChecked = selectedIds.has(node.id);
  const isArchived = node.status === 'ARCHIVED';
  const inputId = `scope-dept-${node.id}`;

  return (
    <li role="treeitem" aria-expanded={hasChildren ? expanded : undefined} aria-selected={isChecked}>
      <div
        className={cn(
          'flex items-center gap-1.5 py-1.5 pr-2 rounded-md hover:bg-divider/60',
          isChecked && 'bg-primary-50',
          isArchived && 'opacity-60',
        )}
        style={{ paddingLeft: `${depth * 14 + 4}px` }}
      >
        <button
          type="button"
          className="p-0.5 text-text-secondary"
          onClick={() => hasChildren && setExpanded((v) => !v)}
          aria-label={expanded ? t('common.collapse') : t('common.expand')}
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
        <input
          id={inputId}
          type="checkbox"
          className="h-4 w-4 rounded border-border-strong text-primary-500 focus:ring-primary-500 disabled:cursor-not-allowed"
          checked={isChecked}
          disabled={disabled}
          onChange={(e) => onToggle(node, e.target.checked)}
          data-testid={`scope-dept-checkbox-${node.code}`}
        />
        <label
          htmlFor={inputId}
          className={cn(
            'flex items-center gap-1.5 min-w-0 cursor-pointer',
            disabled && 'cursor-not-allowed',
          )}
        >
          <span aria-hidden className="text-text-secondary">
            {departmentTypeIcon[node.type]}
          </span>
          <span className="font-medium text-xs text-text-muted">{node.code}</span>
          <span className="truncate">{pickLocalized(node.name_i18n, locale)}</span>
        </label>
      </div>
      {hasChildren && expanded ? (
        <ul role="group">
          {node.children.map((child) => (
            <DepartmentScopeNode
              key={child.id}
              node={child}
              depth={depth + 1}
              selectedIds={selectedIds}
              onToggle={onToggle}
              locale={locale}
              disabled={disabled}
            />
          ))}
        </ul>
      ) : null}
    </li>
  );
}
