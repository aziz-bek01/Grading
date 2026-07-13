import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { ChevronRight, ChevronDown } from 'lucide-react';
import { cn } from '@/shared/lib/cn';
import { pickLocalized } from '@/shared/lib/localized';
import { buildDepartmentTree } from '../lib/tree';
import { departmentTypeIcon } from '../lib/departmentTypeIcon';
import type { Department, DepartmentTreeNode } from '../types/organizationTypes';

interface DepartmentTreeProps {
  items: Department[];
  selectedId: string | null;
  onSelect: (id: string) => void;
  showArchived: boolean;
  query: string;
}

export function DepartmentTree({ items, selectedId, onSelect, showArchived, query }: DepartmentTreeProps) {
  const { t, i18n } = useTranslation();

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return items.filter((d) => {
      if (!showArchived && d.status === 'ARCHIVED') return false;
      if (!q) return true;
      const name = pickLocalized(d.name_i18n, i18n.language).toLowerCase();
      return d.code.toLowerCase().includes(q) || name.includes(q);
    });
  }, [items, showArchived, query, i18n.language]);

  const tree = useMemo(() => buildDepartmentTree(filtered), [filtered]);

  if (tree.length === 0) {
    return (
      <p className="text-sm text-text-secondary px-3 py-6 text-center">
        {showArchived ? t('organization.empty_body') : t('organization.archived_filter_hidden')}
      </p>
    );
  }

  return (
    <ul role="tree" className="text-sm">
      {tree.map((node) => (
        <DepartmentNode
          key={node.id}
          node={node}
          depth={0}
          selectedId={selectedId}
          onSelect={onSelect}
          locale={i18n.language}
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
  locale,
}: {
  node: DepartmentTreeNode;
  depth: number;
  selectedId: string | null;
  onSelect: (id: string) => void;
  locale: string;
}) {
  const [expanded, setExpanded] = useState(depth < 2);
  const hasChildren = node.children.length > 0;
  const isSelected = selectedId === node.id;
  const isArchived = node.status === 'ARCHIVED';

  return (
    <li role="treeitem" aria-expanded={hasChildren ? expanded : undefined} aria-selected={isSelected}>
      <div
        className={cn(
          'flex items-center gap-1 py-1.5 pr-2 rounded-md cursor-pointer hover:bg-divider/60',
          isSelected && 'bg-primary-50 text-primary-700',
          isArchived && 'opacity-60',
        )}
        style={{ paddingLeft: `${depth * 14 + 4}px` }}
        onClick={() => onSelect(node.id)}
      >
        <button
          type="button"
          className="p-0.5 text-text-secondary"
          onClick={(e) => {
            e.stopPropagation();
            if (hasChildren) setExpanded((v) => !v);
          }}
          aria-label={expanded ? 'Collapse' : 'Expand'}
        >
          {hasChildren ? (
            expanded ? <ChevronDown size={14} aria-hidden /> : <ChevronRight size={14} aria-hidden />
          ) : (
            <span className="inline-block w-3.5" aria-hidden />
          )}
        </button>
        <span aria-hidden className="text-text-secondary">{departmentTypeIcon[node.type]}</span>
        <span className="font-medium text-xs text-text-muted">{node.code}</span>
        <span className="truncate">{pickLocalized(node.name_i18n, locale)}</span>
      </div>
      {hasChildren && expanded ? (
        <ul role="group">
          {node.children.map((child) => (
            <DepartmentNode
              key={child.id}
              node={child}
              depth={depth + 1}
              selectedId={selectedId}
              onSelect={onSelect}
              locale={locale}
            />
          ))}
        </ul>
      ) : null}
    </li>
  );
}
