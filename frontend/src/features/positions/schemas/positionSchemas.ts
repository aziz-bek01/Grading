import { z } from 'zod';

const localeKeys = ['ru-RU', 'uz-Cyrl-UZ', 'uz-Latn-UZ', 'en-US'] as const;

const localizedTitleSchema = z
  .object({
    'ru-RU': z.string().trim().max(180).optional(),
    'uz-Cyrl-UZ': z.string().trim().max(180).optional(),
    'uz-Latn-UZ': z.string().trim().max(180).optional(),
    'en-US': z.string().trim().max(180).optional(),
  })
  .refine((v) => localeKeys.some((k) => v[k] && v[k]!.trim().length > 0), {
    message: 'validation_title_required',
  });

export const PositionCreateSchema = z.object({
  project_id: z.string().min(1),
  department_id: z.string().min(1, 'validation_department_required'),
  code: z
    .string()
    .trim()
    .min(2)
    .max(48)
    .regex(/^[A-Z0-9][A-Z0-9-]*$/u, 'code'),
  /** Backend snake_case contract — POST body field. */
  title_i18n: localizedTitleSchema,
  function: z.string().trim().max(120).optional().or(z.literal('')),
  category: z.string().trim().max(120).optional().or(z.literal('')),
  job_family: z.string().trim().max(120).optional().or(z.literal('')),
  job_level: z.string().trim().max(32).optional().or(z.literal('')),
});

export type PositionCreateInput = z.infer<typeof PositionCreateSchema>;
