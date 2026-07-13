/**
 * Grade Structure — Zod schemas for create / update / band / reason forms.
 *
 * Primary locale (ru-RU) is required at submit per the PRD
 * "PRIMARY_LOCALE_INCOMPLETE" rejection rule. min_score / max_score honour
 * 4-decimal precision (BigDecimal 18,4 on the backend).
 */
import { z } from 'zod';
import { localizedStringOptional, localizedStringRequiredPrimary } from '@/shared/lib/localizedSchema';

const localizedNameRequiredPrimary = localizedStringRequiredPrimary({ maxLength: 256, passthrough: true });

const localizedDescriptionOptional = localizedStringOptional({ maxLength: 2000, passthrough: true });

export const GradeStructureTypeSchema = z.enum(['GRADE_14', 'GRADE_16', 'CUSTOM']);
export const GradeStructureTemplateCodeSchema = z.enum(['GRADE_14', 'GRADE_16', 'CUSTOM']);
export const GradeGapPolicySchema = z.enum(['STRICT_NO_GAPS', 'ALLOW_GAPS_WARN']);

const codeSchema = z
  .string()
  .trim()
  .min(1, 'validation_required')
  .max(40)
  .regex(/^[A-Z0-9_-]+$/i, 'validation_code_format');

export const GradeStructureCreateFromTemplateSchema = z.object({
  template_code: GradeStructureTemplateCodeSchema,
  project_id: z.string().min(1).nullable().optional(),
  code: codeSchema,
  name: localizedNameRequiredPrimary,
});

export const GradeStructureCreateFromScratchSchema = z.object({
  project_id: z.string().min(1).nullable().optional(),
  code: codeSchema,
  name: localizedNameRequiredPrimary,
  description: localizedDescriptionOptional.optional(),
  structure_type: GradeStructureTypeSchema,
  gap_policy: GradeGapPolicySchema,
});

export const GradeStructureUpdateMetadataSchema = z.object({
  code: codeSchema.optional(),
  name: localizedNameRequiredPrimary.optional(),
  description: localizedDescriptionOptional.optional(),
  gap_policy: GradeGapPolicySchema.optional(),
});

export const AddGradeSchema = z.object({
  grade_number: z.number().int().min(1).max(99),
  name: localizedNameRequiredPrimary,
  description: localizedDescriptionOptional.optional(),
  sort_order: z.number().int().min(0).max(99).optional(),
});

export const UpdateGradeSchema = AddGradeSchema.partial();

const decimal4 = z
  .number({ message: 'validation_number' })
  .finite()
  .min(0, 'validation_non_negative')
  // 4 decimal precision check — backend stores numeric(18,4)
  .refine((v) => Number((v * 10_000) % 1).toFixed(0) === '0', {
    message: 'validation_decimal_precision_4',
  });

export const UpsertBandSchema = z
  .object({
    min_score: decimal4,
    max_score: decimal4,
  })
  .refine((v) => v.min_score <= v.max_score, {
    message: 'validation_band_min_lte_max',
    path: ['min_score'],
  });

export const ArchiveReasonSchema = z.object({
  reason: z
    .string()
    .trim()
    .min(20, 'validation_reason_min_20'),
});

export type GradeStructureCreateFromTemplateInput = z.infer<
  typeof GradeStructureCreateFromTemplateSchema
>;
export type GradeStructureCreateFromScratchInput = z.infer<
  typeof GradeStructureCreateFromScratchSchema
>;
export type GradeStructureUpdateMetadataInput = z.infer<
  typeof GradeStructureUpdateMetadataSchema
>;
export type AddGradeInput = z.infer<typeof AddGradeSchema>;
export type UpdateGradeInput = z.infer<typeof UpdateGradeSchema>;
export type UpsertBandInput = z.infer<typeof UpsertBandSchema>;
export type ArchiveReasonInput = z.infer<typeof ArchiveReasonSchema>;
