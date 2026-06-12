import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { useUsers } from '@/features/users-access/hooks/useUsers';

interface Props {
  /** Native <select> id (also data-testid). */
  selectId: string;
  value: string;
  onChange: (userId: string, userName: string | null) => void;
  /** User ids already chosen in OTHER rows — hidden so one person is one seat. */
  excludeUserIds?: readonly string[];
  disabled?: boolean;
}

/**
 * People picker for a single evaluator seat. DATA-DRIVEN from the active
 * tenant's user list (`useUsers` — tenant derived from JWT, never sent). Reuses
 * the same fetcher the users-access module uses; the dialog passes the resolved
 * full_name back so the role chip can show a name before the BE resolves it.
 *
 * The server re-validates membership on assign; this is a UI convenience only.
 */
export function EvaluatorPicker({
  selectId,
  value,
  onChange,
  excludeUserIds,
  disabled,
}: Props) {
  const { t } = useTranslation();
  const { data, isLoading, isError } = useUsers();

  const users = useMemo(() => {
    const excluded = new Set(excludeUserIds ?? []);
    // Keep the currently-selected user visible even if excluded elsewhere is
    // impossible (a user can't be in two rows) — straightforward filter.
    return (data?.items ?? []).filter(
      (u) => u.status === 'ACTIVE' && (!excluded.has(u.id) || u.id === value),
    );
  }, [data, excludeUserIds, value]);

  return (
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
  );
}
