import { z } from 'zod';

const localeEnum = z.enum(['ru-RU', 'uz-Cyrl-UZ', 'uz-Latn-UZ', 'en-US']);

const ROLE_CODES = [
  'HRLAB_SUPER_ADMIN',
  'HRLAB_PROJECT_MANAGER',
  'HRLAB_CONSULTANT',
  'HRLAB_ANALYST',
  'CLIENT_ADMIN',
  'CLIENT_HR_DIRECTOR',
  'CLIENT_HR_SPECIALIST',
  'CLIENT_COMMITTEE_MEMBER',
  'CLIENT_DEPARTMENT_MANAGER',
  'CLIENT_VIEWER',
  'AUDITOR',
] as const;

const roleEnum = z.enum(ROLE_CODES);

/**
 * Whitelist of roles that may be granted by non-super-admin client admins.
 * Frontend renders the multi-select from this list when the active user is
 * NOT HRLAB_SUPER_ADMIN. Backend remains source of truth (it will reject
 * elevated role grants regardless of what the UI sent).
 */
export const CLIENT_GRANTABLE_ROLES = [
  'CLIENT_ADMIN',
  'CLIENT_HR_DIRECTOR',
  'CLIENT_HR_SPECIALIST',
  'CLIENT_COMMITTEE_MEMBER',
  'CLIENT_DEPARTMENT_MANAGER',
  'CLIENT_VIEWER',
  'AUDITOR',
] as const;

export const SUPER_ADMIN_GRANTABLE_ROLES = ROLE_CODES;

export const InviteUserSchema = z.object({
  email: z.string().trim().email({ message: 'validation_email' }),
  full_name: z.string().trim().min(2, { message: 'validation_fullname_min' }).max(200, {
    message: 'validation_fullname_max',
  }),
  locale: localeEnum,
  /**
   * Only sent by super-admins; the form omits it when irrelevant.
   * Loose validation (any non-empty string) — backend re-checks the UUID
   * shape AND enforces super-admin authority before honouring this field.
   */
  tenant_id: z.string().min(1).optional(),
  role_codes: z.array(roleEnum).min(1, { message: 'validation_roles_min' }),
});

export type InviteUserInput = z.infer<typeof InviteUserSchema>;

export const UpdateUserSchema = z.object({
  full_name: z.string().trim().min(2).max(200).optional(),
  locale: localeEnum.optional(),
  status: z.enum(['ACTIVE', 'INVITED', 'REVOKED', 'SUSPENDED']).optional(),
});

export type UpdateUserInput = z.infer<typeof UpdateUserSchema>;

export const AddRoleSchema = z.object({
  role_code: roleEnum,
});

export type AddRoleInput = z.infer<typeof AddRoleSchema>;

/** Salary-permission toggle requires a meaningful reason (audit trail). */
export const SalaryPermissionSchema = z.object({
  enabled: z.boolean(),
  reason: z
    .string()
    .trim()
    .min(10, { message: 'validation_reason_min' })
    .max(500, { message: 'validation_reason_max' }),
});

export type SalaryPermissionInput = z.infer<typeof SalaryPermissionSchema>;
