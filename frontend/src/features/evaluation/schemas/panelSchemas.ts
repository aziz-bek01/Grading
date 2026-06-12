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

export type CreatePanelInput = z.infer<typeof CreatePanelSchema>;
export type AssignEvaluatorInput = z.infer<typeof AssignEvaluatorSchema>;
