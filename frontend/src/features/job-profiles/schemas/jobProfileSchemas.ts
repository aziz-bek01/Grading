import { z } from 'zod';
import { localizedStringOptional, localizedStringRequiredPrimary } from '@/shared/lib/localizedSchema';
import { JOB_PROFILE_FIELDS } from '../types';

/**
 * Multilingual long-text field: primary locale required for submit; other
 * locales optional. Matches design-foundation §11 (Uzbek length budget) and
 * PRD MVP1-E6-1 "primary locale required" rule.
 */
const localizedLongTextRequiredPrimary = localizedStringRequiredPrimary({ maxLength: 4000, passthrough: true });

const localizedLongTextOptional = localizedStringOptional({ maxLength: 4000, passthrough: true });

/** Used during DRAFT auto-save: every field is optional. */
export const JobProfileDraftSchema = z
  .object(
    Object.fromEntries(
      JOB_PROFILE_FIELDS.map((k) => [k, localizedLongTextOptional]),
    ) as Record<string, typeof localizedLongTextOptional>,
  )
  .extend({
    actualization_date: z.string().optional().or(z.literal('')),
  });

/** Submit-time validation: every long-text field requires primary locale. */
export const JobProfileSubmitSchema = z
  .object(
    Object.fromEntries(
      JOB_PROFILE_FIELDS.map((k) => [k, localizedLongTextRequiredPrimary]),
    ) as Record<string, typeof localizedLongTextRequiredPrimary>,
  )
  .extend({
    actualization_date: z.string().optional().or(z.literal('')),
  });

export type JobProfileDraftInput = z.infer<typeof JobProfileDraftSchema>;
export type JobProfileSubmitInput = z.infer<typeof JobProfileSubmitSchema>;
