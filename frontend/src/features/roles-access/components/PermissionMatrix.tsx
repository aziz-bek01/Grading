/**
 * PermissionMatrix — the per-role grant/revoke editor (slice E2).
 *
 * Renders the FULL permission catalog grouped by `resource` (module) as a
 * checkbox matrix. Behavior:
 *   - `restricted` rows are LOCKED: rendered disabled with a lock hint and a
 *     fixed checked state mirroring `granted`. They are never togglable and are
 *     never part of the saved replace-set (the page filters them out).
 *   - when `readOnly` (matrix not editable by caller) ALL checkboxes are
 *     disabled; the page shows a banner above.
 *   - a per-module header checkbox toggles every NON-restricted, non-readonly
 *     row in that group at once (indeterminate when partially selected).
 *
 * Controlled: the parent owns the set of currently-checked codes and the dirty
 * tracking. This component is pure render + onToggle, which keeps it trivially
 * testable and lets the page diff against the server snapshot.
 *
 * Security: this is UX only. The backend re-enforces restricted + caller-held
 * permissions on PUT regardless of what the UI rendered as enabled.
 */
import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { Lock } from 'lucide-react';
import { cn } from '@/shared/lib/cn';
import type { RolePermissionItem } from '@/features/users-access/api/rolesApi';

interface PermissionMatrixProps {
  items: RolePermissionItem[];
  /** Codes currently checked (granted in the working copy). */
  selected: ReadonlySet<string>;
  /** When true the whole matrix is read-only (no toggles). */
  readOnly: boolean;
  /** Toggle a single non-restricted code. */
  onToggle: (code: string, next: boolean) => void;
  /** Bulk-set every non-restricted code in a module. */
  onToggleGroup: (codes: string[], next: boolean) => void;
}

interface ResourceGroup {
  resource: string;
  rows: RolePermissionItem[];
}

function groupByResource(items: RolePermissionItem[]): ResourceGroup[] {
  const map = new Map<string, RolePermissionItem[]>();
  for (const it of items) {
    const list = map.get(it.resource) ?? [];
    list.push(it);
    map.set(it.resource, list);
  }
  return [...map.entries()]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([resource, rows]) => ({
      resource,
      rows: [...rows].sort((a, b) => a.action.localeCompare(b.action)),
    }));
}

export function PermissionMatrix({
  items,
  selected,
  readOnly,
  onToggle,
  onToggleGroup,
}: PermissionMatrixProps) {
  const { t } = useTranslation();
  const groups = useMemo(() => groupByResource(items), [items]);

  if (groups.length === 0) {
    return (
      <p className="text-sm text-text-muted py-8 text-center" data-testid="permission-matrix-empty">
        {t('roles.matrix.empty')}
      </p>
    );
  }

  return (
    <div className="space-y-4" role="group" aria-label={t('roles.matrix.ariaLabel')}>
      {groups.map((group) => {
        // Toggling logic only considers rows the caller could actually change.
        const togglable = readOnly ? [] : group.rows.filter((r) => !r.restricted);
        const togglableCodes = togglable.map((r) => r.code);
        const checkedCount = togglable.filter((r) => selected.has(r.code)).length;
        const allChecked = togglable.length > 0 && checkedCount === togglable.length;
        const someChecked = checkedCount > 0 && !allChecked;

        return (
          <section
            key={group.resource}
            className="border border-border rounded-lg overflow-hidden"
            data-testid={`permission-group-${group.resource}`}
          >
            <header className="flex items-center justify-between gap-3 bg-locked-bg px-4 py-2.5 border-b border-border">
              <h3 className="text-sm font-semibold text-text-primary">
                {t(`roles.resource.${group.resource}`, { defaultValue: group.resource })}
              </h3>
              <label
                className={cn(
                  'inline-flex items-center gap-2 text-xs',
                  readOnly || togglable.length === 0
                    ? 'text-text-muted'
                    : 'text-text-secondary cursor-pointer',
                )}
              >
                <input
                  type="checkbox"
                  className="h-4 w-4 rounded border-border-strong text-primary-500 focus:ring-primary-500 disabled:opacity-50"
                  checked={allChecked}
                  ref={(el) => {
                    if (el) el.indeterminate = someChecked;
                  }}
                  disabled={readOnly || togglable.length === 0}
                  onChange={(e) => onToggleGroup(togglableCodes, e.target.checked)}
                  data-testid={`permission-group-toggle-${group.resource}`}
                  aria-label={t('roles.matrix.selectAllInModule')}
                />
                <span>{t('roles.matrix.selectAll')}</span>
              </label>
            </header>

            <ul className="divide-y divide-divider">
              {group.rows.map((row) => {
                const checked = row.restricted ? row.granted : selected.has(row.code);
                const disabled = readOnly || row.restricted;
                return (
                  <li key={row.code}>
                    <label
                      className={cn(
                        'flex items-center gap-3 px-4 py-2.5 text-sm',
                        disabled ? 'cursor-not-allowed' : 'cursor-pointer hover:bg-divider/40',
                      )}
                    >
                      <input
                        type="checkbox"
                        className="h-4 w-4 rounded border-border-strong text-primary-500 focus:ring-primary-500 disabled:opacity-50"
                        checked={checked}
                        disabled={disabled}
                        onChange={(e) => onToggle(row.code, e.target.checked)}
                        data-testid={`permission-checkbox-${row.code}`}
                      />
                      <span
                        className={cn(
                          'flex-1',
                          disabled ? 'text-text-muted' : 'text-text-primary',
                        )}
                      >
                        {t(`roles.permission.${row.code}`, { defaultValue: row.code })}
                      </span>
                      <code className="text-xs text-text-muted font-mono">{row.code}</code>
                      {row.restricted ? (
                        <span
                          className="inline-flex items-center gap-1 text-xs text-locked"
                          title={t('roles.matrix.restrictedHint')}
                          data-testid={`permission-restricted-${row.code}`}
                        >
                          <Lock size={12} aria-hidden />
                          <span>{t('roles.matrix.restricted')}</span>
                        </span>
                      ) : null}
                    </label>
                  </li>
                );
              })}
            </ul>
          </section>
        );
      })}
    </div>
  );
}
