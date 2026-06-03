import { z } from 'zod';

const localeKeys = ['ru-RU', 'uz-Cyrl-UZ', 'uz-Latn-UZ', 'en-US'] as const;

const localizedStringSchema = z
  .object({
    'ru-RU': z.string().trim().max(180).optional(),
    'uz-Cyrl-UZ': z.string().trim().max(180).optional(),
    'uz-Latn-UZ': z.string().trim().max(180).optional(),
    'en-US': z.string().trim().max(180).optional(),
  })
  .refine((v) => localeKeys.some((k) => v[k] && v[k]!.trim().length > 0), {
    message: 'validation_primary_name_required',
  });

export const DepartmentCreateSchema = z.object({
  project_id: z.string().uuid().or(z.string().min(1)),
  parent_id: z.string().uuid().or(z.string().min(1)).nullable(),
  code: z
    .string()
    .trim()
    .min(1)
    .max(32)
    .regex(/^[A-Z0-9][A-Z0-9-]*$/u, 'code'),
  /** Backend snake_case contract — POST body field. */
  name_i18n: localizedStringSchema,
  type: z.enum(['BRANCH', 'DEPARTMENT', 'DIVISION', 'UNIT']),
});

export type DepartmentCreateInput = z.infer<typeof DepartmentCreateSchema>;
