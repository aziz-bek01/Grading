/**
 * Zod schemas for Client Companies module (F-3).
 *
 * Frontend enforces a friendly first-line of defence; backend remains the
 * authoritative validator. Reasons for destructive actions require ≥ 20
 * characters to match the rest of the codebase (job profile archive,
 * methodology archive, evaluation calibration — see master plan §14).
 */
import { z } from 'zod';

const localeEnum = z.enum(['ru-RU', 'uz-Cyrl-UZ', 'uz-Latn-UZ', 'en-US']);

export const UpdateTenantSchema = z.object({
  display_name: z
    .string()
    .trim()
    .min(2, { message: 'validation_display_name_min' })
    .max(200, { message: 'validation_display_name_max' })
    .optional(),
  default_locale: localeEnum.optional(),
});

export type UpdateTenantInput = z.infer<typeof UpdateTenantSchema>;

export const UpdateClientCompanySchema = z.object({
  legal_name: z
    .string()
    .trim()
    .min(2, { message: 'validation_legal_name_min' })
    .max(300, { message: 'validation_legal_name_max' })
    .optional(),
  brand_name: z
    .string()
    .trim()
    .min(2, { message: 'validation_brand_name_min' })
    .max(200, { message: 'validation_brand_name_max' })
    .optional(),
  industry: z.string().trim().max(120).nullable().optional(),
  /** ISO 3166-1 alpha-2 (UZ / RU / KZ / …). 2 letters or null/empty. */
  country_code: z
    .string()
    .trim()
    .regex(/^[A-Za-z]{2}$/u, { message: 'validation_country_code' })
    .nullable()
    .optional()
    .or(z.literal('')),
  tax_id: z.string().trim().max(40).nullable().optional(),
});

export type UpdateClientCompanyInput = z.infer<typeof UpdateClientCompanySchema>;

export const ArchiveTenantSchema = z.object({
  reason: z
    .string()
    .trim()
    .min(20, { message: 'validation_reason_min' })
    .max(500, { message: 'validation_reason_max' }),
});

export type ArchiveTenantInput = z.infer<typeof ArchiveTenantSchema>;
