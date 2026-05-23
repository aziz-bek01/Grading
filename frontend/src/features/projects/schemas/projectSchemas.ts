import { z } from 'zod';

const localeKeys = ['ru-RU', 'uz-Cyrl-UZ', 'uz-Latn-UZ', 'en-US'] as const;

/** Localised string: ru-RU is the primary locale and is required. */
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
    name: localizedStringSchema,
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
