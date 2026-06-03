/**
 * Zod schemas for the audit filter form (D-1 FE).
 *
 * Used only for client-side validation of the filter bar; backend remains
 * the source of truth for what it accepts.
 */
import { z } from 'zod';

const ISO_DATE = /^\d{4}-\d{2}-\d{2}(T\d{2}:\d{2}(:\d{2}(\.\d+)?Z?)?)?$/;

export const AuditFilterSchema = z
  .object({
    from: z.string().regex(ISO_DATE, 'audit.filter.error_date').optional().or(z.literal('')),
    to: z.string().regex(ISO_DATE, 'audit.filter.error_date').optional().or(z.literal('')),
    actorUserId: z.string().optional().or(z.literal('')),
    tenantId: z.string().optional().or(z.literal('')),
    action: z.string().optional().or(z.literal('')),
    entityType: z.string().optional().or(z.literal('')),
    entityId: z.string().optional().or(z.literal('')),
  })
  .refine(
    (v) => !v.from || !v.to || v.from <= v.to,
    { path: ['to'], message: 'audit.filter.error_from_after_to' },
  );

export type AuditFilterInput = z.infer<typeof AuditFilterSchema>;
