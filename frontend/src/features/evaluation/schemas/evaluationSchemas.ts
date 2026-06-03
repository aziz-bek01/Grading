/**
 * Evaluation — Zod schemas mirroring backend validation.
 *
 * Reason min length (20 chars) matches `CalibrateScoreRequest.reason` and
 * `ReasonRequest.reason` server-side annotations.
 */
import { z } from 'zod';

const uuid = z.string().min(1, 'validation_required');

export const CreateEvaluationSchema = z.object({
  position_id: uuid,
  methodology_version_id: uuid,
  evaluator_user_id: z.string().min(1).optional().nullable(),
});

export const UpsertScoreSchema = z.object({
  factor_id: uuid,
  factor_level_id: uuid,
  comment: z.string().trim().max(1000).optional().nullable(),
});

export const CalibrateScoreSchema = z.object({
  factor_id: uuid,
  /**
   * Backend uses BigDecimal with @DecimalMin(0). Frontend ships a JS
   * number; values are coerced from string inputs by RHF.
   */
  new_raw_factor_score: z
    .number()
    .nonnegative('validation_non_negative')
    .max(100_000),
  reason: z
    .string()
    .trim()
    .min(20, 'validation_reason_min_length')
    .max(4000),
});

export const ArchiveEvaluationSchema = z.object({
  reason: z
    .string()
    .trim()
    .min(20, 'validation_reason_min_length')
    .max(4000),
});

export type CreateEvaluationInput = z.infer<typeof CreateEvaluationSchema>;
export type UpsertScoreInput = z.infer<typeof UpsertScoreSchema>;
export type CalibrateScoreInput = z.infer<typeof CalibrateScoreSchema>;
export type ArchiveEvaluationInput = z.infer<typeof ArchiveEvaluationSchema>;
