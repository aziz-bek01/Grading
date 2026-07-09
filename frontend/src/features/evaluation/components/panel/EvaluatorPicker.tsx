import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { useAllUsers } from '@/features/users-access/hooks/useUsers';

interface BaseProps {
  disabled?: boolean;
}

interface SingleProps extends BaseProps {
  mode?: 'single';
  /** Native <select> id (also data-testid). */
  selectId: string;
  value: string;
  onChange: (userId: string, userName: string | null) => void;
  /** User ids already chosen in OTHER rows — hidden so one person is one seat. */
  excludeUserIds?: readonly string[];
}

interface MultiProps extends BaseProps {
  mode: 'multi';
  /** data-testid prefix for each checkbox option (`${prefix}-${userId}`). */
  testIdPrefix: string;
  /** Currently selected evaluator user ids. */
  value: string[];
  onChange: (next: string[]) => void;
  excludeUserIds?: readonly string[];
}

type Props = SingleProps | MultiProps;

/**
 * People picker for evaluator(s). DATA-DRIVEN from the active tenant's FULL
 * user list (`useAllUsers` — tenant derived from JWT, never sent; pages
 * through every user via the shared `fetchAllPages` helper so the 21st+
 * active user is still selectable — see EPIC-013). Reuses the same fetcher
 * the users-access module uses.
 *
 * `mode="single"` (default, backward-compatible) renders the original
 * single-seat `<select>` used by the panel roster rows; the server
 * re-validates membership on assign, this is a UI convenience only.
 *
 * `mode="multi"` renders a checkbox group returning `string[]` — mirrors
 * `RolePicker`'s existing `mode="multi"` variant (used by
 * `AddMembershipDialog.tsx`). Used by the EVALUATION_SUMMARY report filter
 * panel; no separate "EvaluatorMultiPicker" component is created.
 */
export function EvaluatorPicker(props: Props) {
  const { t } = useTranslation();
  const { data, isLoading, isError } = useAllUsers();
  const { excludeUserIds, disabled } = props;

  const users = useMemo(() => {
    const excluded = new Set(excludeUserIds ?? []);
    const selectedSingle = props.mode === 'multi' ? null : props.value;
    return (data?.items ?? []).filter(
      (u) => u.status === 'ACTIVE' && (!excluded.has(u.id) || u.id === selectedSingle),
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [data, excludeUserIds, props.mode === 'multi' ? null : props.value]);

  if (props.mode === 'multi') {
    const { value, onChange, testIdPrefix } = props;
    const selected = new Set(value);

    if (isLoading) {
      return (
        <p className="text-xs text-text-muted" data-testid={`${testIdPrefix}-status-loading`}>
          {t('common.loading')}
        </p>
      );
    }
    if (isError) {
      return (
        <p className="text-xs text-danger-700" role="alert" data-testid={`${testIdPrefix}-status-error`}>
          {t('panel.dialog.evaluator_load_error')}
        </p>
      );
    }
    if (users.length === 0) {
      return (
        <p className="text-xs text-text-muted" data-testid={`${testIdPrefix}-status-empty`}>
          {t('panel.dialog.evaluator_placeholder')}
        </p>
      );
    }

    return (
      <div>
        <div className="grid grid-cols-1 gap-1.5 max-h-44 overflow-y-auto" role="group">
          {users.map((u) => {
            const checked = selected.has(u.id);
            return (
              <label key={u.id} className="inline-flex items-center gap-2 text-sm text-text-primary">
                <input
                  type="checkbox"
                  checked={checked}
                  disabled={disabled}
                  onChange={(e) => {
                    const next = new Set(value);
                    if (e.target.checked) next.add(u.id);
                    else next.delete(u.id);
                    onChange([...next]);
                  }}
                  className="h-4 w-4 rounded border-border-strong text-primary-500 focus:ring-primary-500 disabled:opacity-50"
                  data-testid={`${testIdPrefix}-${u.id}`}
                />
                <span>{u.full_name}</span>
              </label>
            );
          })}
        </div>
        {/* Never silent: the shared fetchAllPages helper hit its safety cap
            before exhausting the tenant's users — say so instead of quietly
            offering an incomplete roster. */}
        {data?.truncated ? (
          <p
            className="mt-1 text-xs text-warning-700"
            role="status"
            data-testid={`${testIdPrefix}-truncated`}
          >
            {t('dataTable.results_truncated', {
              shown: data.items.length,
              total: data.totalElements,
            })}
          </p>
        ) : null}
      </div>
    );
  }

  const { selectId, value, onChange } = props;
  return (
    <div>
      <select
        id={selectId}
        data-testid={selectId}
        value={value}
        disabled={disabled || isLoading || isError}
        onChange={(e) => {
          const id = e.target.value;
          const name = users.find((u) => u.id === id)?.full_name ?? null;
          onChange(id, name);
        }}
        className="w-full h-9 px-3 border border-border-strong rounded-md text-sm bg-surface disabled:opacity-50"
      >
        <option value="">
          {isLoading
            ? t('common.loading')
            : isError
              ? t('panel.dialog.evaluator_load_error')
              : t('panel.dialog.evaluator_placeholder')}
        </option>
        {users.map((u) => (
          <option key={u.id} value={u.id}>
            {u.full_name}
          </option>
        ))}
      </select>
      {data?.truncated ? (
        <p
          className="mt-1 text-xs text-warning-700"
          role="status"
          data-testid={`${selectId}-truncated`}
        >
          {t('dataTable.results_truncated', {
            shown: data.items.length,
            total: data.totalElements,
          })}
        </p>
      ) : null}
    </div>
  );
}
