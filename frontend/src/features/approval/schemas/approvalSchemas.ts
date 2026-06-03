import { z } from 'zod';

/**
 * Reject / Request-changes both require a free-text reason of >= 20 chars
 * (mirrors backend `ApprovalReasonValidator`).
 */
export const reasonRequiredSchema = z.object({
  reason: z
    .string({ error: 'reason_required' })
    .trim()
    .min(20, { message: 'reason_too_short' })
    .max(2000, { message: 'reason_too_long' }),
});

export type ReasonRequiredInput = z.infer<typeof reasonRequiredSchema>;
