/**
 * EVALUATION_PANEL — Zod schemas mirroring the BE snake_case wire contract.
 *
 * These validate the FE-built request bodies BEFORE they hit the wire. The
 * server remains the source of truth (it re-validates and IGNORES any
 * mass-assignment fields). The min-3-mandatory-roles rule is enforced
 * server-side on lock-roster; {@link PanelRosterDraftSchema} is the UI mirror
 * that disables the dialog confirm — never a security control.
 */
import { z } from 'zod';
import {
  MANDATORY_EVALUATOR_ROLES,
  MAX_BULK_PANEL_POSITIONS,
  MAX_EXTERNAL_SEATS,
  PANEL_MIN_EVALUATORS,
} from '../panelTypes';

const uuid = z.string().min(1, 'validation_required');

export const EvaluatorRoleSchema = z.enum([
  'HR_DIRECTOR',
  'DEPARTMENT_DIRECTOR',
  'EXTERNAL_EXPERT',
  'ADDITIONAL',
]);

/**
 * POST /panels body. We DELIBERATELY do not declare the server-computed /
 * mass-assignment fields (raw_total_score, displayed_total_score,
 * evaluator_count, assigned_grade_number, tenant_id) so they can never be sent.
 */
export const CreatePanelSchema = z.object({
  position_id: uuid,
  methodology_version_id: uuid,
  min_evaluators: z.number().int().min(1).optional(),
});

/** POST /panels/{id}/evaluators body. */
export const AssignEvaluatorSchema = z.object({
  evaluator_user_id: uuid,
  evaluator_role: EvaluatorRoleSchema,
});

/**
 * UI mirror of the server lock-roster precondition: at least one of each
 * mandatory role AND total active assignments >= min (default 3). Returns a
 * stable error code the dialog maps to a localized helper string.
 */
export const PanelRosterDraftSchema = z
  .array(
    z.object({
      role: EvaluatorRoleSchema,
      evaluator_user_id: z.string().nullable(),
    }),
  )
  .superRefine((rows, ctx) => {
    const filled = rows.filter((r) => !!r.evaluator_user_id);
    const filledRoles = new Set(filled.map((r) => r.role));
    for (const role of MANDATORY_EVALUATOR_ROLES) {
      if (!filledRoles.has(role)) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: 'panel.validation.mandatory_roles_missing',
          path: [role],
        });
      }
    }
    if (filled.length < PANEL_MIN_EVALUATORS) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: 'panel.validation.below_floor',
      });
    }
  });

/**
 * UI mirror of the BE bulk-create roster cap for the dept-first wizard. Beyond
 * the mandatory-trio rule, the wizard caps EXTERNAL_EXPERT seats at
 * {@link MAX_EXTERNAL_SEATS} (one mandatory EXTERNAL_EXPERT + up to 3 ADDITIONAL
 * externals). ADDITIONAL rows here represent extra externals, so the trio rule +
 * this external cap together bound the roster. Returns a stable error code the
 * wizard maps to a localized helper.
 */
export const WizardRosterDraftSchema = z
  .array(
    z.object({
      role: EvaluatorRoleSchema,
      evaluator_user_id: z.string().nullable(),
    }),
  )
  .superRefine((rows, ctx) => {
    const filled = rows.filter((r) => !!r.evaluator_user_id);
    const filledRoles = new Set(filled.map((r) => r.role));
    for (const role of MANDATORY_EVALUATOR_ROLES) {
      if (!filledRoles.has(role)) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: 'panel.validation.mandatory_roles_missing',
          path: [role],
        });
      }
    }
    if (filled.length < PANEL_MIN_EVALUATORS) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: 'panel.validation.below_floor',
      });
    }
    // EXTERNAL_EXPERT (1 mandatory) + ADDITIONAL externals must not exceed the
    // UI cap. ADDITIONAL seats in the wizard are always extra externals.
    const externalSeatCount = filled.filter(
      (r) => r.role === 'EXTERNAL_EXPERT' || r.role === 'ADDITIONAL',
    ).length;
    if (externalSeatCount > MAX_EXTERNAL_SEATS) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: 'panel.validation.externals_capped',
      });
    }
  });

/** A single shared roster seat in the bulk-create body. */
export const BulkPanelRosterSeatSchema = z.object({
  evaluator_user_id: uuid,
  evaluator_role: EvaluatorRoleSchema,
});

/**
 * POST /panels/bulk-create body. `position_ids` is non-empty and capped at
 * {@link MAX_BULK_PANEL_POSITIONS} (mirrors the BE MAX_PAGE_SIZE guard — the
 * server returns 400 VALIDATION_FAILED above the cap). The roster reuses the
 * shared seat schema; the trio/min-3 rule is NOT enforced here (BE leaves panels
 * COLLECTING and enforces the floor on lock-roster). With start_evaluations the
 * BE additionally locks each fully-rostered panel and creates the evaluator
 * sheets. No tenant_id is declared so it can never be sent.
 */
export const BulkCreatePanelsRequestSchema = z.object({
  methodology_version_id: uuid,
  position_ids: z
    .array(uuid)
    .min(1, 'panel.validation.no_positions')
    .max(MAX_BULK_PANEL_POSITIONS, 'panel.validation.too_many_positions'),
  roster: z.array(BulkPanelRosterSeatSchema).optional(),
  start_evaluations: z.boolean().optional(),
});

const BulkPanelSeatFailureSchema = z.object({
  evaluator_role: EvaluatorRoleSchema,
  evaluator_user_id: z.string(),
  reason: z.string(),
});

const BulkCreatePanelFailureSchema = z.object({
  position_id: z.string(),
  error_code: z.enum([
    'ALREADY_EXISTS',
    'ACCESS_DENIED',
    'VALIDATION',
    'ROSTER_PARTIAL',
    'ROSTER_LOCK_FAILED',
    'INTERNAL_ERROR',
  ]),
  message: z.string(),
  /** Present ONLY for ROSTER_PARTIAL rows (Jackson NON_NULL drops it otherwise). */
  seat_failures: z.array(BulkPanelSeatFailureSchema).optional(),
});

/** POST /panels/bulk-create response (HTTP 201). */
export const BulkCreatePanelsResultSchema = z.object({
  created: z.number().int().nonnegative(),
  failed: z.array(BulkCreatePanelFailureSchema),
});

export type CreatePanelInput = z.infer<typeof CreatePanelSchema>;
export type AssignEvaluatorInput = z.infer<typeof AssignEvaluatorSchema>;
export type BulkCreatePanelsRequestInput = z.infer<
  typeof BulkCreatePanelsRequestSchema
>;
export type BulkCreatePanelsResultParsed = z.infer<
  typeof BulkCreatePanelsResultSchema
>;
