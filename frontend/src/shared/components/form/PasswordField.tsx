/**
 * Shared password input: show/hide toggle + "generate strong password"
 * button, label/hint/inline-error slots.
 *
 * Extracted from three byte-for-byte identical blocks in
 * `InviteUserDialog` / `EditUserDialog` / `ResetLoginDialog` (DUP sweep) —
 * only the label/hint/error copy, the required-asterisk, and the
 * `data-testid` values genuinely differed between the three, so those are
 * the only injected props. The markup, classes, and the show/hide +
 * generate wiring are now defined once.
 *
 * Error a11y is wired here ONCE (via the shared {@link fieldA11y} helper) so
 * every caller's password input gets `aria-invalid` / `aria-describedby`
 * pointing at the rendered error `<p>` for free.
 */
import type { UseFormRegisterReturn } from 'react-hook-form';
import { Eye, EyeOff, Wand2 } from 'lucide-react';
import { fieldA11y, fieldErrorId } from '@/shared/lib/fieldA11y';

export interface PasswordFieldProps {
  /** `id` for the `<input>`, also used as its `htmlFor` target. */
  id: string;
  label: string;
  /** Renders a red required-asterisk next to the label. */
  required?: boolean;
  showPassword: boolean;
  onToggleShow: () => void;
  onGenerate: () => void;
  hint?: string;
  /** Inline validation error message (already localized). */
  error?: string;
  /** aria-label used on the toggle button while the password is hidden. */
  showLabel: string;
  /** aria-label used on the toggle button while the password is revealed. */
  hideLabel: string;
  generateLabel: string;
  /** `data-testid` on the `<input>`. */
  testId: string;
  /** `data-testid` on the show/hide toggle button. */
  toggleTestId: string;
  /** `data-testid` on the generate button. */
  generateTestId: string;
  /** Spread from react-hook-form's `register('password')`. */
  registration: UseFormRegisterReturn;
}

export function PasswordField({
  id,
  label,
  required,
  showPassword,
  onToggleShow,
  onGenerate,
  hint,
  error,
  showLabel,
  hideLabel,
  generateLabel,
  testId,
  toggleTestId,
  generateTestId,
  registration,
}: PasswordFieldProps) {
  return (
    <div>
      <label htmlFor={id} className="text-sm font-medium text-text-primary">
        {label} {required ? <span className="text-danger-700">*</span> : null}
      </label>
      <div className="mt-1 flex items-stretch gap-2">
        <div className="relative flex-1">
          <input
            id={id}
            type={showPassword ? 'text' : 'password'}
            autoComplete="new-password"
            {...registration}
            {...fieldA11y(id, !!error)}
            className="w-full h-10 pl-3 pr-10 border border-border-strong rounded-md text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary-500"
            data-testid={testId}
          />
          <button
            type="button"
            onClick={onToggleShow}
            aria-label={showPassword ? hideLabel : showLabel}
            aria-pressed={showPassword}
            className="absolute inset-y-0 right-0 flex items-center px-3 text-text-secondary hover:text-text-primary"
            data-testid={toggleTestId}
          >
            {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
          </button>
        </div>
        <button
          type="button"
          onClick={onGenerate}
          className="inline-flex items-center gap-1.5 h-10 px-3 border border-border-strong rounded-md text-sm text-text-primary bg-surface hover:bg-divider whitespace-nowrap"
          data-testid={generateTestId}
        >
          <Wand2 size={14} />
          {generateLabel}
        </button>
      </div>
      {hint ? <p className="text-xs text-text-muted mt-1">{hint}</p> : null}
      {error ? (
        <p id={fieldErrorId(id)} className="text-xs text-danger-700 mt-1" role="alert">
          {error}
        </p>
      ) : null}
    </div>
  );
}
