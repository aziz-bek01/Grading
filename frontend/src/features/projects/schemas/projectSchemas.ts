import { z } from 'zod';

const localeKeys = ['ru-RU', 'uz-Cyrl-UZ', 'uz-Latn-UZ', 'en-US'] as const;

/**
 * Localised string: ru-RU is the primary locale and is required.
 *
 * NOTE: kept intentionally distinct from the shared
 * `localizedStringAnyRequired` / `localizedStringRequiredPrimary` builders in
 * `@/shared/lib/localizedSchema` (DUP sweep) — this is the only call site
 * where `ru-RU` is a genuinely REQUIRED (non-optional) field with the
 * default Zod message on `.min(1)`, layered under an "any locale" `.refine`.
 * position/department's "any required" schemas make `ru-RU` optional; the
 * methodology/factor/grade-structure/job-profile "required primary" schemas
 * pass a custom message to `.min(1)`. Forcing this one into either shared
 * builder would change the validation error text — left as-is.
 */
const localizedStringSchema = z
  .object({
    'ru-RU': z.string().trim().min(1).max(120),
    'uz-Cyrl-UZ': z.string().trim().max(120).optional(),
    'uz-Latn-UZ': z.string().trim().max(120).optional(),
    'en-US': z.string().trim().max(120).optional(),
  })
  .refine((v) => localeKeys.some((k) => v[k] && v[k]!.trim().length > 0), {
    message: 'validation_primary_name_required',
  });

export const ProjectCreateSchema = z
  .object({
    code: z
      .string()
      .trim()
      .min(2)
      .max(32)
      .regex(/^[A-Z0-9][A-Z0-9-]*$/u, 'code'),
    /** Backend snake_case contract — POST body field. */
    name_i18n: localizedStringSchema,
    description: z.string().trim().max(2000).optional().or(z.literal('')),
    start_date: z.string().optional().or(z.literal('')),
    end_date: z.string().optional().or(z.literal('')),
  })
  .refine(
    (v) => {
      if (!v.start_date || !v.end_date) return true;
      return v.end_date >= v.start_date;
    },
    { message: 'validation_dates', path: ['end_date'] },
  );

export type ProjectCreateInput = z.infer<typeof ProjectCreateSchema>;
