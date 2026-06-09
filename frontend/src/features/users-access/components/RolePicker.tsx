/**
 * RolePicker — shared, DATA-DRIVEN role selector for the invite / add-membership
 * (multi-select) and assign-role (single-select) dialogs.
 *
 * Renders from {@link useAssignableRoles} (`GET /roles?assignableOnly=true`) so
 * every assignable role — including future custom roles — appears without code
 * changes. Replaces the three duplicated pickers that read the now-deleted
 * CLIENT_GRANTABLE_ROLES / SUPER_ADMIN_GRANTABLE_ROLES constants.
 *
 * Behavior shared by all three dialogs (de-duplicated here):
 *   - localized role names (4 locales) from `name_i18n`;
 *   - rows the caller CAN grant (`assignable_by_caller=true`) are selectable;
 *   - rows the caller can SEE but NOT grant are disabled with a localized hint
 *     derived from `reason_if_not` (HRLAB_ONLY / PLATFORM_SCOPE);
 *   - loading / empty / error states for the picker itself.
 *
 * Security: the backend remains the source of truth — it re-enforces grant
 * authority on submit regardless of what the UI rendered as enabled.
 */
import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { useAssignableRoles } from '../hooks/useAssignableRoles';
import type { AssignableRole, RoleAssignBlockReason } from '../api/rolesApi';

/** Maps a backend block reason to its localized hint key under `users.rolePicker`. */
function reasonHintKey(reason: RoleAssignBlockReason | null): string | null {
  switch (reason) {
    case 'HRLAB_ONLY':
      return 'users.rolePicker.reasonHrlabOnly';
    case 'PLATFORM_SCOPE':
      return 'users.rolePicker.reasonPlatformScope';
    default:
      return null;
  }
}

interface BaseProps {
  /** Codes already held by the membership — hidden from the picker. */
  excludeCodes?: readonly string[];
  /** data-testid prefix for each option (`${prefix}-${code}`). */
  testIdPrefix: string;
}

interface MultiProps extends BaseProps {
  mode: 'multi';
  /** Currently selected role codes. */
  value: string[];
  onChange: (next: string[]) => void;
}

interface SingleProps extends BaseProps {
  mode: 'single';
  /** Currently selected role code (or '' when none). */
  value: string;
  onChange: (next: string) => void;
  /** Native <select> id for the label's htmlFor. */
  selectId: string;
}

type RolePickerProps = MultiProps | SingleProps;

/**
 * Shared status row (loading / error / empty) rendered while the catalog is not
 * ready or yields no selectable options. Kept inline so all three dialogs show
 * identical, localized states.
 */
function PickerStatus({ kind, prefix }: { kind: 'loading' | 'error' | 'empty'; prefix: string }) {
  const { t } = useTranslation();
  const text =
    kind === 'loading'
      ? t('users.rolePicker.loading')
      : kind === 'error'
        ? t('users.rolePicker.error')
        : t('users.rolePicker.empty');
  return (
    <p
      className={`text-xs mt-1 ${kind === 'error' ? 'text-danger-700' : 'text-text-muted'}`}
      role={kind === 'error' ? 'alert' : undefined}
      data-testid={`${prefix}-status-${kind}`}
    >
      {text}
    </p>
  );
}

export function RolePicker(props: RolePickerProps) {
  const { t } = useTranslation();
  const { data, isLoading, isError } = useAssignableRoles();
  const { excludeCodes, testIdPrefix } = props;

  const roles = useMemo<AssignableRole[]>(() => {
    const excluded = new Set(excludeCodes ?? []);
    return (data ?? []).filter((r) => !excluded.has(r.code));
  }, [data, excludeCodes]);

  if (isLoading) return <PickerStatus kind="loading" prefix={testIdPrefix} />;
  if (isError) return <PickerStatus kind="error" prefix={testIdPrefix} />;
  if (roles.length === 0) return <PickerStatus kind="empty" prefix={testIdPrefix} />;

  if (props.mode === 'single') {
    const { value, onChange, selectId } = props;
    return (
      <div>
        <select
          id={selectId}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          className="mt-1 w-full h-10 px-3 border border-border-strong rounded-md text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500"
          data-testid={selectId}
        >
          {value === '' ? <option value="">{t('users.rolePicker.placeholder')}</option> : null}
          {roles.map((r) => {
            const hintKey = reasonHintKey(r.reasonIfNot);
            const label = r.assignableByCaller || !hintKey ? r.name : `${r.name} — ${t(hintKey)}`;
            return (
              <option key={r.code} value={r.code} disabled={!r.assignableByCaller}>
                {label}
              </option>
            );
          })}
        </select>
      </div>
    );
  }

  const { value, onChange } = props;
  const selected = new Set(value);
  return (
    <div className="grid grid-cols-1 gap-1.5 max-h-44 overflow-y-auto" role="group">
      {roles.map((r) => {
        const hintKey = !r.assignableByCaller ? reasonHintKey(r.reasonIfNot) : null;
        const checked = selected.has(r.code);
        return (
          <label
            key={r.code}
            className={`inline-flex items-center gap-2 text-sm ${
              r.assignableByCaller ? 'text-text-primary' : 'text-text-muted'
            }`}
          >
            <input
              type="checkbox"
              checked={checked}
              disabled={!r.assignableByCaller}
              onChange={(e) => {
                const next = new Set(value);
                if (e.target.checked) next.add(r.code);
                else next.delete(r.code);
                onChange([...next]);
              }}
              className="h-4 w-4 rounded border-border-strong text-primary-500 focus:ring-primary-500 disabled:opacity-50"
              data-testid={`${testIdPrefix}-${r.code}`}
            />
            <span>{r.name}</span>
            {hintKey ? (
              <span className="text-xs text-text-muted" data-testid={`${testIdPrefix}-${r.code}-reason`}>
                — {t(hintKey)}
              </span>
            ) : null}
          </label>
        );
      })}
    </div>
  );
}
