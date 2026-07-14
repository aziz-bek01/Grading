/**
 * Shared form-field error accessibility helper.
 *
 * Pairs a react-hook-form control with its inline validation-error message so
 * assistive tech announces the error and associates it with the field:
 *
 *   <input id="foo" {...register('foo')} {...fieldA11y('foo', !!errors.foo)} />
 *   {errors.foo ? (
 *     <p id={fieldErrorId('foo')} role="alert">{...}</p>
 *   ) : null}
 *
 * `fieldA11y` only returns `aria-invalid` / `aria-describedby` when the field
 * actually has an error, so a clean field never carries `aria-invalid="true"`.
 * `fieldErrorId` is the single source of truth for the `${id}-error` naming
 * convention, so the id on the control's `aria-describedby` always matches
 * the id on the rendered error `<p>` — no per-form string duplication.
 */

/** The `id` the error message element must carry for a given field id. */
export function fieldErrorId(id: string): string {
  return `${id}-error`;
}

/** Attributes to spread onto a form control (`<input>`/`<select>`/`<textarea>`). */
export function fieldA11y(
  id: string,
  hasError: boolean,
): { 'aria-invalid'?: true; 'aria-describedby'?: string } {
  if (!hasError) return {};
  return { 'aria-invalid': true, 'aria-describedby': fieldErrorId(id) };
}
