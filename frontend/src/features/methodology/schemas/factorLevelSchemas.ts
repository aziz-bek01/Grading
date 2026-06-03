/**
 * Factor level — Zod schema for create / update.
 *
 * `points` and `scale_value` are both stored; the active scoring mode
 * decides which one drives the total. UI surfaces only the relevant field.
 */
import { z } from 'zod';

const localizedLabelRequiredPrimary = z
  .object({
    'ru-RU': z.string().trim().min(1, 'validation_primary_required').max(256),
    'uz-Cyrl-UZ': z.string().trim().max(256).optional(),
    'uz-Latn-UZ': z.string().trim().max(256).optional(),
    'en-US': z.string().trim().max(256).optional(),
  })
  .passthrough();

const localizedDescriptionOptional = z
  .object({
    'ru-RU': z.string().trim().max(2000).optional(),
    'uz-Cyrl-UZ': z.string().trim().max(2000).optional(),
    'uz-Latn-UZ': z.string().trim().max(2000).optional(),
    'en-US': z.string().trim().max(2000).optional(),
  })
  .passthrough();

export const FactorLevelCreateSchema = z.object({
  code: z
    .string()
    .trim()
    .min(1, 'validation_required')
    .max(20)
    .regex(/^[A-Z0-9_-]+$/i, 'validation_code_format'),
  level_order: z.number().int().min(0).optional(),
  points: z
    .number({ message: 'validation_required' })
    .min(0, 'validation_points_min')
    .max(10_000, 'validation_points_max'),
  scale_value: z
    .number({ message: 'validation_required' })
    .min(0, 'validation_points_min')
    .max(10_000, 'validation_points_max'),
  /** Backend snake_case contract — POST body fields. */
  label_i18n: localizedLabelRequiredPrimary,
  description_i18n: localizedDescriptionOptional.optional(),
});

export const FactorLevelUpdateSchema = FactorLevelCreateSchema.partial();

export type FactorLevelCreateInput = z.infer<typeof FactorLevelCreateSchema>;
export type FactorLevelUpdateInput = z.infer<typeof FactorLevelUpdateSchema>;
