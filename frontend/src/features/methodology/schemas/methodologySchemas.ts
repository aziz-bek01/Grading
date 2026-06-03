/**
 * Methodology — Zod schemas for create / update forms.
 *
 * Primary locale (ru-RU) is required at submit per PRD MVP1-E11-3
 * "PRIMARY_LOCALE_INCOMPLETE" rejection rule.
 */
import { z } from 'zod';

const localizedNameRequiredPrimary = z
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

export const MethodologyTypeSchema = z.enum(['CLASSIC_8_FACTOR', 'EXTENDED_11_CRITERIA', 'CUSTOM']);
export const ScoringModeSchema = z.enum(['DIRECT_POINTS', 'WEIGHTED_POINTS', 'WEIGHTED_SCALE']);

export const MethodologyCreateSchema = z.object({
  project_id: z.string().min(1),
  code: z
    .string()
    .trim()
    .min(1, 'validation_required')
    .max(40)
    .regex(/^[A-Z0-9_-]+$/i, 'validation_code_format'),
  /** Backend snake_case contract — POST body fields. */
  name_i18n: localizedNameRequiredPrimary,
  description_i18n: localizedDescriptionOptional.optional(),
  methodology_type: MethodologyTypeSchema,
  scoring_mode: ScoringModeSchema.optional(),
  target_total_points: z.number().positive().max(10_000).optional(),
  source_template_code: z.enum(['CLASSIC_8_FACTOR', 'EXTENDED_11_CRITERIA']).nullable().optional(),
});

export const MethodologyUpdateSchema = z.object({
  code: z.string().trim().min(1).max(40).optional(),
  name_i18n: localizedNameRequiredPrimary.optional(),
  description_i18n: localizedDescriptionOptional.optional(),
});

export type MethodologyCreateInput = z.infer<typeof MethodologyCreateSchema>;
export type MethodologyUpdateInput = z.infer<typeof MethodologyUpdateSchema>;
