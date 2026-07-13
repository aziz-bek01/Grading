/**
 * Factor level — Zod schema for create / update.
 *
 * `points` and `scale_value` are both stored; the active scoring mode
 * decides which one drives the total. UI surfaces only the relevant field.
 */
import { z } from 'zod';
import { localizedStringOptional, localizedStringRequiredPrimary } from '@/shared/lib/localizedSchema';

const localizedLabelRequiredPrimary = localizedStringRequiredPrimary({ maxLength: 256, passthrough: true });

const localizedDescriptionOptional = localizedStringOptional({ maxLength: 2000, passthrough: true });

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
