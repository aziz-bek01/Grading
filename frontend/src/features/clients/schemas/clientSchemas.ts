/**
 * Zod schemas for Client Companies module (F-3).
 *
 * Frontend enforces a friendly first-line of defence; backend remains the
 * authoritative validator. Reasons for destructive actions require ≥ 20
 * characters to match the rest of the codebase (job profile archive,
 * methodology archive, evaluation calibration — see master plan §14).
 */
import { z } from 'zod';
import { localeEnum } from '@/shared/lib/localizedSchema';

const isolationModeEnum = z.enum(['SCHEMA', 'DATABASE']);

/**
 * Create-tenant schema — mirrors backend `CreateTenantRequest.java` exactly:
 *   - slug: ^[a-z][a-z0-9_]{2,63}$ (lowercase, starts with letter, 3-64 chars)
 *   - display_name: required, ≤ 255
 *   - default_locale: one of the 4 supported locales
 *   - isolation_mode: SCHEMA (default) | DATABASE
 *   - company_legal_name: required, ≤ 500
 *   - company_brand_name: optional, ≤ 255
 *   - company_industry: optional, ≤ 100
 * Backend remains the authoritative validator (incl. TENANT_SLUG_TAKEN 409).
 */
export const CreateTenantSchema = z.object({
  slug: z
    .string()
    .trim()
    .min(1, { message: 'validation_slug_required' })
    .regex(/^[a-z][a-z0-9_]{2,63}$/u, { message: 'validation_slug_format' }),
  display_name: z
    .string()
    .trim()
    .min(1, { message: 'validation_display_name_required' })
    .max(255, { message: 'validation_display_name_max' }),
  default_locale: localeEnum,
  isolation_mode: isolationModeEnum,
  company_legal_name: z
    .string()
    .trim()
    .min(1, { message: 'validation_legal_name_required' })
    .max(500, { message: 'validation_legal_name_max' }),
  company_brand_name: z
    .string()
    .trim()
    .max(255, { message: 'validation_brand_name_max' })
    .optional()
    .or(z.literal('')),
  company_industry: z
    .string()
    .trim()
    .max(100, { message: 'validation_industry_max' })
    .optional()
    .or(z.literal('')),
});

export type CreateTenantInput = z.infer<typeof CreateTenantSchema>;

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
