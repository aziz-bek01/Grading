import { z } from 'zod';

const localeEnum = z.enum(['ru-RU', 'uz-Cyrl-UZ', 'uz-Latn-UZ', 'en-US']);

/**
 * Canonical role catalog — MUST mirror the backend `roles` table seeded in
 * `backend/.../seeds/002-default-roles.yaml`. Divergence here causes the
 * invite / assign-role endpoints to reject the payload (400) in production.
 */
export const ROLE_CODES = [
  'HRLAB_SUPER_ADMIN',
  'HRLAB_PROJECT_MANAGER',
  'HRLAB_CONSULTANT',
  'HRLAB_ANALYST',
  'CLIENT_COMPANY_ADMIN',
  'CLIENT_HR_DIRECTOR',
  'CLIENT_HR_SPECIALIST',
  'EVALUATION_COMMITTEE_MEMBER',
  'DEPARTMENT_MANAGER',
  'VIEWER',
  'EXTERNAL_AUDITOR',
] as const;

const roleEnum = z.enum(ROLE_CODES);

/**
 * Whitelist of roles that may be granted by non-super-admin client admins.
 * CLIENT_COMPANY_ADMIN holds USER_ROLE_ASSIGN (tenant-scope) but NOT
 * USER_ROLE_ASSIGN_HRLAB, so it may grant any TENANT-scope role but no
 * HRLAB_* platform role (see seed 004-default-role-permissions.yaml).
 * Frontend renders the multi-select from this list when the active user is
 * NOT HRLAB_SUPER_ADMIN. Backend remains source of truth (it will reject
 * elevated role grants regardless of what the UI sent).
 */
export const CLIENT_GRANTABLE_ROLES = [
  'CLIENT_COMPANY_ADMIN',
  'CLIENT_HR_DIRECTOR',
  'CLIENT_HR_SPECIALIST',
  'EVALUATION_COMMITTEE_MEMBER',
  'DEPARTMENT_MANAGER',
  'VIEWER',
  'EXTERNAL_AUDITOR',
] as const;

export const SUPER_ADMIN_GRANTABLE_ROLES = ROLE_CODES;

/**
 * Initial login password issued by the inviting admin.
 *
 * Mirrors the backend (and Zitadel default) complexity policy enforced when
 * the server's IdP is enabled: ≥ 8 chars with at least one uppercase, one
 * lowercase, one number and one symbol. A weaker password is rejected by the
 * backend with 400 `USER_INVITE_WEAK_PASSWORD`; we validate client-side first
 * so the admin gets immediate, localised feedback.
 *
 * Single error key (`validation_password_complexity`) covers all rules so the
 * inline message stays short and translatable.
 */
export const PASSWORD_COMPLEXITY = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{8,}$/;

export const passwordField = z
  .string()
  .regex(PASSWORD_COMPLEXITY, { message: 'validation_password_complexity' });

/**
 * Generates a compliant random password the admin can hand to the new user.
 *
 * Guarantees at least one char from each required class (upper / lower /
 * number / symbol), length ≥ 12, and shuffles so the required chars are not
 * positionally predictable. Uses `crypto.getRandomValues` when available and
 * falls back to `Math.random` (e.g. older test envs) — the fallback is only a
 * convenience generator, never a security boundary (the backend re-validates).
 */
export function generateStrongPassword(length = 16): string {
  const lower = 'abcdefghijkmnopqrstuvwxyz';
  const upper = 'ABCDEFGHJKLMNPQRSTUVWXYZ';
  const digits = '23456789';
  const symbols = '!@#$%^&*?-_=+';
  const all = lower + upper + digits + symbols;
  const size = Math.max(12, length);

  const randInt = (max: number): number => {
    if (typeof crypto !== 'undefined' && typeof crypto.getRandomValues === 'function') {
      const buf = new Uint32Array(1);
      crypto.getRandomValues(buf);
      return buf[0] % max;
    }
    return Math.floor(Math.random() * max);
  };
  const pick = (alphabet: string): string => alphabet[randInt(alphabet.length)];

  // Seed one of each required class, then fill the rest from the full set.
  const chars: string[] = [pick(lower), pick(upper), pick(digits), pick(symbols)];
  while (chars.length < size) chars.push(pick(all));

  // Fisher–Yates shuffle so the guaranteed chars are not in fixed positions.
  for (let i = chars.length - 1; i > 0; i -= 1) {
    const j = randInt(i + 1);
    [chars[i], chars[j]] = [chars[j], chars[i]];
  }
  return chars.join('');
}

export const InviteUserSchema = z.object({
  email: z.string().trim().email({ message: 'validation_email' }),
  full_name: z.string().trim().min(2, { message: 'validation_fullname_min' }).max(200, {
    message: 'validation_fullname_max',
  }),
  locale: localeEnum,
  /**
   * Initial login password the admin issues to the new user (the backend
   * provisions the Zitadel account from it). Required when the IdP is enabled;
   * the form always collects it and the backend is the final authority.
   */
  password: passwordField,
  /**
   * Only sent by super-admins; the form omits it when irrelevant.
   * Loose validation (any non-empty string) — backend re-checks the UUID
   * shape AND enforces super-admin authority before honouring this field.
   */
  tenant_id: z.string().min(1).optional(),
  role_codes: z.array(roleEnum).min(1, { message: 'validation_roles_min' }),
});

export type InviteUserInput = z.infer<typeof InviteUserSchema>;

/**
 * PATCH /users/:id body. The backend status field accepts ONLY `ACTIVE` or
 * `DISABLED` (any other value → 400 `USER_PATCH_BAD_STATUS`); INVITED and
 * LOCKED are system-managed and cannot be set via this endpoint. We mirror that
 * constraint here so the Edit-user / Disable-user UI can never submit an
 * illegal status.
 */
export const EDITABLE_USER_STATUSES = ['ACTIVE', 'DISABLED'] as const;

export const UpdateUserSchema = z.object({
  full_name: z
    .string()
    .trim()
    .min(2, { message: 'validation_fullname_min' })
    .max(200, { message: 'validation_fullname_max' })
    .optional(),
  locale: localeEnum.optional(),
  status: z.enum(EDITABLE_USER_STATUSES).optional(),
});

export type UpdateUserInput = z.infer<typeof UpdateUserSchema>;

/**
 * Membership-creation form (POST /users/:id/memberships). `tenant_id` is the
 * explicit target of a NEW membership row (legal in the path/body for this
 * cross-tenant endpoint — it is NOT a tenant-scope marker), so unlike business
 * forms it is required here.
 */
export const AddMembershipSchema = z.object({
  tenant_id: z.string().min(1, { message: 'validation_tenant_required' }),
  role_codes: z.array(roleEnum).min(1, { message: 'validation_roles_min' }),
});

export type AddMembershipInput = z.infer<typeof AddMembershipSchema>;

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
