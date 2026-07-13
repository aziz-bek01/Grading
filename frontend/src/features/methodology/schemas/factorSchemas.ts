/**
 * Factor — Zod schema for create / update.
 *
 * weight: accepts a number — BigNumber arithmetic is server-side; the form
 * is constrained to 4 decimals (per design-foundation §13 weight tolerance).
 *
 * `validation_primary_required` is i18n-keyed in the form layer.
 */
import { z } from 'zod';
import { localizedStringOptional, localizedStringRequiredPrimary } from '@/shared/lib/localizedSchema';

const localizedNameRequiredPrimary = localizedStringRequiredPrimary({ maxLength: 256, passthrough: true });

const localizedDescriptionOptional = localizedStringOptional({ maxLength: 2000, passthrough: true });

export const FactorCreateSchema = z.object({
  code: z
    .string()
    .trim()
    .min(1, 'validation_required')
    .max(40)
    .regex(/^[A-Z0-9_-]+$/i, 'validation_code_format'),
  /** Backend snake_case contract — POST body fields. */
  name_i18n: localizedNameRequiredPrimary,
  description_i18n: localizedDescriptionOptional.optional(),
  weight: z
    .number({ message: 'validation_required' })
    .min(0, 'validation_weight_min')
    .max(100, 'validation_weight_max'),
  max_points: z
    .number({ message: 'validation_required' })
    .min(0, 'validation_max_points_min')
    .max(1000, 'validation_max_points_max'),
  sort_order: z.number().int().min(0).optional(),
  required: z.boolean().default(true),
});

export const FactorUpdateSchema = FactorCreateSchema.partial();

export type FactorCreateInput = z.infer<typeof FactorCreateSchema>;
export type FactorUpdateInput = z.infer<typeof FactorUpdateSchema>;
