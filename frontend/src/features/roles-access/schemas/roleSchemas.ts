/**
 * Client-side validation for the custom-role CREATE form (slice E3).
 *
 * The backend remains the source of truth (it re-validates the code format,
 * enforces RESERVED/EXISTS uniqueness, and re-checks every permission). These
 * rules are UX-only: catch obvious mistakes before the round-trip and give the
 * admin an inline, localized hint.
 *
 * `code` format: UPPER_SNAKE_CASE, must start with a letter, 3–64 chars. This
 * mirrors the system-role code convention (e.g. CLIENT_HR_DIRECTOR) so a custom
 * role reads consistently in pickers and the matrix. The "system kodlari band"
 * (reserved system codes) check is left to the backend (409 ROLE_CODE_RESERVED)
 * since the full reserved set lives server-side.
 *
 * Error messages are i18n KEYS under `roles.create.error.*` — the form resolves
 * them with `t()` so all four locales stay in sync.
 */
import { z } from 'zod';
import { SUPPORTED_LOCALES } from '@/shared/i18n';

/** UPPER_SNAKE_CASE, leading letter, digits/underscores allowed after. */
export const ROLE_CODE_PATTERN = /^[A-Z][A-Z0-9_]{2,63}$/;

/**
 * One localized-name field per supported locale. The PRIMARY locale (ru-RU) is
 * required (every role must have a default label); the others are optional and
 * fall back via `pickLocalized`. Keys are the locale codes.
 */
const nameI18nShape = Object.fromEntries(
  SUPPORTED_LOCALES.map((loc) => [
    loc,
    loc === 'ru-RU'
      ? z.string().trim().min(1, 'roles.create.error.nameRequired')
      : z.string().trim().optional(),
  ]),
) as z.ZodRawShape;

export const CreateRoleSchema = z.object({
  code: z
    .string()
    .trim()
    .min(3, 'roles.create.error.codeFormat')
    .regex(ROLE_CODE_PATTERN, 'roles.create.error.codeFormat'),
  name_i18n: z.object(nameI18nShape),
});

export type CreateRoleInput = z.infer<typeof CreateRoleSchema>;

/** Empty default values for the create form (all locale name fields blank). */
export function emptyCreateRoleValues(): CreateRoleInput {
  const name_i18n = Object.fromEntries(SUPPORTED_LOCALES.map((loc) => [loc, ''])) as Record<
    (typeof SUPPORTED_LOCALES)[number],
    string
  >;
  return { code: '', name_i18n };
}
